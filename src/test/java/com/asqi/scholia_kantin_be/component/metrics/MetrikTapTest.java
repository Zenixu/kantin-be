package com.asqi.scholia_kantin_be.component.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji metrik tap (issue #146) — memastikan timer tercatat dengan tag
 * {@code outcome} &amp; SLO bucket di sekitar ambang 1 detik (PRD §12).
 *
 * <p>Memakai {@link SimpleMeterRegistry} (tanpa Spring/DB), jadi cepat dan
 * tidak butuh Docker.
 */
@DisplayName("MetrikTap — timer SLO tap")
class MetrikTapTest {

    private MeterRegistry registry;
    private MetrikTap metrik;

    @BeforeEach
    void siapkan() {
        registry = new SimpleMeterRegistry();
        metrik = new MetrikTap(registry);
    }

    @Test
    @DisplayName("mencatat durasi tap sukses & menambah jumlah pada tag outcome")
    void catatSukses() {
        metrik.catat(Duration.ofMillis(120), MetrikTap.OUTCOME_SUKSES);
        metrik.catat(Duration.ofMillis(300), MetrikTap.OUTCOME_SUKSES);

        Timer timer = registry.find(MetrikTap.NAMA_TIMER)
                .tag("outcome", MetrikTap.OUTCOME_SUKSES)
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(2);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(420.0);
    }

    @Test
    @DisplayName("memisahkan jalur sukses, menunggu konfirmasi, dan gagal")
    void pisahkanOutcome() {
        metrik.catat(Duration.ofMillis(100), MetrikTap.OUTCOME_SUKSES);
        metrik.catat(Duration.ofMillis(100), MetrikTap.OUTCOME_MENUNGGU);
        metrik.catat(Duration.ofMillis(100), MetrikTap.OUTCOME_GAGAL);

        assertThat(registry.find(MetrikTap.NAMA_TIMER)
                .tag("outcome", MetrikTap.OUTCOME_SUKSES).timer().count()).isEqualTo(1);
        assertThat(registry.find(MetrikTap.NAMA_TIMER)
                .tag("outcome", MetrikTap.OUTCOME_MENUNGGU).timer().count()).isEqualTo(1);
        assertThat(registry.find(MetrikTap.NAMA_TIMER)
                .tag("outcome", MetrikTap.OUTCOME_GAGAL).timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("SLO bucket histogram memuat ambang 1 detik (PRD §12)")
    void bucketSloMemuatAmbangSatuDetik() {
        metrik.catat(Duration.ofMillis(50), MetrikTap.OUTCOME_SUKSES);

        // Micrometer mempublikasikan SLO sebagai meter histogram dengan tag
        // `le`. Pastikan bucket 1 detik (ambang SLO tap) terdaftar — tanpa ini
        // histogram_quantile di Prometheus tak bisa menghitung pelanggaran SLO.
        boolean adaBucketSatuDetik = registry.getMeters().stream()
                .anyMatch(m -> m.getId().getName().contains("histogram")
                        && m.getId().getTags().stream()
                                .anyMatch(t -> "le".equals(t.getKey())
                                        && "1".equals(t.getValue())));

        assertThat(adaBucketSatuDetik)
                .as("bucket le=1 (ambang SLO tap) harus ada")
                .isTrue();
    }
}
