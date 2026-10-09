# Uji Beban Tap — SLO p95 ≤ 1 detik (issue #145)

Dokumen ini menjelaskan cara **mengukur** SLO utama kantin-be:

> **Waktu respons tap (p95) ≤ 1 detik** pada jam istirahat, *termasuk lookup
> kartu ke data SKOOLIA* — PRD §12 no.8 & tabel SLO.

Sebelumnya tidak ada artefak uji beban apa pun di repo (hanya unit/integration
test *correctness*). Berkas uji beban ada di [`load/`](../load/):

| Berkas | Isi |
|---|---|
| `load/k6/tap-slo.js` | Skrip k6: skenario N kasir tap bersamaan + threshold SLO |
| `load/seed/seed-loadtest.sql` | Seed data (kartu, menu, stok, saldo, sesi) |
| `.github/workflows/uji-beban-nightly.yml` | Wiring CI nightly (opsional, tak memblok PR) |

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

> Isi tabel ini setelah menjalankan pada lingkungan target (runner CI / staging).
> Lingkungan dev tanpa Docker tidak dapat menjalankan skenario nyata.

| Tanggal | Lingkungan | TAP_RATE | Durasi | p95 (ms) | p99 (ms) | Gagal | Lulus SLO? |
|---|---|---|---|---|---|---|---|
| _(isi)_ | _(staging/CI)_ | 50 | 2m | | | | |
| _(isi)_ | _(staging/CI)_ | 100 | 3m | | | | |

**Bila p95 > 1 dtk → buka temuan optimasi** (mis. indeks DB, cache, N+1 query,
ukuran koneksi pool) sebagai issue terpisah dan tautkan ke #145.

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
— **tidak** memblok PR (butuh Docker + boot app). Lihat berkas untuk detail
langkah (spin up Postgres/Redis via service container, boot jar, seed, `k6 run`).
