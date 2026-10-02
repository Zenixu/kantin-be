package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.SesiKasir;
import com.asqi.scholia_kantin_be.model.Transaksi;
import com.asqi.scholia_kantin_be.model.TransaksiItem;
import com.asqi.scholia_kantin_be.repository.SesiKasirRepository;
import com.asqi.scholia_kantin_be.repository.TransaksiRepository;
import com.asqi.scholia_kantin_be.security.SekolahGuard;
import com.asqi.scholia_kantin_be.service.stok.LedgerStokService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Void transaksi kasir (PRD §6.3).
 *
 * <p>Aturan:
 * <ul>
 *   <li>Hanya untuk transaksi pada <b>sesi yang belum ditutup</b> (hari yang sama).</li>
 *   <li><b>Wajib alasan</b> (audit).</li>
 *   <li>Saldo dikembalikan penuh, <b>stok dikembalikan</b> memakai HPP snapshot
 *       transaksi, dan belanja hari ini (untuk limit) ikut berkurang.</li>
 *   <li>Kompensasi = <b>mutasi pembalik baru</b>; baris ledger asli tidak
 *       diubah/dihapus (PRD §11.1).</li>
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

    /**
     * Batalkan (void) sebuah transaksi.
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

        // Hanya boleh void pada sesi yang masih terbuka (hari yang sama).
        SesiKasir sesi = sesiRepo.findById(trx.getSesiKasirId())
                .orElseThrow(() -> new NotFoundEntity("Sesi kasir tidak ditemukan"));
        if (!sesi.terbuka()) {
            throw new InvalidOperationException(
                    "Sesi kasir sudah ditutup — koreksi hanya oleh bendahara (PRD §6.3)");
        }

        OffsetDateTime now = jam.sekarang();

        // 1) Kembalikan stok memakai HPP snapshot transaksi (PRD §7.4).
        List<TransaksiItem> items = trx.getItems();
        for (TransaksiItem item : items) {
            ledgerStok.kembalikanVoid(sekolahId, item.getMenuId(), item.getQty(),
                    item.getHppSnapshot(), trx.getId(), aktorId);
        }

        // 2) Kembalikan saldo penuh (kredit pembalik, key unik agar tak dobel).
        ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(sekolahId)
                .subjekTipe(trx.getSubjekTipe())
                .subjekId(trx.getSubjekId())
                .jenis(JenisMutasiSaldo.VOID_PENJUALAN)
                .nominal(trx.getTotal())
                .idempotencyKey("VOID-" + trx.getId())
                .transaksiId(trx.getId())
                .referensiTipe("TRANSAKSI")
                .referensiId(String.valueOf(trx.getId()))
                .keterangan("Void: " + alasan)
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
        auditLogger.catat(aktorId, sekolahId, "VOID_TRANSAKSI", "Transaksi",
                String.valueOf(trx.getId()), alasan,
                StatusTransaksi.SUKSES.name(), StatusTransaksi.VOID.name());

        log.info("Void transaksi id={} oleh={} alasan={}", trx.getId(), aktorId, alasan);
        return trx;
    }
}
