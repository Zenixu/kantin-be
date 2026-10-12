# Uji Beban Tap — SLO p95 ≤ 1 detik (issue #145)

Dokumen ini menjelaskan cara **mengukur** SLO utama kantin-be:

> **Waktu respons tap (p95) ≤ 1 detik** pada jam istirahat, *termasuk lookup
> kartu ke data SKOOLIA* — PRD §12 no.8 & tabel SLO.

Sebelumnya tidak ada artefak uji beban apa pun di repo (hanya unit/integration
test *correctness*). Berkas uji beban ada di [`load/`](../load/):

| Berkas | Isi |
|---|---|
| `load/k6/tap-slo.js` | Skrip k6: skenario N kasir tap bersamaan + threshold SLO |
| `load/k6/p95-prometheus.py` | Hitung p95 tap dari metrik **server** `kantin_tap_duration_seconds` (silang-cek AC #2, issue #172) |
| `load/seed/seed-loadtest.sql` | Seed data (kartu, menu, stok, saldo, sesi) |
| `.github/workflows/uji-beban-nightly.yml` | Wiring CI nightly: jalankan **50–100 tap/dtk** + silang-cek Prometheus |

## 1. Alat & alasan

**k6** dipilih (dibanding JMeter/Gatling): skrip berbasis JS yang ringkas,
menghasilkan **p95 langsung** lewat `thresholds`, mudah dijadikan gerbang CI,
dan tak menambah dependensi ke `pom.xml` (k6 berdiri sendiri). Kantin-be adalah
layanan tap yang tetap Java (ADR-0007); k6 hanya klien beban eksternal.

## 2. Menjalankan (lokal)

> Uji beban **wajib** memakai PostgreSQL & Redis **nyata** (bukan H2/embedded),
> agar latensi yang diukur mewakili produksi.

```bash
# 1) Infrastruktur
docker compose up -d
docker compose ps            # pastikan postgres & redis "healthy"

# 2) Jalankan aplikasi dengan profil uji beban
#    - loadtest     : aktifkan KartuLookupLoadTest (lookup Kartu Tamu nyata)
#    - dev-login    : agar skrip bisa minta token (kantin-be tanpa login sendiri)
#    - rate limit off: agar 429 tidak mengotori pengukuran latensi jalur sukses
SPRING_PROFILES_ACTIVE=local,loadtest \
KANTIN_DEV_LOGIN_ENABLED=true \
KANTIN_RATE_LIMIT_ENABLED=false \
  ./mvnw spring-boot:run

# 3) Seed data uji (sekali)
psql "postgresql://kantin:kantin_dev@localhost:5432/kantin_db" -f load/seed/seed-loadtest.sql

# 4) Jalankan k6
k6 run load/k6/tap-slo.js
#   atau laju/tempo khusus:
TAP_RATE=100 DURATION=3m k6 run load/k6/tap-slo.js
```

> **Tanpa dev-login:** set `TOKEN=<jwt>` (token RS256 yang diterima kantin-be,
> mis. dari `scripts/dev/mint-jwt-dummy.sh staf --sekolah 1 --role PETUGAS_KANTIN`)
> agar skrip tidak perlu login.

> **Tanpa Docker / pakai Podman:** bila mesin hanya punya **Podman** (rootless,
> tanpa sudo), langkah 1 & 4 tetap jalan:
> ```bash
> podman run -d --name kantin-pg-lt -e POSTGRES_DB=kantin_db \
>   -e POSTGRES_USER=kantin -e POSTGRES_PASSWORD=kantin_dev -p 55432:5432 postgres:18-alpine
> podman run -d --name kantin-redis-lt -p 56379:6379 redis:7-alpine
> # arahkan app (env menang atas application-local.properties):
> SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:55432/kantin_db?stringtype=unspecified' \
> SPRING_DATASOURCE_USERNAME=kantin SPRING_DATASOURCE_PASSWORD=kantin_dev \
> SPRING_DATA_REDIS_PORT=56379 SPRING_PROFILES_ACTIVE=local,loadtest \
> KANTIN_DEV_LOGIN_ENABLED=true KANTIN_RATE_LIMIT_ENABLED=false ./mvnw -o spring-boot:run
> # k6 juga lewat Podman (host network agar menjangkau app di localhost):
> podman run --rm -i --network=host -e BASE_URL=http://localhost:8082 -e TOKEN="$TOKEN" \
>   -v "$PWD/load:/load:ro,z" grafana/k6 run /load/k6/tap-slo.js
> ```

### Opsi env skrip k6

| Env | Default | Arti |
|---|---|---|
| `BASE_URL` | `http://localhost:8082` | Alamat kantin-be |
| `TOKEN` | *(kosong)* | JWT; bila kosong → login via shim dev |
| `TAP_RATE` | `50` | Tap/detik (50–100 = jam istirahat) |
| `DURATION` | `2m` | Lama skenario |
| `SEKOLAH` / `TITIK_KASIR` / `MENU_ID` / `QTY` | `1` / `1` / `1` / `1` | Data seed |
| `KARTU_COUNT` | `20` | Jumlah kartu uji (sebar beban) |
| `P95_MS` | `1000` | Ambang SLO (ms) |
| `MAX_FAIL_RATE` | `0.01` | Ambang rasio gagal |

## 3. Yang diukur

- **`tap_ms`** — durasi `POST /api/kasir/tap` (Trend, **p95** = gerbang SLO).
- **`tap_ok`** — rasio tap sukses (HTTP 200 + `body.code == 200`).
- **`tap_rate_limited`** — rasio 429 (harus ~0; matikan rate limit saat uji).
- **`http_req_failed`** — rasio error HTTP.

Jalur pending (`POST /api/kasir/tap/{pendingId}/konfirmasi`) dapat diuji dengan
menyalakan `konfirmasi_manual=true` pada `sekolah_kantin_config`; skrip ini fokus
jalur **sukses langsung** (default `konfirmasi_manual=false`).

Sisi server, latensi yang sama juga terlihat di metrik Prometheus
**`kantin_tap_duration_seconds`** (lihat [`docs/OBSERVABILITY.md`](OBSERVABILITY.md)),
sehingga hasil k6 dapat disilangkan dengan dashboard.

## 4. Ambang regresi (gerbang CI)

Build **gagal** bila salah satu tak terpenuhi:

| Metrik | Ambang | Alasan |
|---|---|---|
| `tap_ms` p95 | **< 1000 ms** | SLO PRD §12 no.8 |
| `tap_ok` | **> 99%** | Regresi fungsional |
| `http_req_failed` | **< 1%** | Tidak ada error 5xx masif |

## 5. Hasil & catatan pengukuran

> Bagian §5.1 (laptop dev) dan §5.2 (**runner target**, issue #172) berisi hasil
> pengukuran nyata. Angka §5.2 adalah bukti yang dipakai untuk Acceptance
> Criteria issue #172.

### 5.1 Hasil — 2026-10-12 (laptop dev, via Podman)

Lingkungan: **laptop dev** (Intel i5-8265U, 4 fisik/8 thread, 15 GiB RAM,
Fedora 44) — **bukan** runner khusus. PostgreSQL 18 & Redis 7 via **Podman**
(rootless), app `local,loadtest`, rate-limit OFF, `kantin_tap_duration_seconds`
sebagai silang-cek. Seed `seed-loadtest.sql` (1 menu, 20 kartu).

| TAP_RATE | Durasi | p95 (ms) | p99 (ms) | Gagal | Lulus SLO? |
|---|---|---|---|---|---|
| 5 | 30s | ~60 | ~90 | 0% | ✅ |
| 10 | 30s | 102 | 129 | 0% | ✅ |
| 20 | 30s | 111 | 154 | 0% | ✅ |
| 30 | 30s | 225 | 352 | 0% | ✅ |
| 40 | 30s | 1 990 | 2 310 | 0% | ❌ (mulai saturasi) |
| 50 | 2m | 7 710 | 8 720 | 0% | ❌ (jenuh) |

- **Fungsional 100%**: `tap_ok` = 100%, `http_req_failed` = 0%, `tap_rate_limited` = 0% pada **semua** laju → tidak ada error/5xx, hanya latensi.
- **Kapasitas**: di laptop ini, SLO p95 ≤ 1 dtk **terpenuhi s.d. ~30 tap/dtk**; di atas ~35–40 tap/dtk latensi naik tajam (throughput jenuh ~36–41 req/s). Silang-cek server (`kantin_tap_duration_seconds`) **konsisten** (sum/count ≈ 4,93 dtk, max 12,1 dtk pada laju 50).
- **Diagnosa**: jenuh karena **kapasitas host/JVM** (1 JVM + 1 PG di laptop yang juga dipakai kerja lain, load avg ~10 saat uji), **bukan bug korektnes**. Uji pembanding menyebar tap ke **20 menu** (hot-row `stok_cache` `FOR UPDATE` dihapus) hanya menaikkan p95 7,71 → 5,80 dtk — perbaikan kecil, jadi bukan hot-row yang dominan.

### 5.2 Hasil — 2026-10-12 (runner target GitHub Actions, issue #172) ✅

**Inilah validasi SLO pada laju target (50–100 tap/dtk) di lingkungan memadai
yang diminta issue #172.** Dijalankan lewat workflow nightly
`.github/workflows/uji-beban-nightly.yml` (`workflow_dispatch`), runner
`ubuntu-latest`, PostgreSQL 18 & Redis 7 sebagai *service container*, app
`local,loadtest`, rate-limit OFF. Seed `seed-loadtest.sql` (1 menu, 20 kartu).

**Steady state (setelah warmup 30 dtk @20 tap/dtk)** — run
[38235819198](https://github.com/Zenixu/kantin-be/actions/runs/38235819198):

| TAP_RATE | Durasi | p95 klien (ms) | p99 (ms) | p95 server (dtk) | Gagal | Lulus SLO? |
|---|---|---|---|---|---|---|
| 50 | 2m | **9** | 15 | 0,238 | 0% | ✅ |
| 75 | 2m | **7** | 14 | 0,238 | 0% | ✅ |
| 100 | 2m | **6** | 14 | 0,238 | 0% | ✅ |

- **Fungsional 100%**: `tap_ok` = 100%, `http_req_failed` = 0%, `tap_rate_limited` = 0% pada **semua** laju.
- **SLO terpenuhi di seluruh rentang target**: p95 klien 6–9 ms ≪ 1 000 ms; p95 **sisi server** (`kantin_tap_duration_seconds`, Prometheus) 0,238 dtk — konsisten (Acceptance Criteria #2 issue #172). Tiap laju memakai snapshot metrik sebelum/sesudah (`prom-sebelum-*.txt`/`prom-sesudah-*.txt`) agar p95 server dihitung per laju.
- **Throughput** tepat sesuai target: 50 → ~50 req/s, 75 → ~75 req/s, 100 → ~100 req/s (k6 `http_reqs.rate`), tanpa 429/5xx.

### 5.3 Catatan penting — JVM dingin vs hangat (temuan #172)

Pengukuran **100 tap/dtk pada JVM yang baru boot (tanpa warmup)** **melanggar**
SLO: p95 klien ≈ **1 163–1 398 ms** (dua run independen), sementara **median
tetap ~7 ms** dan **p95 sisi server ~0,24 dtk**. Artinya tail berasal dari
**pemanasan JIT/kompilasi + pengisian pool koneksi pada detik-detik awal**, bukan
kapasitas pemrosesan steady state:

| Kondisi (100 tap/dtk) | p95 klien | median | p95 server | Lulus? |
|---|---|---|---|---|
| JVM **dingin** (langsung setelah boot) | ~1 163–1 398 ms | ~7 ms | ~0,24 dtk | ❌ |
| JVM **hangat** (setelah warmup) | ~6–12 ms | ~5 ms | ~0,24 dtk | ✅ |

Karena SLO PRD §12 no.8 berbicara tentang **jam istirahat** (beban steady state,
bukan detik pertama setelah deploy), workflow nightly kini menjalankan **warmup
30 dtk @20 tap/dtk** sebelum pengukuran, sehingga gerbang mengukur kapasitas
steady state dan tidak flaky. **Temuan cold-start ini tetap dicatat** sebagai
kandidat optimasi terpisah (mis. `-XX:TieredStopAtLevel`, CDS/AppCDS, atau
panaskan pool saat start) — lihat tindak lanjut §5.4.

### 5.4 Kesimpulan & tindak lanjut

- **SLO p95 ≤ 1 dtk pada laju target (50–100 tap/dtk) TERPENUHI** di runner target
  (GitHub Actions) — p95 klien 6–9 ms, p95 server 0,238 dtk (§5.2). Bukti
  tersimpan sebagai artefak run nightly (Acceptance Criteria #172).
- **Cold-start** (100 tap/dtk tanpa warmup) melanggar SLO (§5.3). Karena PRD
  mengukur steady state jam istirahat, warmup ditambahkan; optimasi cold-start
  (JIT/CDS/pool) dibuka sebagai temuan terpisah.
- Bila p95 **steady state** kembali > 1 dtk di runner target (regresi) → buka
  temuan optimasi (indeks DB, cache, N+1, ukuran pool Hikari, tuning JVM)
  sebagai issue terpisah dan tautkan ke #145/#161/#172.

## 6. Lookup kartu ke SKOOLIA (Q7)

SLO ini *termasuk* lookup kartu. Kontrak lookup **siswa** ke admin-be masih
terbuka (OPEN-QUESTIONS **Q7**, ADR-0004) → implementasi nyata belum ada, dan
`KartuLookupFallback` bersifat **fail-closed** ("kartu tidak dikenal").

Agar uji beban tetap bisa menembus jalur sukses **tanpa** menunggu Q7, profil
`loadtest` mengaktifkan `KartuLookupLoadTest`: lookup ke tabel **Kartu Tamu**
(data milik kantin-be sendiri, PRD §9.4), bukan ke siswa. Saat Q7 terjawab:

1. Tambahkan `SiswaKartuClient` (REST) dengan **timeout ≤ 250 ms** dan
   **fail-closed** bila admin-be tak merespons (jangan diam-diam meloloskan).
2. Jalankan skrip k6 yang sama (tanpa perubahan) — kini lookup memanggil
   admin-be nyata, sehingga SLO terukur end-to-end.

## 7. Wiring CI (nightly)

[`.github/workflows/uji-beban-nightly.yml`](../.github/workflows/uji-beban-nightly.yml)
menjalankan uji beban **terjadwal (nightly)** dan **manual** (`workflow_dispatch`)
— **tidak** memblok PR (butuh Docker + boot app). Langkah:

1. Spin up PostgreSQL 18 & Redis 7 (service container) + boot jar + seed.
2. **Warmup** 30 dtk @20 tap/dtk (di luar pengukuran SLO — lihat §5.3).
3. Untuk **tiap laju** di `TAP_RATES` (default `50 75 100`, seluruh rentang target
   PRD): snapshot `/actuator/prometheus` **sebelum**, jalankan `k6 run` (threshold
   k6 = gerbang klien), snapshot **sesudah**, lalu silang-cek p95 server dengan
   `load/k6/p95-prometheus.py` (gerbang server).
4. Run **merah** bila k6 ATAU silang-cek Prometheus melanggar SLO pada laju mana
   pun. Semua ringkasan + snapshot metrik diunggah sebagai artefak
   `hasil-uji-beban` (retensi 14 hari) — inilah bukti AC issue #172.

Dispatch satu laju saja (mis. untuk investigasi):

```bash
gh workflow run uji-beban-nightly.yml -f tap_rate=100 -f duration=2m
# atau via UI: Actions → "Uji Beban (nightly)" → Run workflow → isi tap_rate
```
