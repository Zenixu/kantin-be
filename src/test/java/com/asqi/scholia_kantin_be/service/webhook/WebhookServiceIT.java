package com.asqi.scholia_kantin_be.service.webhook;

import com.asqi.scholia_kantin_be.component.exception.IdempotencyConflictException;
import com.asqi.scholia_kantin_be.dto.WebhookHasil;
import com.asqi.scholia_kantin_be.enums.StatusWebhook;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AUDIT KEAMANAN (B34) — idempotency event webhook pada <b>PostgreSQL nyata</b>.
 *
 * <p>Menjaga janji SECURITY.md §5: webhook yang di-retry <b>tidak diproses dua
 * kali</b>. Event id sama + payload sama ⇒ replay; payload beda ⇒ 409.
 * Ditegakkan UNIQUE {@code (sumber, event_id)} pada migrasi V12.
 */
@SpringBootTest
@Import({TestcontainersConfig.class, WebhookServiceIT.HandlerUjiConfig.class})
@EnabledIfDockerAvailable
@DisplayName("WebhookService — idempotency per (sumber, eventId)")
class WebhookServiceIT {

    private static final String SUMBER = "SKOOLIA";

    @Autowired
    private WebhookService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HandlerUji handlerUji;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE webhook_event");
        handlerUji.jumlahDipanggil.set(0);
    }

    private String hash(String s) {
        return WebhookService.hashPayload(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Retry event yang sama ⇒ handler dipanggil SEKALI (yang kedua replay)")
    void retryTidakDigandakan() {
        String payload = "{\"eventType\":\"TOPUP\",\"nominal\":50000}";
        String h = hash(payload);

        WebhookHasil pertama = service.terima(SUMBER, "evt-1", "TOPUP", 7L, Map.of("nominal", 50000), h);
        WebhookHasil kedua = service.terima(SUMBER, "evt-1", "TOPUP", 7L, Map.of("nominal", 50000), h);

        assertThat(pertama.isReplay()).isFalse();
        assertThat(kedua.isReplay()).as("retry harus dijawab sebagai replay").isTrue();
        assertThat(kedua.getStatus()).isEqualTo(StatusWebhook.DIPROSES);
        assertThat(handlerUji.jumlahDipanggil.get())
                .as("handler TIDAK boleh dipanggil dua kali untuk event id sama")
                .isEqualTo(1);

        Integer jumlah = jdbc.queryForObject(
                "SELECT COUNT(*) FROM webhook_event WHERE sumber=? AND event_id=?",
                Integer.class, SUMBER, "evt-1");
        assertThat(jumlah).as("hanya satu baris jurnal untuk event id sama").isEqualTo(1);
    }

    @Test
    @DisplayName("Event id sama tetapi payload BEDA ⇒ 409 (indikasi penyalahgunaan)")
    void payloadBedaKonflik() {
        service.terima(SUMBER, "evt-2", "TOPUP", 7L, Map.of("nominal", 50000), hash("a"));

        assertThatThrownBy(() ->
                service.terima(SUMBER, "evt-2", "TOPUP", 7L, Map.of("nominal", 999999), hash("b")))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("Event id sama di sumber berbeda ⇒ event independen")
    void sumberBerbedaIndependen() {
        WebhookHasil a = service.terima("SKOOLIA", "evt-3", "TOPUP", 7L, Map.of(), hash("x"));
        WebhookHasil b = service.terima("CALLBACK_BE", "evt-3", "TOPUP", 7L, Map.of(), hash("x"));

        assertThat(a.isReplay()).isFalse();
        assertThat(b.isReplay()).isFalse();
        Integer jumlah = jdbc.queryForObject(
                "SELECT COUNT(*) FROM webhook_event WHERE event_id=?", Integer.class, "evt-3");
        assertThat(jumlah).isEqualTo(2);
    }

    @Test
    @DisplayName("Jenis event belum ditangani ⇒ dicatat DIABAIKAN (tetap idempoten)")
    void jenisTakDikenalDiabaikan() {
        WebhookHasil h = service.terima(SUMBER, "evt-4", "TIDAK_ADA", null, Map.of(), hash("y"));
        assertThat(h.getStatus()).isEqualTo(StatusWebhook.DIABAIKAN);
        assertThat(h.isReplay()).isFalse();
        assertThat(handlerUji.jumlahDipanggil.get()).isZero();
    }

    @Test
    @DisplayName("Jurnal webhook append-only: UPDATE/DELETE ditolak trigger DB")
    void jurnalAppendOnly() {
        service.terima(SUMBER, "evt-5", "TOPUP", null, Map.of(), hash("z"));

        assertThatThrownBy(() ->
                jdbc.update("UPDATE webhook_event SET status='DIPROSES' WHERE event_id='evt-5'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() ->
                jdbc.update("DELETE FROM webhook_event WHERE event_id='evt-5'"))
                .hasMessageContaining("append-only");
    }

    /** Handler uji: mendukung event {@code TOPUP}, menghitung pemanggilan. */
    static class HandlerUji implements WebhookHandlerPort {

        final AtomicInteger jumlahDipanggil = new AtomicInteger(0);

        @Override
        public boolean mendukung(String eventType) {
            return "TOPUP".equals(eventType);
        }

        @Override
        public void tangani(KonteksWebhook konteks) {
            jumlahDipanggil.incrementAndGet();
        }
    }

    @TestConfiguration
    static class HandlerUjiConfig {
        @Bean
        HandlerUji handlerUji() {
            return new HandlerUji();
        }
    }
}
