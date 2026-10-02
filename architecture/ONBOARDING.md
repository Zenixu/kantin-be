# 🚀 ONBOARDING.md — Panduan Anggota Baru `kantin-be`

> Baca ini dulu sampai selesai (≈30 menit), lalu ikuti checklist setup. Setelahnya Anda siap berkontribusi.

---

## 1. Selamat Datang

Anda bergabung mengerjakan **backend modul kantin cashless SKOOLIA**. Ini proyek berkelompok — kode Anda akan dibaca & dipakai orang lain. Karena itu, **dokumen ini dan `AGENTS.md` bukan formalitas.**

**Urutan baca wajib:**
1. `architecture/AGENTS.md` ← kontrak kerja
2. `../2026-10-01-prd-kantin-skoolia-non-teknis.md` ← pahami produknya dalam bahasa awam
3. `../2026-10-01-prd-kantin-skoolia.md` ← PRD teknis (fitur, §6–§11)
4. `architecture/INTEGRATIONS.md` ← cara nyambung ke SKOOLIA
5. `architecture/GLOSSARY.md` ← istilah
6. `architecture/WORKFLOW.md` + `CONVENTIONS.md` ← cara kerja & gaya kode

---

## 2. Prasyarat Alat

| Alat | Versi wajib | Catatan |
|---|---|---|
| **JDK** | **25 (Temurin 25+36 LTS)** | **WAJIB parity `admin-be` (`zulu-openjdk-alpine:25`). JDK 21 TIDAK cukup.** |
| **Maven** | *tidak perlu global* | Pakai **Maven Wrapper** `./mvnw` (Maven 3.9.12, `distributionType=only-script`) |
| Docker + Compose | terbaru (uji: Docker 29.8.0 / Compose 5.5.1) | PostgreSQL + Redis dev |
| PostgreSQL (client) | 18.x | opsional, untuk `psql` manual |
| Git | terbaru | akses SSH ke GitHub |
| Node + npm | 26.x / 11.x | hanya bila mengerjakan `kantin-fe` |
| k6 / JMeter | opsional | uji performa tap p95 < 1 dtk |

### ⚠️ Jebakan #1 — JDK 21 vs JDK 25

Banyak mesin punya **JDK 21 sebagai default**. `admin-be` memakai **Java 25**; kalau `kantin-be` dibangun dengan 21, build akan **gagal** (`java.version=25` di `pom.xml`).

**Cek versi yang benar-benar aktif:**
```bash
java -version        # HARUS 25, bukan 21
echo $JAVA_HOME      # HARUS menunjuk ke JDK 25
```

**Cara memasang & mengaktifkan JDK 25** — lihat **[`ENVIRONMENT.md`](./ENVIRONMENT.md)** (berisi langkah lengkap per-OS: SDKMAN, apt, brew, + fix untuk shell `fish`/`bash`/`zsh`).

### ⚠️ Jebakan #2 — Maven tidak ada global

Tidak perlu install Maven. Semua perintah pakai **wrapper** — file `mvnw` + `.mvn/wrapper/maven-wrapper.properties` **wajib ada** di repo (disalin dari `admin-be`). Wrapper akan mengunduh Maven 3.9.12 sendiri ke `~/.m2/wrapper/`.

Cek cepat (harus menampilkan **Java version: 25**):
```bash
./mvnw --version
```

---

## 3. Checklist Setup (hari pertama)

- [ ] **1. Clone repo**
  ```bash
  cd ~/Projects/PKL/Kantin-Skoolia
  git clone git@github.com:Zenixu/kantin-be.git
  cd kantin-be
  ```
- [ ] **2. Baca dokumen §1.**
- [ ] **3. Baca PRD** (minimal non-teknis + §6/§11 teknis).
- [ ] **4. Setup infra lokal**
  ```bash
  docker compose up -d        # PostgreSQL + Redis
  ```
- [ ] **5. Salin config**
  ```bash
  cp src/main/resources/application-local.properties.example \
     src/main/resources/application-local.properties
  # isi kredensial lokal (JANGAN commit)
  ```
- [ ] **6. Jalankan migrasi**
  ```bash
  ./mvnw flyway:info
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
  ```
- [ ] **7. Verifikasi** `GET /actuator/health` → `UP`.
- [ ] **8. Baca `OPEN-QUESTIONS.md`** — cari 🔴 yang belum terjawab; jangan kerjakan modul terblokir.
- [ ] **9. Ambil satu task** dari papan (GitHub Projects/Issues), buat branch, kerjakan.
- [ ] **10. Buka PR pertama**, minta review.

> ⚠️ Pada saat onboarding, scaffold mungkin belum ada (repo masih kosong). Jika demikian, tanyakan ke koordinator tim: mulai dari [`MODULE-MAP.md`](./MODULE-MAP.md) bagian "Fase Fondasi".

---

## 4. Peta "Saya Mau Mulai dari Mana"

| Jika Anda... | Mulai dari |
|---|---|
| Baru, tak paham produk | Tiga dokumen di §1 |
| Mau paham arus data | `diagrams/tap-flow.md`, `INTEGRATIONS.md` |
| BE — fondasi | Setup auth JWT + tenant + RBAC (lihat `MODULE-MAP.md`) |
| BE — ledger | `service/kasir`, tabel `saldo_ledger`, `mutasi_stok` |
| BE — katalog/stok | `service/katalog`, `service/stok` |
| BE — saldo/kartu | `service/saldo`, `service/kartu` |
| FE | `kantin-fe` (repo terpisah) — layar kasir & back office |
| Bingung keputusan | `adr/` + `OPEN-QUESTIONS.md` |

---

## 5. Kontak & Koordinasi

- **Koordinasi lintas-tim (admin-be/mobile-be/internal-be):** lihat `OPEN-QUESTIONS.md` §A — tiap Q punya "untuk siapa".
- **Keputusan arsitektur:** ajukan lewat ADR, diskusikan di PR.
- **Standup harian & papan tugas:** _(isi sesuai kebiasaan tim)_.

---

## 6. Etiket Tim

- Ajukan pertanyaan di tempat yang tepat (issue/PR, bukan chat pribadi) agar semua belajar.
- Dokumentasikan keputusan, jangan simpan di kepala.
- Review PR orang lain dengan hormat & spesifik.
- **Blocker itu normal** — angkat lebih awal (lihat `OPEN-QUESTIONS.md`).
