package com.asqi.scholia_kantin_be.service.stok;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

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
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerStokService {

    private final MutasiStokRepository mutasiRepo;
    private final StokCacheRepository cacheRepo;
    private final HppService hppService;
    private final IdGenerator idGenerator;
    private final JamKantin jam;

    // ────────────────────────────────────────────────────────────────
    // OPERASI BISNIS
    // ────────────────────────────────────────────────────────────────

    /**
     * Barang masuk: tambah stok &amp; perbarui HPP rata-rata tertimbang (PRD §7.2).
     *
     * @return hasil dengan HPP baru
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

        StokCache cache = kunciStok(sekolahId, menuId);
        int stokSebelum = cache.getStok();
        long hppSebelum = cache.getHpp();

        long hppBaru = hppService.hitungRataRataTertimbang(stokSebelum, hppSebelum, qty, hargaBeliPerUnit);
        int stokBaru = stokSebelum + qty;

        MutasiStok mutasi = catat(ArahStok.MASUK, JenisMutasiStok.BARANG_MASUK, sekolahId, menuId,
                qty, stokBaru, hppBaru, null, referensiTipe, referensiId, null, aktorId);

        cache.setStok(stokBaru);
        cache.setHpp(hppBaru);
        cache.setUpdatedAt(jam.sekarang());
        cacheRepo.save(cache);

        log.info("Barang masuk menu={} qty={} hargaBeli={} → stok={} hpp={}",
                menuId, qty, hargaBeliPerUnit, stokBaru, hppBaru);
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
                qty, stokBaru, hppSnapshot, transaksiId, null, null, null, null);

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
                qty, stokBaru, hppSnapshot, transaksiId, null, null, null, aktorId);

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
     * @param qtyFisik stok hasil hitung fisik
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public HasilMutasiStok sesuaikanOpname(Long sekolahId, Long menuId, int qtyFisik,
                                           String alasan, String referensiId, Long aktorId) {
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
        JenisMutasiStok jenis = selisih > 0 ? JenisMutasiStok.OPNAME_MASUK : JenisMutasiStok.OPNAME_KELUAR;

        MutasiStok mutasi = catat(arah, jenis, sekolahId, menuId, Math.abs(selisih), qtyFisik,
                cache.getHpp(), null, "OPNAME", referensiId, alasan, aktorId);

        cache.setStok(qtyFisik);
        cache.setUpdatedAt(jam.sekarang());
        cacheRepo.save(cache);

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

    // ────────────────────────────────────────────────────────────────
    // INTERNAL
    // ────────────────────────────────────────────────────────────────

    /** Pastikan baris cache ada &amp; terkunci (FOR UPDATE) — serialisasi per menu. */
    private StokCache kunciStok(Long sekolahId, Long menuId) {
        cacheRepo.pastikanBarisAda(menuId, sekolahId);
        StokCache cache = cacheRepo.kunciUntukUpdate(menuId)
                .orElseThrow(() -> new NotFoundEntity("Stok menu tidak ditemukan"));
        if (!cache.getSekolahId().equals(sekolahId)) {
            throw new NotFoundEntity("Stok menu tidak ditemukan");
        }
        return cache;
    }

    private MutasiStok catat(ArahStok arah, JenisMutasiStok jenis, Long sekolahId, Long menuId,
                             int qty, int stokSetelah, Long hppSnapshot, Long transaksiId,
                             String referensiTipe, String referensiId, String alasan, Long aktorId) {
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
}
