package com.asqi.scholia_kantin_be.service.webhook;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.dto.WebhookHasil;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.StatusWebhook;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi <b>top-up online via webhook</b> (PRD §8.2, INTEGRATIONS §5,
 * issue #37) pada <b>PostgreSQL nyata</b> (Testcontainers).
 *
 * <p>Menegakkan janji inti: saldo bertambah <b>hanya setelah</b> callback sukses
 * dan <b>callback duplikat ≠ 2×</b> (PRD §11.3) — idempotency dua lapis:
 * jurnal webhook per (sumber, eventId) &amp; ledger per refId PG (tenant-scoped).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
@DisplayName("Top-up online via webhook — idempotency & efek saldo")
class TopUpOnlineWebhookIT {

    private static final String SUMBER = "CALLBACK_BE";
    private static final long SEKOLAH = 1L;
    private static final long SISWA = 77L;

    @Autowired
    private WebhookService webhook;

    @Autowired
    private LedgerSaldoService ledger;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE webhook_event, saldo_ledger, saldo_cache, audit_log CASCADE");
    }

    private String hash(String s) {
        return WebhookService.hashPayload(s.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> payload(String refId, long nominal) {
        Map<String, Object> data = new HashMap<>();
        data.put("refId", refId);
        data.put("nominal", nominal);
        data.put("siswaId", SISWA);
        data.put("kanal", "QRIS");
        return data;
    }

    private long saldo() {
        return ledger.saldo(SEKOLAH, SubjekTipe.SISWA, SISWA);
    }

    @Test
    @DisplayName("Callback sukses ⇒ saldo bertambah & tercatat TOPUP_ONLINE")
    void callbackMenambahSaldo() {
        String body = "{\"eventType\":\"TOPUP_ONLINE_SUKSES\",\"refId\":\"PG-1\",\"nominal\":50000}";

        WebhookHasil hasil = webhook.terima(SUMBER, "evt-1", "TOPUP_ONLINE_SUKSES",
                SEKOLAH, payload("PG-1", 50_000), hash(body));

        assertThat(hasil.getStatus()).isEqualTo(StatusWebhook.DIPROSES);
        assertThat(hasil.isReplay()).isFalse();
        assertThat(saldo()).isEqualTo(50_000L);

        Integer jumlah = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saldo_ledger WHERE sekolah_id=? AND jenis='TOPUP_ONLINE'",
                Integer.class, SEKOLAH);
        assertThat(jumlah).isEqualTo(1);
    }

    @Test
    @DisplayName("Callback duplikat (event id sama, payload sama) ⇒ saldo TIDAK bertambah dua kali")
    void callbackDuplikatIdempoten() {
        String body = "{\"eventType\":\"TOPUP_ONLINE_SUKSES\",\"refId\":\"PG-2\",\"nominal\":25000}";
        String h = hash(body);

        WebhookHasil a = webhook.terima(SUMBER, "evt-2", "TOPUP_ONLINE_SUKSES", SEKOLAH, payload("PG-2", 25_000), h);
        WebhookHasil b = webhook.terima(SUMBER, "evt-2", "TOPUP_ONLINE_SUKSES", SEKOLAH, payload("PG-2", 25_000), h);

        assertThat(a.isReplay()).isFalse();
        assertThat(b.isReplay()).as("retry harus dijawab replay").isTrue();
        assertThat(saldo()).as("saldo hanya bertambah sekali").isEqualTo(25_000L);
    }

    @Test
    @DisplayName("Event id BERBEDA tetapi refId PG sama ⇒ tetap idempoten di ledger (tidak dobel)")
    void refIdSamaEventBedaTidakDobel() {
        webhook.terima(SUMBER, "evt-3a", "TOPUP_ONLINE_SUKSES", SEKOLAH,
                payload("PG-3", 10_000), hash("a"));
        // PG mengirim ulang dengan event id baru (mis. ack hilang) tetapi refId sama.
        webhook.terima(SUMBER, "evt-3b", "TOPUP_ONLINE_SUKSES", SEKOLAH,
                payload("PG-3", 10_000), hash("b"));

        assertThat(saldo()).as("refId PG sama ⇒ saldo tidak bertambah dua kali")
                .isEqualTo(10_000L);
    }

    @Test
    @DisplayName("refId PG sama di sekolah BERBEDA ⇒ mutasi independen (tenant-scoped)")
    void refIdSamaLintasTenantIndependen() {
        webhook.terima(SUMBER, "evt-4a", "TOPUP_ONLINE_SUKSES", SEKOLAH,
                payload("PG-SAMA", 5_000), hash("a"));

        Map<String, Object> dataB = new HashMap<>();
        dataB.put("refId", "PG-SAMA");
        dataB.put("nominal", 7_000);
        dataB.put("siswaId", 88L);
        webhook.terima(SUMBER, "evt-4b", "TOPUP_ONLINE_SUKSES", 2L, dataB, hash("b"));

        assertThat(saldo()).isEqualTo(5_000L);
        assertThat(ledger.saldo(2L, SubjekTipe.SISWA, 88L)).isEqualTo(7_000L);
    }

    @Test
    @DisplayName("Payload tanpa refId ⇒ 400 (kunci idempotency wajib)")
    void tanpaRefIdDitolak() {
        Map<String, Object> data = new HashMap<>();
        data.put("nominal", 10_000);
        data.put("siswaId", SISWA);

        assertThatThrownBy(() -> webhook.terima(SUMBER, "evt-5", "TOPUP_ONLINE_SUKSES",
                SEKOLAH, data, hash("x")))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("refId");
        assertThat(saldo()).isZero();
    }

    @Test
    @DisplayName("Payload tanpa sekolahId ⇒ 400 (tenant scoping wajib, PRD §11.4)")
    void tanpaSekolahIdDitolak() {
        Map<String, Object> data = new HashMap<>();
        data.put("refId", "PG-6");
        data.put("nominal", 10_000);
        data.put("siswaId", SISWA);

        assertThatThrownBy(() -> webhook.terima(SUMBER, "evt-6", "TOPUP_ONLINE_SUKSES",
                null, data, hash("x")))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("sekolahId");
    }

    @Test
    @DisplayName("Jenis event lain ⇒ DIABAIKAN, saldo tidak berubah")
    void jenisLainDiabaikan() {
        WebhookHasil hasil = webhook.terima(SUMBER, "evt-7", "KARTU_SINKRON",
                SEKOLAH, payload("PG-7", 10_000), hash("x"));

        assertThat(hasil.getStatus()).isEqualTo(StatusWebhook.DIABAIKAN);
        assertThat(saldo()).isZero();
    }

    @Test
    @DisplayName("Audit tercatat untuk top-up online baru")
    void auditTercatat() {
        webhook.terima(SUMBER, "evt-8", "TOPUP_ONLINE_SUKSES", SEKOLAH,
                payload("PG-8", 30_000), hash("x"));

        Integer jumlah = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE sekolah_id=? AND aksi='TOPUP_ONLINE'",
                Integer.class, SEKOLAH);
        assertThat(jumlah).isEqualTo(1);
    }
}
