package com.asqi.scholia_kantin_be.service.integrasi;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.enums.MetodeBukuKas;
import com.asqi.scholia_kantin_be.enums.TipeBukuKas;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.SesiKasir;
import com.asqi.scholia_kantin_be.repository.SesiKasirRepository;
import com.asqi.scholia_kantin_be.security.SekolahGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Posting rekap sesi kasir ke Buku Kas admin-be — INTEGRATIONS.md §3.
 *
 * <p><b>Idempotency (INTEGRATIONS.md §3.4 poin 2).</b> Buku Kas admin-be
 * <b>tidak</b> idempoten ({@code catatTransaksi()} selalu {@code save}), jadi
 * kantin-be yang menjamin sekali-posting:
 * <ol>
 *   <li>flag {@code sesi_kasir.posting_buku_kas} — dicek sebelum memanggil port;</li>
 *   <li>{@code refId} <b>deterministik</b> {@code KANTIN-SESI-<id>} — agar
 *       retry/percobaan ganda mengarah ke entri yang sama;</li>
 *   <li>unique partial index {@code uq_sesi_kasir_referensi_buku_kas} di DB —
 *       jaring terakhir bila dua request balapan.</li>
 * </ol>
 *
 * <p><b>Mitigasi Q3.</b> {@code refModul} dikirim {@code null} sampai tim
 * admin-be menambah case kantin di {@code migrateBukuKas()} — entri masuk
 * {@code remainingBks} dan <b>tidak</b> dihapus saat migrasi
 * (INTEGRATIONS.md §3.4 poin 1). Nilai bisa diaktifkan tanpa ubah kode lewat
 * {@code kantin.bukukas.ref-modul} begitu Q3 terjawab.
 *
 * <p><b>Fail-open terhadap kasir:</b> kegagalan posting <b>tidak</b> membatalkan
 * penutupan sesi. Port yang melempar ditangkap &amp; diubah menjadi
 * {@link HasilPostingBukuKas#gagal}; sesi tetap tertutup dan bisa di-posting
 * ulang.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BukuKasPostingService {

    /** Prefix referensi entri Buku Kas untuk sesi kasir (deterministik). */
    public static final String PREFIX_REF_SESI = "KANTIN-SESI-";

    private final BukuKasPort bukuKasPort;
    private final SesiKasirRepository sesiRepo;
    private final SekolahGuard sekolahGuard;
    private final AuditLogger auditLogger;
    private final JamKantin jam;

    /** Modul referensi yang dikirim ke Buku Kas. Default kosong → {@code null} (mitigasi Q3). */
    @Value("${kantin.bukukas.ref-modul:}")
    private String refModul;

    /** Kategori pos Buku Kas untuk pendapatan kantin (INTEGRATIONS.md §3.3). */
    @Value("${kantin.bukukas.kategori-pendapatan:Pendapatan Kantin}")
    private String kategoriPendapatan;

    /** Referensi entri Buku Kas untuk sebuah sesi — deterministik &amp; unik. */
    public static String refIdSesi(Long sesiId) {
        return PREFIX_REF_SESI + sesiId;
    }

    /**
     * Posting total bersih sesi ke Buku Kas. Idempoten.
     *
     * <p>Propagasi {@link Propagation#REQUIRED}: ikut transaksi penutupan sesi
     * bila ada (sehingga flag {@code posting_buku_kas} + audit kompak dengan
     * baris {@code sesi_kasir}), atau membuka transaksi sendiri bila pemanggil
     * tidak punya (mis. jalur auto-tutup lintas-tenant yang menyimpan per-baris).
     * <b>Jangan</b> memakai {@code MANDATORY}: jalur auto-tutup tidak selalu
     * berada dalam satu transaksi, sehingga posting akan gagal hanya karena
     * ketiadaan transaksi.
     *
     * @param sesi    sesi yang sudah DITUTUP (punya {@code totalBersih})
     * @param aktorId pelaku (audit); {@code null} untuk auto-tutup sistem
     * @return hasil posting — {@code DILEWATI} bila belum dikonfigurasi / nominal 0
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public HasilPostingBukuKas postingSesi(SesiKasir sesi, Long aktorId) {
        if (sesi.isPostingBukuKas()) {
            log.debug("Sesi {} sudah diposting (ref={}) — dilewati (idempoten).",
                    sesi.getId(), sesi.getReferensiBukuKas());
            return HasilPostingBukuKas.sukses(sesi.getReferensiBukuKas(), "Sudah diposting sebelumnya");
        }

        long bersih = sesi.getTotalBersih() == null ? 0L : sesi.getTotalBersih();
        if (bersih <= 0) {
            // Tidak ada pendapatan bersih (mis. semua di-void / tidak ada transaksi).
            // Tidak ada yang diposting ke Buku Kas — jangan tandai terposting palsu.
            log.info("Sesi {} total bersih={} — tidak ada yang diposting ke Buku Kas.",
                    sesi.getId(), bersih);
            return HasilPostingBukuKas.dilewati("Total bersih 0 — tidak ada yang diposting");
        }

        String refId = refIdSesi(sesi.getId());
        PerintahBukuKas perintah = PerintahBukuKas.builder()
                .sekolahId(sesi.getSekolahId())
                .tanggal(sesi.getTanggal().atStartOfDay(jam.zona()).toOffsetDateTime())
                .tipe(TipeBukuKas.MASUK)
                .kategori(kategoriPendapatan)
                .jumlah(BigDecimal.valueOf(bersih))
                .metode(MetodeBukuKas.NON_TUNAI)
                .keterangan("Pendapatan kantin sesi " + sesi.getId())
                .refId(refId)
                .refModul(refModulAktif())
                .build();

        HasilPostingBukuKas hasil;
        try {
            hasil = bukuKasPort.catat(perintah);
        } catch (RuntimeException e) {
            // Kegagalan integrasi TIDAK boleh membatalkan penutupan sesi (fail-open kasir).
            log.error("Posting Buku Kas gagal (ref={}): {}", refId, e.getMessage(), e);
            return HasilPostingBukuKas.gagal("Gagal posting ke Buku Kas: " + e.getMessage());
        }

        if (hasil.sukses()) {
            sesi.setPostingBukuKas(true);
            sesi.setReferensiBukuKas(hasil.referensi() != null ? hasil.referensi() : refId);
            sesi.setUpdatedAt(jam.sekarang());
            sesiRepo.save(sesi);

            auditLogger.catat(aktorId, sesi.getSekolahId(), "POSTING_BUKU_KAS", "SesiKasir",
                    String.valueOf(sesi.getId()), null, null, refId);
            log.info("Sesi {} diposting ke Buku Kas (ref={}, jumlah={}, refModul={}).",
                    sesi.getId(), refId, bersih, perintah.getRefModul());
        } else if (hasil.dilewati()) {
            log.warn("Posting Buku Kas sesi {} DILEWATI: {}", sesi.getId(), hasil.pesan());
        } else {
            log.error("Posting Buku Kas sesi {} GAGAL: {}", sesi.getId(), hasil.pesan());
        }
        return hasil;
    }

    /**
     * Posting ulang sesi yang sudah DITUTUP namun belum terposting (retry manual
     * setelah Q3 terjawab, atau setelah gangguan admin-be).
     *
     * @throws NotFoundEntity       bila sesi bukan milik sekolah pemanggil
     * @throws InvalidOperationException bila sesi masih TERBUKA
     */
    @Transactional
    public HasilPostingBukuKas postingUlang(Long sekolahId, Long sesiId, Long aktorId) {
        SesiKasir sesi = sesiRepo.kunciUntukUpdate(sekolahId, sesiId)
                .orElseThrow(() -> new NotFoundEntity("Sesi kasir tidak ditemukan"));
        sekolahGuard.pastikanMilikSekolah(sesi.getSekolahId(), "Sesi kasir");
        if (sesi.terbuka()) {
            throw new InvalidOperationException("Sesi kasir masih terbuka — tutup dulu sebelum posting");
        }
        return postingSesi(sesi, aktorId);
    }

    /** {@code null} bila belum dikonfigurasi (mitigasi Q3), bukan string kosong. */
    private String refModulAktif() {
        return (refModul == null || refModul.isBlank()) ? null : refModul;
    }
}
