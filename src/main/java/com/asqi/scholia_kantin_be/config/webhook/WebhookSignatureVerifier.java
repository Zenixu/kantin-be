package com.asqi.scholia_kantin_be.config.webhook;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Verifikator tanda tangan webhook (SECURITY.md §5, BUGS-DITEMUKAN B34).
 *
 * <p>Terpisah dari filter HTTP agar logika keamanan bisa diuji tanpa servlet.
 * Semua jalur <b>fail-closed</b>: ragu ⇒ tolak.
 */
@Component
@RequiredArgsConstructor
public class WebhookSignatureVerifier {

    /** Hasil verifikasi — dipetakan ke status HTTP oleh {@link WebhookSignatureFilter}. */
    public enum Hasil {
        /** Signature sah & timestamp dalam jendela. */
        SAH,
        /** Rahasia belum dikonfigurasi — endpoint ditutup (503). */
        TANPA_KONFIGURASI,
        /** Header timestamp/signature tidak ada. */
        HEADER_KURANG,
        /** Timestamp bukan angka. */
        TIMESTAMP_TIDAK_VALID,
        /** Timestamp di luar jendela toleransi (anti-replay). */
        TIMESTAMP_KEDALUWARSA,
        /** Signature tidak cocok. */
        SIGNATURE_SALAH
    }

    private final WebhookProperties properties;

    /** Verifikasi memakai waktu server saat ini. */
    public Hasil verifikasi(String timestamp, String signature, byte[] badan) {
        return verifikasi(timestamp, signature, badan, Instant.now().getEpochSecond());
    }

    /**
     * Verifikasi dengan waktu acuan eksplisit (untuk pengujian deterministik).
     *
     * @param timestamp epoch detik dari header (string)
     * @param signature HMAC hex dari header
     * @param badan     badan request <b>mentah</b> (byte persis yang dikirim)
     * @param sekarangEpochDetik waktu acuan untuk cek anti-replay
     */
    public Hasil verifikasi(String timestamp, String signature, byte[] badan, long sekarangEpochDetik) {
        String rahasia = properties.getSecret();
        if (rahasia == null || rahasia.isBlank()) {
            // Fail-closed: tanpa rahasia kita tak bisa memverifikasi apa pun.
            return Hasil.TANPA_KONFIGURASI;
        }
        if (timestamp == null || timestamp.isBlank() || signature == null || signature.isBlank()) {
            return Hasil.HEADER_KURANG;
        }

        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return Hasil.TIMESTAMP_TIDAK_VALID;
        }

        long toleransi = properties.getToleranceSeconds();
        long batasBawah = sekarangEpochDetik - toleransi;
        long batasAtas = sekarangEpochDetik + toleransi;
        if (ts < batasBawah || ts > batasAtas) {
            return Hasil.TIMESTAMP_KEDALUWARSA;
        }

        String diharapkan = TandaTanganWebhook.hitung(rahasia, timestamp.trim(), badan);
        return TandaTanganWebhook.cocok(diharapkan, signature)
                ? Hasil.SAH
                : Hasil.SIGNATURE_SALAH;
    }
}
