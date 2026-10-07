package com.asqi.scholia_kantin_be.service.saldo;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.JenisNotifikasi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.kasir.HasilMutasiSaldo;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import com.asqi.scholia_kantin_be.service.integrasi.BukuKasPostingService;
import com.asqi.scholia_kantin_be.service.integrasi.NotifikasiService;
import com.asqi.scholia_kantin_be.service.integrasi.PerintahNotifikasi;
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
 * <p>Top-up <b>online</b> (via payment gateway) memakai jalur
 * {@link #topUpOnline}: saldo bertambah <b>hanya setelah</b> callback PG sukses
 * (PRD §8.2, INTEGRATIONS §5), dipanggil oleh handler webhook
 * {@code TopUpOnlineWebhookHandler}. Kontrak payload callback-be belum final
 * (OPEN-QUESTIONS Q4); pemisahan itu dijaga di lapisan webhook, sedangkan
 * pencatatan saldo di sini sudah final &amp; idempoten berbasis {@code refId} PG
 * (tenant-scoped, PRD §11.3/§11.4).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SaldoTopUpService {

    private final LedgerSaldoService ledgerSaldo;
    private final AuditLogger auditLogger;
    private final BukuKasPostingService bukuKasPosting;
    private final NotifikasiService notifikasi;

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

            // Notifikasi ke ortu (PRD §8.4) — best-effort/fail-open.
            notifikasi.kirim(PerintahNotifikasi.builder()
                    .sekolahId(sekolahId)
                    .jenis(JenisNotifikasi.TOPUP_TUNAI)
                    .subjekTipe(subjekTipe)
                    .subjekId(subjekId)
                    .nominal(nominal)
                    .saldoSetelah(hasil.getSaldoSetelah())
                    .referensiId(referensiId)
                    .ringkasan("Top-up tunai Rp" + nominal + " berhasil")
                    .aktorId(aktorId)
                    .build());
        }
        return hasil;
    }

    /**
     * Top-up <b>online</b> via payment gateway — dipanggil handler webhook
     * callback-be setelah callback PG <b>sukses</b> (PRD §8.2, INTEGRATIONS §5).
     *
     * <p>Saldo bertambah <b>hanya</b> di sini (bukan saat ortu membuka PG), dan
     * idempoten berbasis <b>refId PG</b>: callback duplikat ≠ saldo dua kali
     * (PRD §11.3). Idempotency key bersifat <b>tenant-scoped</b> — refId PG yang
     * sama di sekolah berbeda adalah mutasi berbeda (PRD §11.4, V10).
     *
     * <p>Bukan uang fisik: {@link JenisMutasiSaldo#TOPUP_ONLINE} dicatat sebagai
     * dana titipan (bukan pendapatan — PRD §5). Aksi diaudit (§11.7).
     *
     * @param refIdPg referensi transaksi PG (kunci idempotency; wajib)
     * @param kanal   kanal pembayaran (mis. {@code QRIS}, {@code VA}) untuk keterangan
     * @param aktorId pelaku (biasanya {@code null} — aksi sistem webhook)
     */
    @Transactional
    public HasilMutasiSaldo topUpOnline(Long sekolahId, SubjekTipe subjekTipe, Long subjekId,
                                        long nominal, String refIdPg, String penyetor,
                                        String kanal, Long aktorId) {
        if (nominal <= 0) {
            throw new InvalidOperationException("Nominal top-up harus > 0");
        }
        if (refIdPg == null || refIdPg.isBlank()) {
            throw new InvalidOperationException("Referensi transaksi PG (refId) wajib diisi");
        }
        String ref = refIdPg.trim();

        String keterangan = "Top-up online"
                + (kanal == null || kanal.isBlank() ? "" : " via " + kanal)
                + (penyetor == null || penyetor.isBlank() ? "" : " oleh " + penyetor);

        HasilMutasiSaldo hasil = ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(sekolahId)
                .subjekTipe(subjekTipe)
                .subjekId(subjekId)
                .jenis(JenisMutasiSaldo.TOPUP_ONLINE)
                .nominal(nominal)
                .idempotencyKey("TOPUP-ONLINE-" + ref)
                .referensiTipe("TOPUP_ONLINE")
                .referensiId(ref)
                .keterangan(keterangan)
                .aktorId(aktorId)
                .build());

        // Audit hanya untuk mutasi baru (bukan replay idempotent).
        if (!hasil.isIdempotentReplay()) {
            auditLogger.catat(aktorId, sekolahId, "TOPUP_ONLINE", "Saldo",
                    subjekTipe + ":" + subjekId, keterangan,
                    null, String.valueOf(nominal));

            // Notifikasi ke ortu (PRD §8.4) — best-effort/fail-open.
            notifikasi.kirim(PerintahNotifikasi.builder()
                    .sekolahId(sekolahId)
                    .jenis(JenisNotifikasi.TOPUP_ONLINE)
                    .subjekTipe(subjekTipe)
                    .subjekId(subjekId)
                    .nominal(nominal)
                    .saldoSetelah(hasil.getSaldoSetelah())
                    .referensiId(ref)
                    .ringkasan("Top-up online Rp" + nominal + " berhasil"
                            + (kanal == null || kanal.isBlank() ? "" : " via " + kanal))
                    .aktorId(aktorId)
                    .build());
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

            // PRD §5.1/§9.2: koreksi bendahara diposting sebagai entri penyesuaian
            // pos "Penyesuaian Kantin" (bukan mengubah entri lama). Idempoten
            // (refId dari berita acara) & fail-open — kegagalan posting tidak
            // membatalkan koreksi saldo yang sudah tercatat.
            bukuKasPosting.postingKoreksiSaldo(sekolahId, arah == ArahMutasi.KREDIT, nominal,
                    referensiId, alasan,
                    hasil.getMutasi() == null ? null : hasil.getMutasi().getId(), aktorId);
        }

        return hasil;
    }
}
