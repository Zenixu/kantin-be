# 🍽️ kantin-be — Modul Kantin Cashless SKOOLIA (Backend)

Backend modul **kantin sekolah cashless** untuk platform SKOOLIA. 100% non-tunai:
pembeli membayar dengan **tap kartu RFID**, saldo didebit dari **ledger append-only**,
stok berkurang otomatis, dan penjualan terposting ke Buku Kas SKOOLIA.

> **Status:** MVP fitur (PRD §4–§11) selesai. Sisa pekerjaan: **integrasi eksternal**
> (adapter REST sudah ada, menunggu kredensial tim SKOOLIA) & **hardening/operasional**.
> Indeks sisa pekerjaan: [#165](https://github.com/Zenixu/kantin-be/issues/165).
>
> **Stack:** Java 25 · Spring Boot 4 (webmvc) · PostgreSQL + Flyway · Redis 7 ·
> JWT RS256 · MinIO/S3 · Apache POI (XLSX) · Maven · Docker/Jenkins.
>
> Kontrak API lengkap: [`architecture/API-ENDPOINTS.md`](architecture/API-ENDPOINTS.md)
> (dijaga otomatis oleh `ApiEndpointsDocTest`).

---

## 📚 Mulai dari Sini

| Dokumen | Untuk apa |
|---|---|
| [`architecture/README.md`](architecture/README.md) | **Pusat dokumentasi teknis** — baca berurutan |
| [`architecture/AGENTS.md`](architecture/AGENTS.md) | **Kontrak kerja** — aturan emas, stack, DoD |
| [`architecture/ONBOARDING.md`](architecture/ONBOARDING.md) | Anggota baru: alat, setup, hari pertama |
| [`architecture/2026-10-01-prd-kantin-skoolia.md`](architecture/2026-10-01-prd-kantin-skoolia.md) | PRD (sumber kebenaran kebutuhan) |
| [`README-DEV.md`](README-DEV.md) | Panduan dev Database & Integration (PostgreSQL, Redis, MinIO) |
| [`GIT-WORKFLOW.md`](GIT-WORKFLOW.md) | Branching, PR, review, konflik |

---

## 🚀 Menjalankan Cepat (Lokal)

```bash
docker compose up -d                                  # PostgreSQL 18 + Redis 7
docker compose ps                                     # pastikan keduanya "healthy"

cp src/main/resources/application-local.properties.example \
   src/main/resources/application-local.properties     # isi nilai <GANTI_...>

./mvnw spring-boot:run                                # profil default = local, port 8082
```

- PostgreSQL → `localhost:5432` (db `kantin_db`, user `kantin`) · Redis → `localhost:6379`.
- Port aplikasi **8082** (admin-be 8081, agar tidak bentrok).
- Alternatif berbasis env var (container): `cp .env.example .env` — daftar lengkap di file itu.
- **Git Bash (MSYS) di Windows:** `./mvnw` bisa gagal (`classworlds.launcher.Launcher`) karena
  MSYS mengubah path — pakai `./mvnw.cmd` (PowerShell/CMD) atau helper lokal `./mvn-run.sh`
  (lihat [`README-DEV.md`](README-DEV.md) §7).
- **Demo kasir E2E tanpa data SKOOLIA:** profil `demo` + seed demo
  ([`docs/demo-e2e-kasir.md`](docs/demo-e2e-kasir.md)). Profil `demo` **hanya lokal**.

---

## 🧪 Test & Build

```bash
./mvnw test              # unit test
./mvnw verify            # + integration test (Testcontainers) + laporan JaCoCo
./mvnw clean package     # build jar
```

- Integration test `*IT` memakai **Testcontainers** (PostgreSQL + Redis nyata) → butuh **Docker**;
  dijalankan lewat `verify`/failsafe.
- **Cakupan (JaCoCo):** `target/site/jacoco/`; angka & skenario UAT/DoD rilis:
  [`docs/uat-dan-dod-rilis.md`](docs/uat-dan-dod-rilis.md).
- **CI:** GitHub Actions per-PR ([`.github/workflows/ci.yml`](.github/workflows/ci.yml),
  `./mvnw verify`) + Jenkinsfile stage `Test` sebelum build image. Wajib hijau sebelum merge.

---

## 🗂️ Struktur Proyek

```
src/main/java/com/asqi/scholia_kantin_be/
├── controller/   REST controller (Auth, Kasir, Saldo, Stok, Katalog, KartuTamu,
│                 KontrolKartu, Laporan, KonfigurasiKantin, PengaturanKantin, Storage, Webhook, Internal)
├── service/      logika domain (kasir, saldo, stok, katalog, kartu, laporan, integrasi)
├── repository/   Spring Data JPA        ├── model/    entitas JPA (ledger append-only)
├── config/       security (JWT), ratelimit, webhook, internal, notifikasi, aktivasi
├── component/    audit logger, exception, rate limit
└── scheduler/    auto-tutup sesi kasir, retry posting Buku Kas

src/main/resources/db/migration/   migrasi Flyway (V1 …)
architecture/                      dokumentasi teknis (PRD, ADR, konvensi)
docs/                              spesifikasi, plan, integrasi, SQL manual
```

---

## 🔐 Prinsip Inti (Non-Negotiable)

1. **Ledger append-only** — baris transaksi tidak pernah diubah/dihapus; koreksi = mutasi pembalik.
2. **Saldo & stok tak boleh minus** — dijaga di level DB (`FOR UPDATE`) + validasi.
3. **Idempotency** — tiap mutasi punya `idempotency_key`, **tenant-scoped**.
4. **Tenant scoping** — semua query ter-scope `sekolah_id`; data lintas-sekolah → **404**.
5. **RBAC di backend** — setiap endpoint dijaga (`@PerluPeran`), bukan hanya di UI.
6. **Blokir kartu instan** — status blokir diperiksa ke server **tiap tap**; **DILARANG** di-cache.
7. **Tap ≤ 1 detik (p95)** — termasuk lookup kartu ke data SKOOLIA.
8. **Integrasi lewat `service/integrasi/`** — dilarang memanggil lintas-sistem dari controller.

---

## 🔌 Integrasi Eksternal

Semua panggilan lintas-sistem lewat `service/integrasi/`. **Adapter REST nyata sudah ada**
(diuji kontrak WireMock) tetapi **default nonaktif** (`kantin.integrasi.*.enabled=false`);
selama nonaktif, `*Fallback` yang dipakai. Di staging/produksi `kantin.integrasi.wajib-aktif=true`
**menggagalkan start** bila masih ada `*Fallback`. Untuk demo tanpa kredensial, profil `demo`
memakai adapter `dev/*Demo`.

| Target | Kegunaan |
|---|---|
| `admin-be` | Buku Kas, lookup kartu/siswa |
| `mobile-be` | Notifikasi, identitas ortu |
| `callback-be` | Callback top-up online (webhook HMAC) |
| `internal-be` | Aktivasi modul & fee platform |

Kontrak & pertanyaan yang memblokir: [`architecture/INTEGRATIONS.md`](architecture/INTEGRATIONS.md)
· [`architecture/OPEN-QUESTIONS.md`](architecture/OPEN-QUESTIONS.md).

---

## 📊 Observability & Dokumentasi API

- Metrik Prometheus: `GET /actuator/prometheus` (Micrometer); SLO tap p95 ≤ 1 dtk diukur lewat
  timer `kantin_tap_duration_seconds` — [`docs/OBSERVABILITY.md`](docs/OBSERVABILITY.md).
- Uji beban (k6) untuk SLO tap: [`docs/uji-beban-tap.md`](docs/uji-beban-tap.md) (skrip di `load/`).
- Swagger UI: `GET /swagger-ui.html` · OpenAPI: `GET /v3/api-docs` (springdoc). Publik bila
  `KANTIN_OPENAPI_PUBLIC=true`; set `false` untuk menutup di balik JWT.

---

*Repo: `github.com/Zenixu/kantin-be` · Versi 0.0.1-SNAPSHOT*
