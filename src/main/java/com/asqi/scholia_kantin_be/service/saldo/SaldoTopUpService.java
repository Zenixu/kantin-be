package com.asqi.scholia_kantin_be.service.saldo;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.kasir.HasilMutasiSaldo;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service saldo: top-up tunai (TU) &amp; koreksi bendahara (PRD §9.2).
 *
 * <p><b>Top-up tunai</b> adalah satu-satunya penerimaan uang tunai modul kantin,
 * dilakukan di TU (bukan kasir). Tercatat atas nama petugas; saldo bertambah
 * sebagai <b>dana titipan</b> (bukan pendapatan — PRD §5).
 *
 * <p><b>Koreksi</b> dilakukan sebagai <b>mutasi pembalik</b> dengan alasan
 * (data tidak pernah diedit/dihapus — PRD §11.1). Semua aksi diaudit (§11.7).
 *
 * <p>Top-up <b>online</b> (via payment gateway) TIDAK ada di sini — menunggu
 * kontrak callback-be (OPEN-QUESTIONS Q4) dan ditangani lewat webhook.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SaldoTopUpService {

    private final LedgerSaldoService ledgerSaldo;
    private final AuditLogger auditLogger;

    /**
     * Top-up tunai di TU/bendahara.
     *
     * @param referensiId nomor bukti/referensi unik (mis. {@code TU-2026-0001}).
     *                    Dipakai sebagai idempotency key sehingga menyimpan
     *                    ulang bukti yang sama tidak menambah saldo dua kali.
     * @param aktorId     petugas TU (audit)
     */
    @Transactional
    public HasilMutasiSaldo topUpTunai(Long sekolahId, SubjekTipe subjekTipe, Long subjekId,
                                       long nominal, String penyetor, String referensiId, Long aktorId) {
        if (nominal <= 0) {
            throw new InvalidOperationException("Nominal top-up harus > 0");
        }
        if (referensiId == null || referensiId.isBlank()) {
            throw new InvalidOperationException("Nomor referensi/bukti wajib diisi");
        }

        String keterangan = "Top-up tunai"
                + (penyetor == null || penyetor.isBlank() ? "" : " oleh " + penyetor);

        HasilMutasiSaldo hasil = ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(sekolahId)
                .subjekTipe(subjekTipe)
                .subjekId(subjekId)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI)
                .nominal(nominal)
                .idempotencyKey("TOPUP-TUNAI-" + referensiId)
                .referensiTipe("TOPUP")
                .referensiId(referensiId)
                .keterangan(keterangan)
                .aktorId(aktorId)
                .build());

        // Audit hanya untuk mutasi baru (bukan replay idempotent).
        if (!hasil.isIdempotentReplay()) {
            auditLogger.catat(aktorId, sekolahId, "TOPUP_TUNAI", "Saldo",
                    subjekTipe + ":" + subjekId, keterangan,
                    null, String.valueOf(nominal));
        }
        return hasil;
    }

    /**
     * Koreksi saldo oleh bendahara — mutasi pembalik dengan alasan (PRD §9.2).
     *
     * @param arah        {@link ArahMutasi#KREDIT} menambah, {@link ArahMutasi#DEBIT}
     *                    mengurangi (mis. membatalkan top-up salah input).
     * @param referensiId nomor berita acara/referensi koreksi yang <b>unik</b>.
     *                    <b>Wajib</b> — dipakai sebagai idempotency key agar retry
     *                    jaringan/double-submit tidak menerapkan koreksi dua kali
     *                    (Aturan Emas §3.3).
     */
    @Transactional
    public HasilMutasiSaldo koreksi(Long sekolahId, SubjekTipe subjekTipe, Long subjekId,
                                    ArahMutasi arah, long nominal, String alasan,
                                    String referensiId, Long aktorId) {
        if (nominal <= 0) {
            throw new InvalidOperationException("Nominal koreksi harus > 0");
        }
        if (alasan == null || alasan.isBlank()) {
            throw new InvalidOperationException("Alasan koreksi wajib diisi (PRD §9.2)");
        }
        if (referensiId == null || referensiId.isBlank()) {
            throw new InvalidOperationException(
                    "Nomor referensi/berita acara koreksi wajib diisi (idempotency)");
        }

        PerintahMutasiSaldo perintah = PerintahMutasiSaldo.builder()
                .sekolahId(sekolahId)
                .subjekTipe(subjekTipe)
                .subjekId(subjekId)
                .jenis(JenisMutasiSaldo.KOREKSI)
                .nominal(nominal)
                .idempotencyKey("KOREKSI-" + referensiId)
                .referensiTipe("KOREKSI")
                .referensiId(referensiId)
                .keterangan("Koreksi: " + alasan)
                .aktorId(aktorId)
                .build();

        HasilMutasiSaldo hasil = (arah == ArahMutasi.KREDIT)
                ? ledgerSaldo.kredit(perintah)
                : ledgerSaldo.debit(perintah);

        // Audit hanya untuk mutasi baru (bukan replay idempotent).
        if (!hasil.isIdempotentReplay()) {
            long saldoSebelum = (arah == ArahMutasi.KREDIT)
                    ? hasil.getSaldoSetelah() - nominal
                    : hasil.getSaldoSetelah() + nominal;
            auditLogger.catat(aktorId, sekolahId, "KOREKSI_SALDO", "Saldo",
                    subjekTipe + ":" + subjekId, alasan,
                    String.valueOf(saldoSebelum), String.valueOf(hasil.getSaldoSetelah()));
        }

        return hasil;
    }
}
