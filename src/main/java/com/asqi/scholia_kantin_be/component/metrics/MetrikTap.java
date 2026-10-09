package com.asqi.scholia_kantin_be.component.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Metrik Prometheus untuk alur <b>tap kasir</b> — issue #146.
 *
 * <p>PRD §12 (tabel SLO no.8) menetapkan <b>tap p95 ≤ 1 detik</b> termasuk
 * lookup kartu ke SKOOLIA. Tanpa metrik, SLO itu tidak bisa diukur, dipantau,
 * atau diberi alert. Kelas ini merekam durasi tiap tap sebagai {@link Timer}
 * Micrometer yang diekspos di {@code /actuator/prometheus}.
 *
 * <p>Timer diberi <i>service level objectives</i> (SLO buckets) di sekitar
 * ambang 1 detik (250 ms, 500 ms, 1 dtk) sehingga {@code histogram_quantile}
 * di Prometheus/Grafana bisa menghitung p95 langsung, dan alert dapat dipasang
 * pada rasio bucket melewati 1 detik.
 *
 * <p>Tag {@code outcome} memisahkan tap yang <b>sukses</b>, <b>menunggu
 * konfirmasi</b> petugas (PRD §6.1), dan <b>gagal</b> (validasi/konflik) —
 * supaya latensi jalur sukses tidak tercampur dengan jalur error.
 */
@Component
public class MetrikTap {

    /** Nama meter (Prometheus: {@code kantin_tap_duration_seconds}). */
    public static final String NAMA_TIMER = "kantin.tap.duration";

    public static final String OUTCOME_SUKSES = "sukses";
    public static final String OUTCOME_MENUNGGU = "menunggu_konfirmasi";
    public static final String OUTCOME_GAGAL = "gagal";

    private final MeterRegistry registry;

    public MetrikTap(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Catat durasi satu tap.
     *
     * @param durasi  waktu proses tap (dari masuk sampai respons dibangun)
     * @param outcome {@link #OUTCOME_SUKSES}, {@link #OUTCOME_MENUNGGU}, atau
     *                {@link #OUTCOME_GAGAL}
     */
    public void catat(Duration durasi, String outcome) {
        Timer.builder(NAMA_TIMER)
                .description("Durasi satu tap kasir — SLO p95 <= 1 dtk (PRD §12)")
                .tag("outcome", outcome)
                .serviceLevelObjectives(
                        Duration.ofMillis(250),
                        Duration.ofMillis(500),
                        Duration.ofSeconds(1))
                .register(registry)
                .record(durasi);
    }
}
