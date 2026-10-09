# 🍽️ kantin-be — Modul Kantin Cashless SKOOLIA (Backend)

Backend modul **kantin sekolah cashless** untuk platform SKOOLIA. 100% non-tunai:
pembeli membayar dengan **tap kartu RFID**, saldo didebit dari ledger append-only,
stok berkurang otomatis, dan penjualan terposting ke Buku Kas SKOOLIA.

> **Status:** MVP fitur (PRD §4–§11) ≈ **90%** selesai · 88 endpoint · 215 unit test hijau ·
> 22 migrasi Flyway. Sisa pekerjaan: integrasi eksternal (masih `*Fallback`) & hardening
> — lihat [Peta Sisa Pekerjaan](#-peta-sisa-pekerjaan).

---

## 📚 Mulai dari Sini

| Dokumen | Untuk apa |
|---|---|
| [`architecture/README.md`](architecture/README.md) | **Pusat dokumentasi teknis** — baca dalam urutan bernomor |
| [`architecture/AGENTS.md`](architecture/AGENTS.md) | **Kontrak kerja** — aturan emas, stack, DoD, larangan |
| [`architecture/ONBOARDING.md`](architecture/ONBOARDING.md) | Anggota baru: alat, setup, checklist hari pertama |
| [`architecture/2026-10-01-prd-kantin-skoolia.md`](architecture/2026-10-01-prd-kantin-skoolia.md) | PRD (sumber kebenaran kebutuhan produk) |
| [`architecture/API-ENDPOINTS.md`](architecture/API-ENDPOINTS.md) | Rujukan kontrak API |
| [`README-DEV.md`](README-DEV.md) | Panduan dev Database & Integration (PostgreSQL, Redis, MinIO) |
| [`GIT-WORKFLOW.md`](GIT-WORKFLOW.md) | Branching, PR, review, penanganan konflik |

---

## 🧱 Stack

| Lapisan | Teknologi |
|---|---|
| Bahasa / Runtime | **Java 25** (`.sdkmanrc`: `java=25-tem`) |
| Framework | **Spring Boot 4.0.2** (webmvc) |
| Database | **PostgreSQL** + **Flyway** (migrasi; `ddl-auto=none`) |
| Persistensi | Spring Data **JPA** + **JDBC** |
| Cache / Rate limit | **Redis 7** (blacklist token, rate limit, cache) |
| Auth | **JWT RS256** (tanpa login sendiri — token dari SKOOLIA) |
| Objek/berkas | **MinIO / S3** (`S3Storage`) |
| Ekspor | **Apache POI** (XLSX) |
| Build | **Maven** (`./mvnw`) |
| Infra & deploy | **Docker** + **Jenkins** → k3s |

---

## 🚀 Menjalankan Cepat (Lokal)

### 1. Nyalakan infrastruktur (PostgreSQL + Redis)

```bash
docker compose up -d
docker compose ps          # pastikan keduanya "healthy"
```

- PostgreSQL 18 → `localhost:5432`, db `kantin_db`, user `kantin`
- Redis 7 → `localhost:6379`

### 2. Siapkan konfigurasi lokal

```bash
cp src/main/resources/application-local.properties.example \
   src/main/resources/application-local.properties
# isi nilai <GANTI_...> ; file ini sudah di-.gitignore — JANGAN commit kredensial
```

### 3. Jalankan aplikasi

```bash
./mvnw spring-boot:run        # profil default = local, port 8082
```

> Port aplikasi **8082** (admin-be 8081, agar tidak bentrok).

### ⚠️ Git Bash (MSYS) di Windows

`./mvnw` (versi shell) bisa gagal `ClassNotFoundException: ...classworlds.launcher.Launcher`
karena MSYS mengubah path. Pakai salah satu:

```bash
./mvnw.cmd clean compile      # Opsi A — PowerShell / CMD
./mvn-run.sh clean test       # Opsi B — wrapper lokal untuk Git Bash
```

---

## 🧪 Test & Build

```bash
./mvn-run.sh -o -q test       # 215 unit test (38 file *Test)
./mvn-run.sh -o -q verify     # + 29 integration test (*IT, Testcontainers)
./mvn-run.sh clean package    # build jar
```

- Unit test: **215 lolos, 0 gagal** (`target/surefire-reports/`).
- Integration test: `*IT` memakai **Testcontainers** (PostgreSQL + Redis nyata).
- `TODO`/`FIXME` di `src/main`: **0**.

### CI (otomatis per-PR)

- **GitHub Actions** — [`.github/workflows/ci.yml`](.github/workflows/ci.yml):
  tiap push/PR ke `main`/`develop` menjalankan `./mvnw verify` (JDK 25 temurin,
  unit + integration test) dan mengunggah laporan test. **Wajib hijau** sebelum merge
  (WORKFLOW.md §4).
- **Jenkinsfile** — stage `Test` (`./mvnw verify`) berjalan **sebelum** build image,
  jadi pipeline gagal bila ada test merah.

### Observability

- Metrik Prometheus di `GET /actuator/prometheus` (Micrometer). SLO tap p95 ≤ 1 dtk
  (PRD §12) diukur lewat timer `kantin_tap_duration_seconds`. Detail & contoh query/alert:
  [`docs/OBSERVABILITY.md`](docs/OBSERVABILITY.md).

---

## 🗂️ Struktur Proyek

```
src/main/java/com/asqi/scholia_kantin_be/
├── controller/      13 REST controller (Auth, Kasir, Saldo, Stok, Katalog,
│                    KartuTamu, KontrolKartu, Laporan, KonfigurasiKantin,
│                    PengaturanKantin, Storage, Webhook, Internal)
├── service/         logika domain (kasir, saldo, stok, katalog, kartu,
│                    laporan, konfigurasi, storage, webhook, integrasi)
├── repository/      Spring Data JPA
├── model/           entitas JPA (ledger append-only)
├── dto/ enums/      kontrak data & enumerasi domain
├── config/          security (JWT), ratelimit, webhook, internal, notifikasi, aktivasi
├── component/       audit logger, exception, rate limit
├── scheduler/       auto-tutup sesi kasir, retry posting Buku Kas
└── security/        TenantContext, blacklist token

src/main/resources/db/migration/   V1 … V22 (Flyway)
architecture/                      dokumentasi teknis (PRD, ADR, konvensi, dsb.)
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

Semua panggilan lintas-sistem lewat `service/integrasi/`. Status saat ini:

| Target | Kegunaan | Status |
|---|---|---|
| `admin-be` | Buku Kas, lookup kartu/siswa | ⚠️ masih `*Fallback` (fail-open) |
| `mobile-be` | Notifikasi, identitas ortu | ⚠️ masih `*Fallback` |
| `callback-be` | Callback top-up online | ✅ webhook HMAC terpasang |
| `internal-be` | Aktivasi modul & fee platform | ⚠️ masih `*Fallback` |

Rincian kontrak & pertanyaan yang memblokir: [`architecture/INTEGRATIONS.md`](architecture/INTEGRATIONS.md)
dan [`architecture/OPEN-QUESTIONS.md`](architecture/OPEN-QUESTIONS.md).

---

## 🌿 Branch & Alur Kerja

- Branch tetap: **`main`** (rilis) & **`develop`** (integrasi).
- **Tidak boleh push langsung** ke `main`/`develop` — selalu via **PR + review**
  ([`GIT-WORKFLOW.md`](GIT-WORKFLOW.md), [`architecture/WORKFLOW.md`](architecture/WORKFLOW.md)).
- Branch fitur: `feat/<nama>` · `fix/<nama>` — hapus setelah PR di-merge.
- Format commit: `type(scope): deskripsi` — `feat`, `fix`, `docs`, `refactor`, `test`, `chore`.

```bash
git checkout develop && git pull origin develop
git checkout -b feat/nama-fitur
# ... kerja ...
git commit -m "feat(kasir): tambah validasi urutan 6 tahap"
git push -u origin feat/nama-fitur   # buka PR ke develop
```

---

## 🧭 Peta Sisa Pekerjaan

Sisa pekerjaan dilacak sebagai **issue GitHub** di
[`Zenixu/kantin-be`](https://github.com/Zenixu/kantin-be/issues) (label `tracking`).

Yang menahan sisa progres:

1. **6 integrasi eksternal masih `*Fallback`** — menunggu tim admin-be / mobile-be /
   callback-be / internal-be (OPEN-QUESTIONS Q4–Q8) & public key RS256 produksi (Q1/Q2).
2. **Hardening & operasional** — CI (test otomatis), proteksi branch, uji beban SLO tap p95,
   metrics/observability.
3. **Kelengkapan laporan** — laporan "Setoran kas TU" & ekspor "Per siswa" (PRD §9.5).

---

## 👥 Tim

Dikelola tim PKL SKOOLIA (pembagian jobdesk: *Security & Controller*, *Service & Repository*,
*Database & Integration*). Lihat [`architecture/AGENTS.md`](architecture/AGENTS.md) & issue
berlabel `jobdesk-*`.

---

*Repo: `github.com/Zenixu/kantin-be` · Versi 0.0.1-SNAPSHOT*
