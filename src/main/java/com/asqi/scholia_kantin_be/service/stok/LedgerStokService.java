package com.asqi.scholia_kantin_be.service.stok;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.enums.ArahStok;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.MutasiStok;
import com.asqi.scholia_kantin_be.model.StokCache;
import com.asqi.scholia_kantin_be.repository.MutasiStokRepository;
import com.asqi.scholia_kantin_be.repository.StokCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service inti ledger stok — <b>satu-satunya pintu</b> perubahan stok.
 *
 * <p><b>Aturan emas (PRD §11.1, §11.2, §7.2, §7.3, §7.4):</b>
 * <ul>
 *   <li><b>Append-only</b> pada {@code mutasi_stok}; stok = turunan (Σ MASUK − Σ KELUAR).</li>
 *   <li><b>Stok tak boleh minus</b> — diperiksa di sini (409) + CHECK DB.</li>
 *   <li><b>Locking</b> {@code SELECT ... FOR UPDATE} pada {@code stok_cache} saat
 *       penjualan, sehingga dua kasir tak menjual stok yang sama bersamaan.</li>
 *   <li><b>HPP rata-rata tertimbang</b> dihitung ulang saat barang masuk; nilai
 *       saat transaksi disimpan sebagai snapshot (lihat {@link HppService}).</li>
 *   <li><b>Koreksi = mutasi pembalik baru</b>, bukan edit/hapus (PRD §7.2).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerStokService {

    private final MutasiStokRepository mutasiRepo;
    private final StokCacheRepository cacheRepo;
    private final HppService hppService;
    private final AuditLogger auditLogger;
    private final IdGenerator idGenerator;
    private final JamKantin jam;

    // ────────────────────────────────────────────────────────────────
    // OPERASI BISNIS
    // ────────────────────────────────────────────────────────────────

    /**
     * Barang masuk: tambah stok &amp; perbarui HPP rata-rata tertimbang (PRD §7.2).
     *
     * <p><b>Idempotent</b> lewat {@code referensiId} (nomor bukti penerimaan):
     * retry dengan bukti yang sama mengembalikan hasil lama tanpa menggandakan
     * stok. Ditegakkan dua lapis — jalur cepat (query) + UNIQUE parsial
     * {@code uq_mutasi_stok_barang_masuk_referensi} yang menangkap balapan.
     *
     * @return hasil dengan HPP baru (atau hasil lama bila ini replay)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public HasilMutasiStok masukBarang(Long sekolahId, Long menuId, int qty,
                                       long hargaBeliPerUnit, String referensiTipe,
                                       String referensiId, Long aktorId) {
        if (qty <= 0) {
            throw new InvalidOperationException("Qty barang masuk harus > 0");
        }
        if (hargaBeliPerUnit < 0) {
            throw new InvalidOperationException("Harga beli tidak boleh negatif");
        }

        // Idempotency jalur cepat: bukti + menu yang sama sudah pernah diproses.
        if (referensiId != null && !referensiId.isBlank()) {
            Optional<MutasiStok> lama = mutasiRepo
                    .cariByReferensiDanMenu(sekolahId, JenisMutasiStok.BARANG_MASUK,
                            referensiId, menuId, PageRequest.of(0, 1))
                    .stream().findFirst();
            if (lama.isPresent()) {
                MutasiStok m = lama.get();
                log.debug("Idempotency replay barang masuk referensi={} menu={} → mutasiId={}",
                        referensiId, menuId, m.getId());
                return HasilMutasiStok.baru(m, m.getStokSetelah(), nolBilaNull(m.getHppSnapshot()));
            }
        }

        StokCache cache = kunciStok(sekolahId, menuId);
        int stokSebelum = cache.getStok();
        long hppSebelum = cache.getHpp();

        long hppBaru = hppService.hitungRataRataTertimbang(stokSebelum, hppSebelum, qty, hargaBeliPerUnit);
        int stokBaru = stokSebelum + qty;

        MutasiStok mutasi = catat(ArahStok.MASUK, JenisMutasiStok.BARANG_MASUK, sekolahId, menuId,
                qty, stokBaru, hppBaru, null, referensiTipe, referensiId, null, aktorId,
                hargaBeliPerUnit, null);

        cache.setStok(stokBaru);
        cache.setHpp(hppBaru);
        cache.setUpdatedAt(jam.sekarang());
        cacheRepo.save(cache);

        // Audit (PRD §11.7): barang masuk wajib tercatat (nilai_lama → nilai_baru).
        auditLogger.catat(aktorId, sekolahId, "BARANG_MASUK", "Stok",
                "MENU:" + menuId, null,
                "stok=" + stokSebelum + ";hpp=" + hppSebelum,
                "stok=" + stokBaru + ";hpp=" + hppBaru);

        log.info("Barang masuk menu={} qty={} hargaBeli={} → stok={} hpp={}",
                menuId, qty, hargaBeliPerUnit, stokBaru, hppBaru);
        return HasilMutasiStok.baru(mutasi, stokBaru, hppBaru);
    }

    /**
     * Koreksi barang masuk salah input dengan <b>barang masuk pembalik</b>
     * (PRD §7.2): catat mutasi KELUAR baru yang menunjuk baris asal, kurangi
     * stok, dan hitung ulang HPP memakai harga beli asal. Baris asal
     * <b>tidak</b> diubah/dihapus (ledger append-only, PRD §11.1).
     *
     * <p>Idempoten lewat {@code referensiId} (nomor bukti pembalik): retry dengan
     * bukti sama mengembalikan hasil lama tanpa membalik dua kali.
     *
     * @param qtyBalik jumlah yang dibalik; {@code null} = balik seluruh sisa
     *                 yang belum dibalik (pembatalan penuh)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public HasilMutasiStok pembalikBarangMasuk(Long sekolahId, Long asalMutasiId, Integer qtyBalik,
                                               String alasan, String referensiId, Long aktorId) {
        if (alasan == null || alasan.isBlank()) {
            throw new InvalidOperationException("Alasan pembalik barang masuk wajib diisi (PRD §7.2)");
        }
        if (referensiId == null || referensiId.isBlank()) {
            throw new InvalidOperationException("Nomor bukti pembalik wajib diisi (idempotency)");
        }

        // Idempotency: bukti pembalik yang sama → kembalikan hasil lama.
        Optional<MutasiStok> replay = mutasiRepo
                .cariByReferensi(sekolahId, JenisMutasiStok.BARANG_MASUK_PEMBALIK, referensiId,
                        PageRequest.of(0, 1))
                .stream().findFirst();
        if (replay.isPresent()) {
            MutasiStok lama = replay.get();
            log.debug("Idempotency replay pembalik referensi={} → mutasiId={}", referensiId, lama.getId());
            return HasilMutasiStok.baru(lama, lama.getStokSetelah(), nolBilaNull(lama.getHppSnapshot()));
        }

        MutasiStok asal = mutasiRepo.findByIdAndSekolahId(asalMutasiId, sekolahId)
                .orElseThrow(() -> new NotFoundEntity("Barang masuk tidak ditemukan"));
        if (asal.getJenis() != JenisMutasiStok.BARANG_MASUK) {
            throw new InvalidOperationException(
                    "Hanya barang masuk (BARANG_MASUK) yang dapat dibalik — baris ini: " + asal.getJenis());
        }

        long sudahDibalik = mutasiRepo.totalDibalik(sekolahId, asal.getId());
        long sisa = asal.getQty() - sudahDibalik;
        if (sisa <= 0) {
            throw new ConflictException("Barang masuk ini sudah dibalik seluruhnya");
        }

        int qty = (qtyBalik == null) ? (int) sisa : qtyBalik;
        if (qty <= 0) {
            throw new InvalidOperationException("Qty pembalik harus > 0");
        }
        if (qty > sisa) {
            throw new InvalidOperationException(
                    "Qty pembalik (" + qty + ") melebihi sisa yang dapat dibalik (" + sisa + ")");
        }

        // Harga beli asal untuk menghitung ulang HPP (fallback ke HPP snapshot
        // untuk baris lama yang belum menyimpan harga beli satuan).
        long hargaBeliAsal = asal.getHargaBeliSatuan() != null
                ? asal.getHargaBeliSatuan()
                : nolBilaNull(asal.getHppSnapshot());

        StokCache cache = kunciStok(sekolahId, asal.getMenuId());
        int stokSebelum = cache.getStok();
        long hppSebelum = cache.getHpp();

        if (stokSebelum < qty) {
            throw new ConflictException(
                    "Stok saat ini (" + stokSebelum + ") lebih kecil dari qty yang dibalik (" + qty
                            + ") — sebagian barang mungkin sudah terjual. Pakai stok opname.");
        }

        long hppBaru = hppService.hitungRataRataSetelahPembalik(stokSebelum, hppSebelum, qty, hargaBeliAsal);
        int stokBaru = stokSebelum - qty;

        MutasiStok mutasi = catat(ArahStok.KELUAR, JenisMutasiStok.BARANG_MASUK_PEMBALIK,
                sekolahId, asal.getMenuId(), qty, stokBaru, hppBaru, null,
                "BARANG_MASUK_PEMBALIK", referensiId, alasan, aktorId,
                hargaBeliAsal, asal.getId());

        cache.setStok(stokBaru);
        cache.setHpp(hppBaru);
        cache.setUpdatedAt(jam.sekarang());
        cacheRepo.save(cache);

        // Audit (PRD §11.7): barang masuk pembalik wajib tercatat + alasan.
        auditLogger.catat(aktorId, sekolahId, "BARANG_MASUK_PEMBALIK", "Stok",
                "MENU:" + asal.getMenuId(), alasan,
                "stok=" + stokSebelum + ";hpp=" + hppSebelum,
                "stok=" + stokBaru + ";hpp=" + hppBaru + ";asalMutasiId=" + asal.getId());

        log.info("Barang masuk pembalik asal={} qty={} menu={} → stok={} hpp={}",
                asal.getId(), qty, asal.getMenuId(), stokBaru, hppBaru);
        return HasilMutasiStok.baru(mutasi, stokBaru, hppBaru);
    }

    /**
     * Penjualan: kurangi stok memakai HPP berjalan sebagai snapshot (PRD §6.2, §7.4).
     *
     * @return HPP per unit yang dipakai (untuk disimpan di {@code transaksi_item})
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public HasilMutasiStok keluarPenjualan(Long sekolahId, Long menuId, int qty, Long transaksiId) {
        if (qty <= 0) {
            throw new InvalidOperationException("Qty penjualan harus > 0");
        }

        StokCache cache = kunciStok(sekolahId, menuId);
        int stokSebelum = cache.getStok();
        if (stokSebelum < qty) {
            throw new ConflictException("Stok tidak cukup (sisa " + stokSebelum + ")");
        }
        long hppSnapshot = cache.getHpp();
        int stokBaru = stokSebelum - qty;

        MutasiStok mutasi = catat(ArahStok.KELUAR, JenisMutasiStok.PENJUALAN, sekolahId, menuId,
                qty, stokBaru, hppSnapshot, transaksiId, null, null, null, null, null, null);

        cache.setStok(stokBaru);
        cache.setUpdatedAt(jam.sekarang());
        cacheRepo.save(cache);

        return HasilMutasiStok.baru(mutasi, stokBaru, hppSnapshot);
    }

    /**
     * Void penjualan: kembalikan stok memakai HPP snapshot transaksi sehingga
     * HPP rata-rata tidak berubah (PRD §6.3, §7.4).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public HasilMutasiStok kembalikanVoid(Long sekolahId, Long menuId, int qty,
                                          long hppSnapshot, Long transaksiId, Long aktorId) {
        if (qty <= 0) {
            throw new InvalidOperationException("Qty void harus > 0");
        }

        StokCache cache = kunciStok(sekolahId, menuId);
        int stokBaru = cache.getStok() + qty;

        MutasiStok mutasi = catat(ArahStok.MASUK, JenisMutasiStok.VOID_PENJUALAN, sekolahId, menuId,
                qty, stokBaru, hppSnapshot, transaksiId, null, null, null, aktorId, null, null);

        cache.setStok(stokBaru);
        cache.setUpdatedAt(jam.sekarang());
        cacheRepo.save(cache);

        return HasilMutasiStok.baru(mutasi, stokBaru, cache.getHpp());
    }

    /**
     * Penyesuaian stok opname (PRD §7.3): set stok ke hasil hitung fisik,
     * selisih dicatat sebagai mutasi MASUK/KELUAR dengan <b>alasan wajib</b>.
     * HPP rata-rata <b>tidak</b> berubah.
     *
     * <p>Varian tunggal — selisih kurang selalu {@code OPNAME_KELUAR}. Untuk
     * menandai kerugian barang rusak/basi, pakai
     * {@link #sesuaikanOpname(Long, Long, int, String, String, boolean, String, Long)}.
     *
     * @param qtyFisik stok hasil hitung fisik
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public HasilMutasiStok sesuaikanOpname(Long sekolahId, Long menuId, int qtyFisik,
                                           String alasan, String referensiId, Long aktorId) {
        return sesuaikanOpname(sekolahId, menuId, qtyFisik, alasan, referensiId, false,
                "OPNAME", aktorId);
    }

    /**
     * Penyesuaian stok opname dengan kendali jenis mutasi &amp; tipe referensi
     * (PRD §7.3) — dipakai baik opname tunggal maupun <b>batch</b>.
     *
     * @param qtyFisik      stok hasil hitung fisik
     * @param rusak         bila {@code true} dan stok berkurang → jenis
     *                      {@code BARANG_RUSAK} (rusak/basi), bukan
     *                      {@code OPNAME_KELUAR} (selisih audit)
     * @param referensiTipe tipe referensi: {@code "OPNAME"} (tunggal) atau
     *                      {@code "OPNAME_BATCH"} (idempotency batch)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public HasilMutasiStok sesuaikanOpname(Long sekolahId, Long menuId, int qtyFisik,
                                           String alasan, String referensiId, boolean rusak,
                                           String referensiTipe, Long aktorId) {
        if (qtyFisik < 0) {
            throw new InvalidOperationException("Stok fisik tidak boleh negatif");
        }
        if (alasan == null || alasan.isBlank()) {
            throw new InvalidOperationException("Alasan penyesuaian stok wajib diisi (PRD §7.3)");
        }

        StokCache cache = kunciStok(sekolahId, menuId);
        int stokSekarang = cache.getStok();
        int selisih = qtyFisik - stokSekarang;

        if (selisih == 0) {
            // Tidak ada perubahan — tidak ada mutasi (ledger tetap bersih).
            return HasilMutasiStok.baru(null, stokSekarang, cache.getHpp());
        }

        ArahStok arah = selisih > 0 ? ArahStok.MASUK : ArahStok.KELUAR;
        JenisMutasiStok jenis;
        if (selisih > 0) {
            jenis = JenisMutasiStok.OPNAME_MASUK;
        } else {
            // Selisih kurang: rusak/basi → BARANG_RUSAK; selebihnya → OPNAME_KELUAR.
            jenis = rusak ? JenisMutasiStok.BARANG_RUSAK : JenisMutasiStok.OPNAME_KELUAR;
        }

        MutasiStok mutasi = catat(arah, jenis, sekolahId, menuId, Math.abs(selisih), qtyFisik,
                cache.getHpp(), null, referensiTipe, referensiId, alasan, aktorId, null, null);

        cache.setStok(qtyFisik);
        cache.setUpdatedAt(jam.sekarang());
        cacheRepo.save(cache);

        // Audit (PRD §11.7): penyesuaian stok wajib tercatat + alasan.
        String aksiAudit = jenis == JenisMutasiStok.BARANG_RUSAK ? "BARANG_RUSAK" : "OPNAME_STOK";
        auditLogger.catat(aktorId, sekolahId, aksiAudit, "Stok",
                "MENU:" + menuId, alasan,
                "stok=" + stokSekarang, "stok=" + qtyFisik);

        return HasilMutasiStok.baru(mutasi, qtyFisik, cache.getHpp());
    }

    // ────────────────────────────────────────────────────────────────
    // BACA
    // ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public int stok(Long sekolahId, Long menuId) {
        return cacheRepo.findByMenuId(menuId)
                .filter(c -> c.getSekolahId().equals(sekolahId))
                .map(StokCache::getStok)
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public long hpp(Long sekolahId, Long menuId) {
        return cacheRepo.findByMenuId(menuId)
                .filter(c -> c.getSekolahId().equals(sekolahId))
                .map(StokCache::getHpp)
                .orElse(0L);
    }

    @Transactional(readOnly = true)
    public List<StokCache> stokMenipis(Long sekolahId) {
        return cacheRepo.stokMenipis(sekolahId);
    }

    /** Hitung ulang stok dari ledger (sumber kebenaran) — audit/deteksi drift. */
    @Transactional(readOnly = true)
    public long hitungUlangDariLedger(Long sekolahId, Long menuId) {
        Long hasil = mutasiRepo.hitungStokDariLedger(sekolahId, menuId, ArahStok.MASUK);
        return hasil == null ? 0L : hasil;
    }

    /** Barang masuk (jenis {@code BARANG_MASUK}) berdasarkan id — tenant-scoped. */
    @Transactional(readOnly = true)
    public MutasiStok barangMasuk(Long sekolahId, Long mutasiId) {
        return mutasiRepo.findByIdAndSekolahId(mutasiId, sekolahId)
                .orElseThrow(() -> new NotFoundEntity("Barang masuk tidak ditemukan"));
    }

    /** Total qty yang sudah dibalik untuk sebuah barang masuk (0 bila belum). */
    @Transactional(readOnly = true)
    public long totalDibalik(Long sekolahId, Long asalMutasiId) {
        return mutasiRepo.totalDibalik(sekolahId, asalMutasiId);
    }

    // ────────────────────────────────────────────────────────────────
    // INTERNAL
    // ────────────────────────────────────────────────────────────────

    /** Pastikan baris cache ada &amp; terkunci (FOR UPDATE) — serialisasi per menu. */
    private StokCache kunciStok(Long sekolahId, Long menuId) {
        cacheRepo.pastikanBarisAda(menuId, sekolahId);
        StokCache cache = cacheRepo.kunciUntukUpdate(sekolahId, menuId)
                .orElseThrow(() -> new NotFoundEntity("Stok menu tidak ditemukan"));
        // Pertahanan berlapis: kunci sudah tenant-scoped (B17); cek tetap dijaga.
        if (!cache.getSekolahId().equals(sekolahId)) {
            throw new NotFoundEntity("Stok menu tidak ditemukan");
        }
        return cache;
    }

    private MutasiStok catat(ArahStok arah, JenisMutasiStok jenis, Long sekolahId, Long menuId,
                             int qty, int stokSetelah, Long hppSnapshot, Long transaksiId,
                             String referensiTipe, String referensiId, String alasan, Long aktorId,
                             Long hargaBeliSatuan, Long mutasiAsalId) {
        OffsetDateTime now = jam.sekarang();
        MutasiStok mutasi = MutasiStok.builder()
                .id(idGenerator.berikutnyaLong())
                .sekolahId(sekolahId)
                .menuId(menuId)
                .arah(arah)
                .jenis(jenis)
                .qty(qty)
                .stokSetelah(stokSetelah)
                .hppSnapshot(hppSnapshot)
                .hargaBeliSatuan(hargaBeliSatuan)
                .mutasiAsalId(mutasiAsalId)
                .transaksiId(transaksiId)
                .referensiTipe(referensiTipe)
                .referensiId(referensiId)
                .alasan(alasan)
                .aktorId(aktorId)
                .waktu(now)
                .createdAt(now)
                .build();
        return mutasiRepo.save(mutasi);
    }

    private static long nolBilaNull(Long nilai) {
        return nilai == null ? 0L : nilai;
    }
}
