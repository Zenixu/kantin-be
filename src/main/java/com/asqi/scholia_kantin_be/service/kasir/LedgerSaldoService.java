package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.SaldoCache;
import com.asqi.scholia_kantin_be.model.SaldoLedger;
import com.asqi.scholia_kantin_be.repository.SaldoCacheRepository;
import com.asqi.scholia_kantin_be.repository.SaldoLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service inti ledger saldo — <b>satu-satunya pintu</b> perubahan saldo.
 *
 * <p><b>Aturan emas yang ditegakkan di sini (PRD §11.1, §11.2, §11.3):</b>
 * <ol>
 *   <li><b>Append-only.</b> Setiap perubahan = satu baris {@code saldo_ledger}
 *       baru. Tidak pernah UPDATE/DELETE (dijaga {@code @Immutable} + trigger DB).</li>
 *   <li><b>Atomik &amp; bebas race.</b> Saldo diambil dengan
 *       {@code SELECT ... FOR UPDATE} pada {@code saldo_cache} sehingga dua
 *       kasir tidak bisa memotong saldo yang sama bersamaan.</li>
 *   <li><b>Saldo tak boleh minus.</b> Diperiksa di sini (409) dan dijaga
 *       CHECK constraint DB sebagai jaring terakhir.</li>
 *   <li><b>Idempotency.</b> {@code idempotency_key} UNIQUE: key yang sama
 *       mengembalikan hasil lama, tidak memotong dua kali.</li>
 * </ol>
 *
 * <p>Service ini <b>tidak</b> menyentuh tenant context langsung — pemanggil
 * (mis. {@code TapService}) mengisi {@code sekolahId} pada perintah, dan
 * {@code SekolahGuard} memverifikasi kepemilikan.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerSaldoService {

    private final SaldoLedgerRepository ledgerRepo;
    private final SaldoCacheRepository cacheRepo;
    private final IdGenerator idGenerator;
    private final JamKantin jam;

    // ────────────────────────────────────────────────────────────────
    // MUTASI (WAJIB di dalam transaksi pemanggil)
    // ────────────────────────────────────────────────────────────────

    /**
     * Tambah saldo (top-up, refund, void penjualan, koreksi masuk).
     *
     * <p>{@code MANDATORY}: harus berjalan di dalam transaksi pemanggil agar
     * debit + kredit + transaksi kasir menjadi <b>satu</b> transaksi DB.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public HasilMutasiSaldo kredit(PerintahMutasiSaldo perintah) {
        return terapkan(ArahMutasi.KREDIT, perintah, false);
    }

    /**
     * Kurangi saldo (penjualan, koreksi keluar). Melempar
     * {@link ConflictException} bila saldo kurang (409) — <b>tanpa</b> menyentuh
     * ledger.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public HasilMutasiSaldo debit(PerintahMutasiSaldo perintah) {
        return terapkan(ArahMutasi.DEBIT, perintah, false);
    }

    /**
     * Kurangi saldo <b>sebanyak sisa</b> yang ada (PRD §9.3 — refund siswa
     * keluar). Nominal <b>ditentukan dari saldo berjalan</b>, bukan masukan,
     * sehingga saldo pasti menjadi 0 (tidak bisa salah ketik nominal).
     *
     * <p>Gagal ({@link ConflictException}) bila saldo sudah 0 — tidak ada yang
     * dapat direfund. Idempoten lewat {@code idempotencyKey}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public HasilMutasiSaldo debitSisaPenuh(PerintahMutasiSaldo perintah) {
        return terapkan(ArahMutasi.DEBIT, perintah, true);
    }

    /**
     * Pindahkan <b>seluruh</b> saldo sumber ke tujuan (saudara kandung, PRD
     * §9.3) dalam <b>satu</b> transaksi: satu kaki DEBIT (sumber → 0) + satu
     * kaki KREDIT (tujuan += nominal). Bila kaki kredit gagal, kaki debit ikut
     * di-rollback (atomik) — saldo tidak boleh "hilang" di tengah.
     *
     * <p>Idempoten lewat {@code referensiId} (nomor berita acara): replay
     * mengembalikan kaki debit lama tanpa mutasi baru.
     *
     * @return hasil kaki <b>keluar</b> (sumber) — {@code saldoSetelah} = 0
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public HasilMutasiSaldo pindahSaldo(Long sekolahId, SubjekTipe subjekTipe, Long sumberId,
                                        Long tujuanId, String referensiId, String keterangan,
                                        Long aktorId) {
        String keySumber = "TRANSFER-" + referensiId;

        // Idempotency: replay → kembalikan kaki keluar lama tanpa menyentuh saldo.
        Optional<SaldoLedger> lama = ledgerRepo.findBySekolahIdAndIdempotencyKey(sekolahId, keySumber);
        if (lama.isPresent()) {
            return HasilMutasiSaldo.replay(lama.get());
        }

        HasilMutasiSaldo keluar = terapkan(ArahMutasi.DEBIT, PerintahMutasiSaldo.builder()
                .sekolahId(sekolahId).subjekTipe(subjekTipe).subjekId(sumberId)
                .jenis(JenisMutasiSaldo.TRANSFER).nominal(0L)
                .idempotencyKey(keySumber).referensiTipe("TRANSFER").referensiId(referensiId)
                .keterangan(keterangan).aktorId(aktorId).build(), true);

        long nominal = keluar.getMutasi().getNominal() == null ? 0L : keluar.getMutasi().getNominal();

        // Kaki masuk — key turunan agar tidak bentrok dengan kaki keluar, tetapi
        // tetap idempoten (replay kredit tidak menambah saldo dua kali).
        terapkan(ArahMutasi.KREDIT, PerintahMutasiSaldo.builder()
                .sekolahId(sekolahId).subjekTipe(subjekTipe).subjekId(tujuanId)
                .jenis(JenisMutasiSaldo.TRANSFER).nominal(nominal)
                .idempotencyKey(keySumber + "-MASUK").referensiTipe("TRANSFER").referensiId(referensiId)
                .keterangan(keterangan).aktorId(aktorId).build(), false);

        return keluar;
    }

    private HasilMutasiSaldo terapkan(ArahMutasi arah, PerintahMutasiSaldo perintah, boolean seluruhSaldo) {
        validasi(perintah, seluruhSaldo);

        // 1) Idempotency: key sama (per sekolah) → kembalikan hasil lama (tanpa mutasi baru).
        if (perintah.getIdempotencyKey() != null) {
            Optional<SaldoLedger> lama = ledgerRepo.findBySekolahIdAndIdempotencyKey(
                    perintah.getSekolahId(), perintah.getIdempotencyKey());
            if (lama.isPresent()) {
                log.debug("Idempotency replay saldo key={} → saldoSetelah={}",
                        perintah.getIdempotencyKey(), lama.get().getSaldoSetelah());
                return HasilMutasiSaldo.replay(lama.get());
            }
        }

        // 2) Pastikan baris cache ada, lalu KUNCI (FOR UPDATE) — serialisasi per subjek.
        cacheRepo.pastikanBarisAda(perintah.getSubjekTipe().name(), perintah.getSubjekId(), perintah.getSekolahId());
        SaldoCache cache = cacheRepo.kunciUntukUpdate(perintah.getSekolahId(), perintah.getSubjekTipe(), perintah.getSubjekId())
                .orElseThrow(() -> new NotFoundEntity("Baris saldo tidak ditemukan"));

        // 3) Verifikasi tenant (PRD §11.4) — pertahanan berlapis: kunci sudah
        //    tenant-scoped (B17), tapi tetap dicek agar data sekolah lain → 404.
        if (!cache.getSekolahId().equals(perintah.getSekolahId())) {
            throw new NotFoundEntity("Saldo tidak ditemukan");
        }

        long sebelum = cache.getSaldo() == null ? 0L : cache.getSaldo();

        // Nominal: dari perintah, atau SELURUH saldo berjalan (refund/pindah §9.3).
        long nominal = seluruhSaldo ? sebelum : perintah.getNominal();
        if (seluruhSaldo && sebelum <= 0) {
            throw new ConflictException("Saldo kosong — tidak ada yang dapat dipindahkan/direfund");
        }

        long setelah = (arah == ArahMutasi.KREDIT) ? sebelum + nominal
                : sebelum - nominal;

        // 4) Anti saldo minus (PRD §11.2) — gagal lebih awal dengan pesan jelas.
        if (setelah < 0) {
            throw new ConflictException("Saldo kurang Rp" + (-setelah));
        }

        // 5) Baris ledger baru (append-only).
        OffsetDateTime now = jam.sekarang();
        SaldoLedger mutasi = SaldoLedger.builder()
                .id(idGenerator.berikutnyaLong())
                .sekolahId(perintah.getSekolahId())
                .subjekTipe(perintah.getSubjekTipe())
                .subjekId(perintah.getSubjekId())
                .arah(arah)
                .jenis(perintah.getJenis())
                .nominal(nominal)
                .saldoSetelah(setelah)
                .transaksiId(perintah.getTransaksiId())
                .idempotencyKey(perintah.getIdempotencyKey())
                .referensiTipe(perintah.getReferensiTipe())
                .referensiId(perintah.getReferensiId())
                .keterangan(perintah.getKeterangan())
                .aktorId(perintah.getAktorId())
                .waktu(now)
                .createdAt(now)
                .build();

        try {
            ledgerRepo.save(mutasi);
        } catch (DataIntegrityViolationException e) {
            // Balapan idempotency: dua request dengan key sama (per sekolah) nyaris bersamaan.
            // Yang kalah memakai hasil pemenang (idempotent, bukan error).
            if (perintah.getIdempotencyKey() != null) {
                SaldoLedger pemenang = ledgerRepo.findBySekolahIdAndIdempotencyKey(
                                perintah.getSekolahId(), perintah.getIdempotencyKey())
                        .orElseThrow(() -> e);
                return HasilMutasiSaldo.replay(pemenang);
            }
            throw e;
        }

        // 6) Perbarui cache (baris sudah terkunci).
        cache.setSaldo(setelah);
        cache.setUpdatedAt(now);
        cacheRepo.save(cache);

        return HasilMutasiSaldo.baru(mutasi, setelah);
    }

    private void validasi(PerintahMutasiSaldo p, boolean seluruhSaldo) {
        if (p.getSekolahId() == null) {
            throw new IllegalArgumentException("sekolahId wajib diisi");
        }
        if (p.getSubjekTipe() == null || p.getSubjekId() == null) {
            throw new IllegalArgumentException("subjek wajib diisi");
        }
        if (p.getJenis() == null) {
            throw new IllegalArgumentException("jenis mutasi wajib diisi");
        }
        // Nominal > 0 wajib, KECUALI mode "seluruh saldo" (nominal diambil dari
        // saldo berjalan, bukan masukan — PRD §9.3 refund/pindah).
        if (!seluruhSaldo && p.getNominal() <= 0) {
            throw new IllegalArgumentException("nominal harus > 0");
        }
    }

    // ────────────────────────────────────────────────────────────────
    // BACA (read-only)
    // ────────────────────────────────────────────────────────────────

    /** Saldo berjalan satu subjek (0 bila belum pernah ada mutasi). */
    @Transactional(readOnly = true)
    public long saldo(Long sekolahId, SubjekTipe subjekTipe, Long subjekId) {
        return cacheRepo.findBySubjekTipeAndSubjekId(subjekTipe, subjekId)
                .filter(c -> c.getSekolahId().equals(sekolahId))
                .map(c -> c.getSaldo() == null ? 0L : c.getSaldo())
                .orElse(0L);
    }

    /**
     * Hitung ulang saldo dari ledger (sumber kebenaran) — untuk audit &amp;
     * deteksi drift cache (PRD §11.1).
     */
    @Transactional(readOnly = true)
    public long hitungUlangDariLedger(Long sekolahId, SubjekTipe subjekTipe, Long subjekId) {
        Long hasil = ledgerRepo.hitungSaldoDariLedger(sekolahId, subjekTipe, subjekId, ArahMutasi.KREDIT);
        return hasil == null ? 0L : hasil;
    }

    /**
     * Total belanja bersih hari ini untuk satu subjek — dasar pemeriksaan
     * <b>limit harian</b> (PRD §6.1 tahap 5). Transaksi yang sudah di-void
     * dikurangi (net) agar tidak lagi memakan jatah limit (PRD §6.3).
     */
    @Transactional(readOnly = true)
    public long belanjaHariIni(Long sekolahId, SubjekTipe subjekTipe, Long subjekId) {
        Long hasil = ledgerRepo.hitungBelanjaBersihSejak(
                sekolahId, subjekTipe, subjekId, ArahMutasi.DEBIT,
                List.of(JenisMutasiSaldo.PENJUALAN, JenisMutasiSaldo.VOID_PENJUALAN),
                jam.awalHariIni());
        return hasil == null ? 0L : hasil;
    }

    /** Riwayat mutasi terbaru satu subjek (PRD §8.4). */
    @Transactional(readOnly = true)
    public List<SaldoLedger> riwayatTerbaru(Long sekolahId, SubjekTipe subjekTipe, Long subjekId, int batas) {
        return ledgerRepo.riwayatTerbaru(sekolahId, subjekTipe, subjekId,
                org.springframework.data.domain.PageRequest.of(0, Math.max(1, batas)));
    }
}
