package com.asqi.scholia_kantin_be.config.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AUDIT KEAMANAN (B34) — verifikasi tanda tangan &amp; anti-replay webhook.
 *
 * <p>Menjaga janji SECURITY.md §5: request webhook tanpa HMAC sah atau dengan
 * timestamp kedaluwarsa <b>wajib ditolak</b>.
 */
@DisplayName("AUDIT webhook — signature salah/kedaluwarsa ditolak")
class WebhookSignatureVerifierTest {

    private static final String RAHASIA = "rahasia-webhook-uji";
    private static final long SEKARANG = 1_735_689_600L;
    private static final byte[] BADAN = "{\"event\":\"topup\"}".getBytes(StandardCharsets.UTF_8);

    private WebhookProperties props(String rahasia) {
        WebhookProperties p = new WebhookProperties();
        p.setSecret(rahasia);
        p.setToleranceSeconds(300);
        return p;
    }

    private String sig(String rahasia, String ts) {
        return TandaTanganWebhook.hitung(rahasia, ts, BADAN);
    }

    @Test
    @DisplayName("Signature sah & timestamp dalam jendela ⇒ SAH")
    void sah() {
        var v = new WebhookSignatureVerifier(props(RAHASIA));
        String ts = String.valueOf(SEKARANG);
        assertThat(v.verifikasi(ts, sig(RAHASIA, ts), BADAN, SEKARANG))
                .isEqualTo(WebhookSignatureVerifier.Hasil.SAH);
    }

    @Test
    @DisplayName("Signature salah ⇒ SIGNATURE_SALAH (bukan diproses)")
    void signatureSalah() {
        var v = new WebhookSignatureVerifier(props(RAHASIA));
        String ts = String.valueOf(SEKARANG);
        String palsu = sig("rahasia-penyerang", ts);
        assertThat(v.verifikasi(ts, palsu, BADAN, SEKARANG))
                .isEqualTo(WebhookSignatureVerifier.Hasil.SIGNATURE_SALAH);
    }

    @Test
    @DisplayName("Badan diubah setelah ditandatangani ⇒ signature tak cocok")
    void badanDiubah() {
        var v = new WebhookSignatureVerifier(props(RAHASIA));
        String ts = String.valueOf(SEKARANG);
        String tandaTanganBadanAsli = sig(RAHASIA, ts);
        byte[] badanLain = "{\"event\":\"topup\",\"nominal\":999999}".getBytes(StandardCharsets.UTF_8);
        assertThat(v.verifikasi(ts, tandaTanganBadanAsli, badanLain, SEKARANG))
                .isEqualTo(WebhookSignatureVerifier.Hasil.SIGNATURE_SALAH);
    }

    @Test
    @DisplayName("Timestamp kedaluwarsa (anti-replay) ⇒ TIMESTAMP_KEDALUWARSA")
    void timestampKedaluwarsa() {
        var v = new WebhookSignatureVerifier(props(RAHASIA));
        String tsLama = String.valueOf(SEKARANG - 10_000); // jauh di luar 300 dtk
        // Signature sah atas ts lama, tapi ditolak karena di luar jendela.
        assertThat(v.verifikasi(tsLama, sig(RAHASIA, tsLama), BADAN, SEKARANG))
                .isEqualTo(WebhookSignatureVerifier.Hasil.TIMESTAMP_KEDALUWARSA);
    }

    @Test
    @DisplayName("Timestamp di masa depan jauh ⇒ TIMESTAMP_KEDALUWARSA")
    void timestampMasaDepan() {
        var v = new WebhookSignatureVerifier(props(RAHASIA));
        String tsDepan = String.valueOf(SEKARANG + 10_000);
        assertThat(v.verifikasi(tsDepan, sig(RAHASIA, tsDepan), BADAN, SEKARANG))
                .isEqualTo(WebhookSignatureVerifier.Hasil.TIMESTAMP_KEDALUWARSA);
    }

    @Test
    @DisplayName("Header kurang ⇒ HEADER_KURANG")
    void headerKurang() {
        var v = new WebhookSignatureVerifier(props(RAHASIA));
        assertThat(v.verifikasi(null, null, BADAN, SEKARANG))
                .isEqualTo(WebhookSignatureVerifier.Hasil.HEADER_KURANG);
        assertThat(v.verifikasi(String.valueOf(SEKARANG), "", BADAN, SEKARANG))
                .isEqualTo(WebhookSignatureVerifier.Hasil.HEADER_KURANG);
    }

    @Test
    @DisplayName("Timestamp bukan angka ⇒ TIMESTAMP_TIDAK_VALID")
    void timestampBukanAngka() {
        var v = new WebhookSignatureVerifier(props(RAHASIA));
        assertThat(v.verifikasi("besok", sig(RAHASIA, "besok"), BADAN, SEKARANG))
                .isEqualTo(WebhookSignatureVerifier.Hasil.TIMESTAMP_TIDAK_VALID);
    }

    @Test
    @DisplayName("Rahasia belum dikonfigurasi ⇒ TANPA_KONFIGURASI (fail-closed)")
    void tanpaKonfigurasi() {
        var v = new WebhookSignatureVerifier(props(""));
        String ts = String.valueOf(SEKARANG);
        assertThat(v.verifikasi(ts, "apa saja", BADAN, SEKARANG))
                .isEqualTo(WebhookSignatureVerifier.Hasil.TANPA_KONFIGURASI);
    }
}
