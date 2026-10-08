package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.JenisNotifikasi;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.SesiKasir;
import com.asqi.scholia_kantin_be.model.Transaksi;
import com.asqi.scholia_kantin_be.model.TransaksiItem;
import com.asqi.scholia_kantin_be.repository.SesiKasirRepository;
import com.asqi.scholia_kantin_be.repository.TransaksiRepository;
import com.asqi.scholia_kantin_be.security.SekolahGuard;
import com.asqi.scholia_kantin_be.service.integrasi.NotifikasiService;
import com.asqi.scholia_kantin_be.service.integrasi.PerintahNotifikasi;
import com.asqi.scholia_kantin_be.service.stok.LedgerStokService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Void &amp; koreksi transaksi kasir (PRD §6.3, §9.2).
 *
 * <p><b>Void</b> ({@link #voidTransaksi}) — dipakai petugas kasir lewat tombol
 * "Batalkan" pada sesi yang <b>belum ditutup</b> (hari yang sama).
 *
 * <p><b>Koreksi sesi tertutup</b> ({@link #koreksiTransaksiSesiTertutup}) —
 * khusus <b>bendahara</b> (PRD §6.3): transaksi pada sesi yang sudah ditutup
 * tidak boleh di-void petugas, tetapi tetap dapat dikoreksi bendahara sebagai
 * <b>mutasi pembalik</b> beralasan (PRD §9.2). Data asli tidak pernah diedit
 * atau dihapus.
 *
 * <p>Aturan bersama:
 * <ul>
 *   <li>Saldo dikembalikan penuh, <b>stok dikembalikan</b> memakai HPP snapshot
 *       transaksi, dan belanja hari ini (untuk limit) ikut berkurang.</li>
 *   <li>Kompensasi = <b>mutasi pembalik baru</b>; baris ledger asli tidak
 *       diubah/dihapus (PRD §11.1).</li>
 *   <li><b>Wajib alasan</b> (audit §11.7).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoidService {

    private final TransaksiRepository transaksiRepo;
    private final SesiKasirRepository sesiRepo;
    private final LedgerSaldoService ledgerSaldo;
    private final LedgerStokService ledgerStok;
    private final SekolahGuard sekolahGuard;
    private final AuditLogger auditLogger;
    private final JamKantin jam;
    private final NotifikasiService notifikasi;

    /**
     * Batalkan (void) sebuah transaksi pada sesi yang masih terbuka.
     *
     * @param sekolahId tenant pemanggil
     * @param transaksiId transaksi yang dibatalkan
     * @param aktorId petugas yang membatalkan (audit)
     * @param alasan alasan void (wajib)
     */
    @Transactional
    public Transaksi voidTransaksi(Long sekolahId, Long transaksiId, Long aktorId, String alasan) {
        if (alasan == null || alasan.isBlank()) {
            throw new InvalidOperationException("Alasan void wajib diisi (PRD §6.3)");
        }

        Transaksi trx = muatTransaksi(sekolahId, transaksiId);

        // Hanya boleh void pada sesi yang masih terbuka (hari yang sama).
        SesiKasir sesi = sesiRepo.findById(trx.getSesiKasirId())
                .orElseThrow(() -> new NotFoundEntity("Sesi kasir tidak ditemukan"));
        if (!sesi.terbuka()) {
            throw new InvalidOperationException(
                    "Sesi kasir sudah ditutup — koreksi hanya oleh bendahara (PRD §6.3)");
        }

        return balikkan(sekolahId, trx, aktorId, alasan, false);
    }

    /**
     * Koreksi transaksi pada <b>sesi yang sudah ditutup</b> oleh bendahara
     * (PRD §6.3, §9.2). Dijalankan sebagai <b>mutasi pembalik</b> beralasan —
     * saldo &amp; stok dikembalikan, transaksi ditandai {@code VOID}, jejak
     * lengkap di audit log. Baris asli tidak pernah diubah/dihapus.
     *
     * <p><b>Idempoten:</b> transaksi yang sudah di-void/dikoreksi ditolak (409),
     * dan kunci mutasi pembalik memakai id transaksi sehingga retry jaringan
     * tidak menggandakan pengembalian saldo.
     *
     * @param sekolahId   tenant pemanggil (harus sekolah transaksi)
     * @param transaksiId transaksi pada sesi tertutup yang dikoreksi
     * @param aktorId     bendahara yang mengoreksi (audit)
     * @param alasan      alasan koreksi (wajib)
     */
    @Transactional
    public Transaksi koreksiTransaksiSesiTertutup(Long sekolahId, Long transaksiId,
                                                  Long aktorId, String alasan) {
        if (alasan == null || alasan.isBlank()) {
            throw new InvalidOperationException("Alasan koreksi wajib diisi (PRD §9.2)");
        }

        Transaksi trx = muatTransaksi(sekolahId, transaksiId);

        // Koreksi bendahara ini KHUSUS untuk sesi yang sudah ditutup; bila sesi
        // masih terbuka, gunakan void biasa (agar rekap sesi tetap konsisten).
        SesiKasir sesi = sesiRepo.findById(trx.getSesiKasirId())
                .orElseThrow(() -> new NotFoundEntity("Sesi kasir tidak ditemukan"));
        if (sesi.terbuka()) {
            throw new InvalidOperationException(
                    "Sesi kasir masih terbuka — gunakan void biasa (PRD §6.3)");
        }

        return balikkan(sekolahId, trx, aktorId, alasan, true);
    }

    /** Muat transaksi + guard tenant + pastikan belum di-void (PRD §11.4). */
    private Transaksi muatTransaksi(Long sekolahId, Long transaksiId) {
        Transaksi trx = transaksiRepo.findById(transaksiId)
                .orElseThrow(() -> new NotFoundEntity("Transaksi tidak ditemukan"));
        // Tenant scoping: data sekolah lain → 404 (PRD §11.4).
        sekolahGuard.pastikanMilikSekolah(trx.getSekolahId(), "Transaksi");
        if (!trx.getSekolahId().equals(sekolahId)) {
            throw new NotFoundEntity("Transaksi tidak ditemukan");
        }
        if (trx.getStatus() == StatusTransaksi.VOID) {
            throw new InvalidOperationException("Transaksi sudah di-void");
        }
        return trx;
    }

    /**
     * Inti pembalik (dipakai void &amp; koreksi): kembalikan stok + saldo lewat
     * mutasi pembalik baru, tandai transaksi VOID, audit, lalu notifikasi.
     *
     * @param koreksi {@code true} = koreksi bendahara sesi tertutup (jenis mutasi
     *                saldo {@code KOREKSI}, aksi audit berbeda); {@code false} =
     *                void kasir biasa ({@code VOID_PENJUALAN}).
     */
    private Transaksi balikkan(Long sekolahId, Transaksi trx, Long aktorId, String alasan,
                               boolean koreksi) {
        OffsetDateTime now = jam.sekarang();

        // 1) Kembalikan stok memakai HPP snapshot transaksi (PRD §7.4).
        List<TransaksiItem> items = trx.getItems();
        for (TransaksiItem item : items) {
            ledgerStok.kembalikanVoid(sekolahId, item.getMenuId(), item.getQty(),
                    item.getHppSnapshot(), trx.getId(), aktorId);
        }

        // 2) Kembalikan saldo penuh (kredit pembalik, key unik agar tak dobel).
        //    Void kasir → VOID_PENJUALAN; koreksi bendahara → KOREKSI (PRD §9.2).
        String prefix = koreksi ? "KOREKSI-TRX-" : "VOID-";
        JenisMutasiSaldo jenis = koreksi ? JenisMutasiSaldo.KOREKSI : JenisMutasiSaldo.VOID_PENJUALAN;
        ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(sekolahId)
                .subjekTipe(trx.getSubjekTipe())
                .subjekId(trx.getSubjekId())
                .jenis(jenis)
                .nominal(trx.getTotal())
                .idempotencyKey(prefix + trx.getId())
                .transaksiId(trx.getId())
                .referensiTipe("TRANSAKSI")
                .referensiId(String.valueOf(trx.getId()))
                .keterangan((koreksi ? "Koreksi: " : "Void: ") + alasan)
                .aktorId(aktorId)
                .build());

        // 3) Tandai transaksi VOID (transaksi BUKAN ledger — update status diizinkan).
        trx.setStatus(StatusTransaksi.VOID);
        trx.setAlasanVoid(alasan);
        trx.setVoidAt(now);
        trx.setVoidOleh(aktorId);
        trx.setUpdatedAt(now);
        transaksiRepo.save(trx);

        // 4) Audit (PRD §11.7).
        auditLogger.catat(aktorId, sekolahId,
                koreksi ? "KOREKSI_TRANSAKSI_SESI_TERTUTUP" : "VOID_TRANSAKSI",
                "Transaksi", String.valueOf(trx.getId()), alasan,
                StatusTransaksi.SUKSES.name(), StatusTransaksi.VOID.name());

        log.info("{} transaksi id={} oleh={} alasan={}",
                koreksi ? "Koreksi sesi tertutup" : "Void", trx.getId(), aktorId, alasan);

        // Notifikasi ke ortu (PRD §8.4) — best-effort/fail-open: kegagalan
        // pengiriman TIDAK membatalkan pembalik yang sudah tercatat di ledger.
        Long saldoSetelah = (trx.getSubjekTipe() == null || trx.getSubjekId() == null)
                ? null
                : ledgerSaldo.saldo(sekolahId, trx.getSubjekTipe(), trx.getSubjekId());
        notifikasi.kirim(PerintahNotifikasi.builder()
                .sekolahId(sekolahId)
                .jenis(JenisNotifikasi.VOID)
                .subjekTipe(trx.getSubjekTipe())
                .subjekId(trx.getSubjekId())
                .nominal(trx.getTotal())
                .saldoSetelah(saldoSetelah)
                .referensiId(String.valueOf(trx.getId()))
                .ringkasan((koreksi ? "Transaksi dikoreksi bendahara: " : "Transaksi dibatalkan (void): ")
                        + alasan)
                .aktorId(aktorId)
                .waktu(now)
                .build());

        return trx;
    }
}
