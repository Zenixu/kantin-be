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
        return terapkan(ArahMutasi.KREDIT, perintah);
    }

    /**
     * Kurangi saldo (penjualan, koreksi keluar). Melempar
     * {@link ConflictException} bila saldo kurang (409) — <b>tanpa</b> menyentuh
     * ledger.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public HasilMutasiSaldo debit(PerintahMutasiSaldo perintah) {
        return terapkan(ArahMutasi.DEBIT, perintah);
    }

    private HasilMutasiSaldo terapkan(ArahMutasi arah, PerintahMutasiSaldo perintah) {
        validasi(perintah);

        // 1) Idempotency: key sama → kembalikan hasil lama (tanpa mutasi baru).
        if (perintah.getIdempotencyKey() != null) {
            Optional<SaldoLedger> lama = ledgerRepo.findByIdempotencyKey(perintah.getIdempotencyKey());
            if (lama.isPresent()) {
                log.debug("Idempotency replay saldo key={} → saldoSetelah={}",
                        perintah.getIdempotencyKey(), lama.get().getSaldoSetelah());
                return HasilMutasiSaldo.replay(lama.get());
            }
        }

        // 2) Pastikan baris cache ada, lalu KUNCI (FOR UPDATE) — serialisasi per subjek.
        cacheRepo.pastikanBarisAda(perintah.getSubjekTipe().name(), perintah.getSubjekId(), perintah.getSekolahId());
        SaldoCache cache = cacheRepo.kunciUntukUpdate(perintah.getSubjekTipe(), perintah.getSubjekId())
                .orElseThrow(() -> new NotFoundEntity("Baris saldo tidak ditemukan"));

        // 3) Verifikasi tenant (PRD §11.4) — data sekolah lain → 404.
        if (!cache.getSekolahId().equals(perintah.getSekolahId())) {
            throw new NotFoundEntity("Saldo tidak ditemukan");
        }

        long sebelum = cache.getSaldo() == null ? 0L : cache.getSaldo();
        long setelah = (arah == ArahMutasi.KREDIT) ? sebelum + perintah.getNominal()
                : sebelum - perintah.getNominal();

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
                .nominal(perintah.getNominal())
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
            // Balapan idempotency: dua request dengan key sama nyaris bersamaan.
            // Yang kalah memakai hasil pemenang (idempotent, bukan error).
            if (perintah.getIdempotencyKey() != null) {
                SaldoLedger pemenang = ledgerRepo.findByIdempotencyKey(perintah.getIdempotencyKey())
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

    private void validasi(PerintahMutasiSaldo p) {
        if (p.getSekolahId() == null) {
            throw new IllegalArgumentException("sekolahId wajib diisi");
        }
        if (p.getSubjekTipe() == null || p.getSubjekId() == null) {
            throw new IllegalArgumentException("subjek wajib diisi");
        }
        if (p.getJenis() == null) {
            throw new IllegalArgumentException("jenis mutasi wajib diisi");
        }
        if (p.getNominal() <= 0) {
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
