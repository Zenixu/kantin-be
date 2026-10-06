package com.asqi.scholia_kantin_be.config.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji utilitas tanda tangan webhook — HMAC-SHA256 &amp; perbandingan konstan-waktu
 * (SECURITY.md §5).
 */
@DisplayName("TandaTanganWebhook — HMAC-SHA256")
class TandaTanganWebhookTest {

    private static final String RAHASIA = "rahasia-uji-123";
    private static final String TS = "1735689600";

    @Test
    @DisplayName("HMAC deterministik & cocok dengan nilai yang dihitung ulang")
    void hmacDeterministik() {
        byte[] badan = "{\"event\":\"x\"}".getBytes(StandardCharsets.UTF_8);
        String a = TandaTanganWebhook.hitung(RAHASIA, TS, badan);
        String b = TandaTanganWebhook.hitung(RAHASIA, TS, badan);
        assertThat(a).isEqualTo(b).hasSize(64);
    }

    @Test
    @DisplayName("Rahasia/timestamp/badan berbeda ⇒ tanda tangan berbeda")
    void sensitifTerhadapInput() {
        byte[] badan = "{}".getBytes(StandardCharsets.UTF_8);
        String dasar = TandaTanganWebhook.hitung(RAHASIA, TS, badan);

        assertThat(TandaTanganWebhook.hitung("lain", TS, badan)).isNotEqualTo(dasar);
        assertThat(TandaTanganWebhook.hitung(RAHASIA, "1735689601", badan)).isNotEqualTo(dasar);
        assertThat(TandaTanganWebhook.hitung(RAHASIA, TS, "{\"a\":1}".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(dasar);
    }

    @Test
    @DisplayName("cocok() menerima bentuk polos maupun ber-prefix sha256= (tak peduli besar/kecil)")
    void cocokToleranFormat() {
        String sig = TandaTanganWebhook.hitung(RAHASIA, TS, "{}".getBytes(StandardCharsets.UTF_8));
        assertThat(TandaTanganWebhook.cocok(sig, sig)).isTrue();
        assertThat(TandaTanganWebhook.cocok(sig, "sha256=" + sig)).isTrue();
        assertThat(TandaTanganWebhook.cocok(sig.toUpperCase(), sig)).isTrue();
        assertThat(TandaTanganWebhook.cocok(sig, sig.substring(0, 63) + "0")).isFalse();
        assertThat(TandaTanganWebhook.cocok(sig, null)).isFalse();
        assertThat(TandaTanganWebhook.cocok(null, sig)).isFalse();
    }

    @Test
    @DisplayName("hashPayload = SHA-256 hex badan; badan berbeda ⇒ hash berbeda")
    void hashPayload() {
        String h1 = TandaTanganWebhook.hashPayload("a".getBytes(StandardCharsets.UTF_8));
        String h2 = TandaTanganWebhook.hashPayload("b".getBytes(StandardCharsets.UTF_8));
        assertThat(h1).hasSize(64).isNotEqualTo(h2);
    }
}
