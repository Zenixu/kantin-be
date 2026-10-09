# Observability — Metrik & SLO (issue #146)

Dokumen ini menjelaskan metrik yang diekspos `kantin-be` dan cara memantau
**SLO tap p95 ≤ 1 detik** (PRD §12).

## 1. Ringkas

| Hal | Nilai |
|---|---|
| Endpoint scrape | `GET /actuator/prometheus` |
| Registry | Micrometer + `micrometer-registry-prometheus` |
| Format | Prometheus text exposition |
| Autentikasi | **publik** secara default (`kantin.metrics.public=true`); set `KANTIN_METRICS_PUBLIC=false` untuk menutup di balik JWT |
| Health | `GET /actuator/health` (+ `/liveness`, `/readiness` bila probes aktif) |

## 2. Metrik tap (SLO utama)

Meter: **`kantin_tap_duration_seconds`** (timer + histogram SLO).

Setiap tap kasir direkam di `TapService.tap()` dengan tag `outcome`:

| `outcome` | Arti |
|---|---|
| `sukses` | tap selesai normal |
| `menunggu_konfirmasi` | tap butuh konfirmasi petugas (PRD §6.1) |
| `gagal` | validasi/konflik (saldo kurang, kartu terkunci, dst.) |

SLO bucket yang dipasang: **250 ms, 500 ms, 1 dtk**. Ambang **1 dtk** adalah
batas SLO (PRD §12) — pelanggaran = tap yang durasinya > 1 detik.

### Contoh query PromQL

```promql
# p95 durasi tap (jalur sukses) — harus <= 1 dtk
histogram_quantile(
  0.95,
  sum by (le) (rate(kantin_tap_duration_seconds_bucket{outcome="sukses"}[5m]))
)

# rasio tap sukses yang melewati ambang SLO (bucket +Inf - le=1)
1 - (
  sum(rate(kantin_tap_duration_seconds_bucket{outcome="sukses",le="1.0"}[5m]))
  / sum(rate(kantin_tap_duration_seconds_count{outcome="sukses"}[5m]))
)

# laju tap gagal
sum(rate(kantin_tap_duration_seconds_count{outcome="gagal"}[5m]))
```

### Contoh alert (Prometheus rule)

```yaml
groups:
  - name: kantin-slo
    rules:
      - alert: TapP95MelebihiSLO
        expr: |
          histogram_quantile(
            0.95,
            sum by (le) (rate(kantin_tap_duration_seconds_bucket{outcome="sukses"}[5m]))
          ) > 1
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Tap p95 > 1 dtk selama 10 menit (langgar PRD §12)"
```

## 3. Metrik HTTP otomatis

Spring Boot juga mengekspos `http_server_requests_seconds_*`. Histogram
percentile diaktifkan (`management.distributions.percentiles-histogram.http.server.requests=true`)
sehingga latensi endpoint (termasuk `POST /api/kasir/tap`) dapat dihitung p95
tanpa instrumentasi tambahan.

## 4. Konfigurasi

| Properti | Default | Fungsi |
|---|---|---|
| `management.endpoints.web.exposure.include` | `health,info,prometheus` | endpoint yang diekspos |
| `management.endpoint.health.probes.enabled` | `true` | `/liveness`, `/readiness` |
| `management.metrics.tags.application` | nama aplikasi | tag global tiap deret |
| `management.distributions.percentiles-histogram.http.server.requests` | `true` | histogram latensi HTTP |
| `kantin.metrics.public` (`KANTIN_METRICS_PUBLIC`) | `true` | `/actuator/prometheus` tanpa token |

## 5. Scrape config Prometheus

```yaml
scrape_configs:
  - job_name: kantin-be
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['kantin-be:8082']
```

Bila `KANTIN_METRICS_PUBLIC=false`, tambahkan `authorization` bearer token pada
scrape config karena endpoint kembali butuh JWT.
