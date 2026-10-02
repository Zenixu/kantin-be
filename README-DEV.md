# 🗄️ Panduan Dev — Database & Integration (kantin-be)

Bagian ini milik **Database & Integration** dalam pembagian kerja tim.
Mencakup: PostgreSQL, Entity & Relationship, Redis, MinIO/S3, Midtrans, dan
integrasi REST ke SKOOLIA (admin-be / mobile-be).

> **Status Fase 4 (Ledger): SELESAI & TERVERIFIKASI.**
> Migrasi `V2`–`V4` + 8 tabel domain. Lihat detail di
> [`docs/spesifikasi-fase4-ledger.md`](docs/spesifikasi-fase4-ledger.md).

---

## 1. Menyalakan Infrastruktur Dev

```bash
docker compose up -d
docker compose ps          # pastikan postgres & redis "healthy"
```

Yang jalan: **PostgreSQL 18** (`localhost:5432`, db `kantin_db`, user `kantin`)
dan **Redis 7** (`localhost:6379`).

```bash
docker compose down        # stop (data tetap)
docker compose down -v     # stop + hapus data
```

---

## 2. Konfigurasi Lokal

```bash
cp src/main/resources/application-local.properties.example \
   src/main/resources/application-local.properties
```

Lalu isi nilai `<GANTI_...>` di `application-local.properties`.
File ini **sudah di-`.gitignore`** — jangan pernah commit kredensial.

Profil default sudah `local` (`spring.profiles.active`), jadi cukup:

```bash
./mvnw spring-boot:run
```

Port aplikasi: **8082** (admin-be 8081, agar tidak bentrok).

---

## 3. Skema Database — Aturan Wajib

| Aturan | Keterangan |
|---|---|
| **Flyway, bukan Hibernate** | `spring.jpa.hibernate.ddl-auto=none`. Semua perubahan skema lewat file migrasi baru di `src/main/resources/db/migration/` |
| **Nama file** | `V<n>__<Deskripsi>.sql` (contoh: `V2__TabelLedger.sql`) |
| **Jangan edit migrasi lama** | Migrasi yang sudah di-commit bersifat *immutable* — buat file baru |
| **DB terpisah** | Kantin punya DB sendiri, **tidak** menyatu dengan admin-be (ADR-0001) |

Saat ini baru ada `V1__Baseline.sql` (ext `pgcrypto`). Tabel domain dimulai di **Fase 4** (lihat `architecture/MODULE-MAP.md`).

---

## 4. Redis — Satu Larangan Keras

Redis dipakai untuk: cache umum, blacklist token, rate limit.

> ⛔ **DILARANG** meng-cache **status blokir kartu** (PRD §11.11, AGENTS.md §10).
> Blokir kartu wajib diperiksa langsung ke server pada **setiap tap**.
> Cache status blokir = siswa bisa tetap belanja walau kartunya sudah diblokir.

---

## 5. MinIO / S3

Dipakai untuk nota barang masuk & foto stok. Helper: `helper/S3Storage.java`.

MinIO lokal:

```bash
docker run -d --name kantin-minio -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio server /data --console-address ":9001"
```

Buat bucket `kantin` lewat console `http://localhost:9001`, lalu isi
`minio.access-key` / `minio.secret-key` di config lokal.

---

## 6. Integrasi SKOOLIA (REST internal)

Aturan: **semua panggilan lintas-sistem wajib lewat `service/integrasi/`** —
tidak boleh dipanggil langsung dari controller atau service domain
(AGENTS.md §6, CONVENTIONS.md).

| Target | Kegunaan | Status |
|---|---|---|
| `admin-be` | Buku Kas (posting penjualan/belanja), data siswa | ⛔ tergantung Q1, Q3 |
| `mobile-be` | Identitas ortu, push notification | ⛔ tergantung Q2, Q5 |
| callback-be | Callback top-up online | ⛔ tergantung Q4 |

### ⚠️ Dua temuan penting (hasil verifikasi ke repo admin-be)

**1. Klaim JWT ≠ seperti tertulis di `INTEGRATIONS.md`.**
Token yang diterbitkan admin-be **hanya** berisi `sub` (username), `typ`, `jti`,
`iat`, `exp` — **tidak ada** `sekolah_id` maupun `role`.
➡️ kantin-be belum bisa mengambil tenant/role dari JWT. Perlu endpoint API
internal **atau** minta admin-be menambah klaim. Ini memperkuat urgensi **Q1 & Q7**.

**2. Idempotency Buku Kas.**
`BukuKasService.catatTransaksi()` memang selalu `save` tanpa cek duplikat,
**tetapi** `BukuKasSekolahRepository` sudah punya
`existsByReferensiIdAndReferensiModul(...)`. Jadi kantin-be tetap harus menjaga
idempotency sendiri, dan bisa memakai pola pengecekan itu tanpa menunggu
perubahan di admin-be.

---

## 7. Membangun & Menjalankan

```bash
./mvnw clean package          # build + test
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

Docker:

```bash
docker build -t kantin-be:local .
```

### Catatan Git Bash (MSYS) di Windows

Wrapper `./mvnw` versi shell bisa gagal dengan
`ClassNotFoundException: org.codehaus.plexus.classworlds.launcher.Launcher`
karena MSYS mengubah path. Pakai salah satu:

```bash
# Opsi A — PowerShell / CMD
.\mvnw.cmd clean compile

# Opsi B — Git Bash: jalankan Maven langsung dengan path Windows
MVN_DIR="C:/Users/<user>/.m2/wrapper/dists/apache-maven-3.9.12/<hash>"
PROJ="D:/path/ke/kantin-be"
java -classpath "$MVN_DIR/boot/plexus-classworlds-2.9.0.jar" \
  "-Dclassworlds.conf=$MVN_DIR/bin/m2.conf" \
  "-Dmaven.home=$MVN_DIR" \
  "-Dmaven.multiModuleProjectDirectory=$PROJ" \
  org.codehaus.plexus.classworlds.launcher.Launcher clean compile
```

---

## 8. Checklist Sebelum Push

- [ ] Tidak ada kredensial di file yang di-commit
- [ ] Perubahan skema = file migrasi baru (bukan edit file lama)
- [ ] Panggilan lintas-sistem lewat `service/integrasi/`
- [ ] Status blokir kartu **tidak** di-cache
- [ ] `./mvnw clean package` hijau
