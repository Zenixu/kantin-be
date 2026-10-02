# AGENTS.md — Panduan Kontribusi & Agen AI untuk `kantin-be`

> **Dokumen ini adalah kontrak kerja tim.** Setiap kontributor (manusia **maupun agen AI**) WAJIB membaca dokumen ini sebelum menulis kode. Jika instruksi di sini bertentangan dengan kebiasaan pribadi Anda, instruksi di sini yang menang.

| | |
|---|---|
| **Proyek** | Modul Kantin Cashless SKOOLIA — backend (`kantin-be`) |
| **Repo** | `git@github.com:Zenixu/kantin-be.git` |
| **Dokumen acuan produk** | `../2026-10-01-prd-kantin-skoolia.md` (PRD teknis v4) |
| **Dokumen acuan proses** | `../2026-10-01-prd-kantin-skoolia-non-teknis.md` (PRD non-teknis) |
| **Referensi implementasi** | Repo SKOOLIA yang di-clone: `admin-be`, `admin-fe` (lihat §N) |
| **Versi dokumen** | 1.0 |

---

## 1. Ringkas Produk (30 detik)

Kantin sekolah **100% tanpa uang tunai**. Siswa/guru/staf/tamu bayar dengan **tap kartu RFID**. Saldo siswa = **dana titipan** (kewajiban sekolah), baru jadi pendapatan saat dibelanjakan.

- **Backend ini** memegang: ledger saldo, transaksi, menu, stok, HPP, sesi kasir, limit/blokir, Kartu Tamu, laporan kantin.
- **SKOOLIA** (`admin-be`, `mobile-be`, `internal-be`, `callback-be`) = sumber kebenaran identitas, Buku Kas, notifikasi, aktivasi modul.

Baca PRD lengkap sebelum menyentuh fitur apa pun. **PRD > opini pribadi.**

---

## 2. Tech Stack (WAJIB — jangan menyimpang tanpa ADR)

| Lapisan | Teknologi | Catatan |
|---|---|---|
| Bahasa | **Java 25** | parity dengan `admin-be` |
| Framework | **Spring Boot 4.0.2 (webmvc)** | **bukan** reactive/WebFlux |
| Build | **Maven** via `./mvnw` | jangan pakai `mvn` global |
| DB | **PostgreSQL** | DB terpisah dari admin-be |
| Migrasi | **Flyway** | semua perubahan skema = file migrasi baru |
| ORM | **Spring Data JPA + JDBC** | ledger pakai JDBC + locking |
| Cache | **Redis** | ⚠️ **DILARANG** cache status blokir kartu |
| Auth | **JWT RS256** (`jjwt` 0.12.6) | verifikasi public key admin-be & mobile-be |
| Storage | **MinIO** | nota/stok |
| Export | **Apache POI** (Excel) | semua laporan harus bisa ekspor |
| Test | **JUnit + Testcontainers (PostgreSQL)** | wajib untuk ledger/race-condition |
| Container | **Docker multi-stage** + Jenkins | ikuti pola `admin-be` |

> 🖥️ **Versi teruji toolchain + cara memasang JDK 25 + troubleshooting:** lihat **[`ENVIRONMENT.md`](./ENVIRONMENT.md)**.
> Prinsip: **JDK 25** (❌ bukan 21/26) · **`./mvnw`** (Maven 3.9.12, ❌ jangan `mvn` global) · **`TZ=Asia/Jakarta`**.
> Sebelum PR, `./mvnw --version` **harus** menampilkan `Java version: 25`.

---

## 3. Aturan Emas (Non-Negotiable)

Diangkat dari **PRD teknis §11** — pelanggaran = PR ditolak.

1. **Ledger append-only** untuk saldo **dan** stok. Tidak ada `UPDATE`/`DELETE` pada baris mutasi. Saldo/stok = turunan yang bisa dihitung ulang.
2. **Atomik & bebas race condition.** Debit saldo + kurangi stok + catat transaksi = satu transaksi DB dengan locking tepat. **Saldo/stok minus harus mustahil.**
3. **Idempotency** pada: transaksi kasir (key dari klien), callback PG, posting Buku Kas.
4. **Tenant scoping** di setiap query. Data sekolah lain → **404, bukan 403**.
5. **RBAC dicek di backend** tiap endpoint. Menyembunyikan tombol di FE bukan pengaman.
6. **Nominal uang = integer rupiah (`BIGINT`)**, bukan float/double. Qty = integer.
7. **Audit log** untuk: void, koreksi, refund/pindah saldo, ubah harga, barang masuk & pembalik, opname, ubah limit/blokir (siapa, kapan, lama→baru).
8. **Performa tap ≤ 1 detik (p95).**
9. **Zona waktu sekolah** untuk reset limit, tutup kasir otomatis, laporan.
10. **JWT RS256 wajib** di production.
11. **Blokir instan** — status diblokir diperiksa di server **setiap tap**, tanpa cache ber-TTL.

---

## 4. Struktur Folder yang Disepakati

```
kantin-be/
├── architecture/          # ← dokumen ini & semua keputusan arsitektur
├── docs/                  # spesifikasi fitur, plan, sqL manual (non-Flyway)
├── pom.xml  mvnw  .mvn/
├── Dockerfile  Dockerfile-dev  Jenkinsfile  docker-compose.yml
└── src/
    ├── main/java/com/asqi/scholia_kantin_be/
    │   ├── config/security/       # decoder JWT, filter, WebSecurityConfig
    │   ├── component/             # GlobalExceptionHandler, exception/, logging/
    │   ├── controller/            # tipis: hanya validasi request & delegasi
    │   ├── dto/                   # objek transfer antar layer
    │   ├── enums/
    │   ├── helper/                # Constants (idGenerator), util
    │   ├── model/                 # entity JPA (pemilik data kantin)
    │   ├── payload/request|response/
    │   ├── repository/
    │   ├── security/              # TenantContext, SekolahGuard, RbacChecker
    │   └── service/
    │       ├── kasir/  stok/  saldo/  kartu/  katalog/  laporan/
    │       ├── integrasi/         # adaptor ke SKOOLIA (BukuKasClient, dsb.)
    │       └── audit/
    ├── main/resources/
    │   ├── application*.properties
    │   └── db/migration/          # Flyway V{n}__{Nama}.sql
    └── test/java/...              # unit + Testcontainers integration
```

**Pola layer:** `controller → service → repository → model`. Controller **tidak** boleh berisi logika bisnis.

---

## 5. Konvensi Kode

Detail lengkap di [`CONVENTIONS.md`](./CONVENTIONS.md). Ringkasan:

- **Penamaan:** entity = PascalCase tunggal (`Transaksi`, `KartuTamu`). Tabel = `snake_case` plural (`transaksi`, `kartu_tamu`). Kolom = `snake_case`.
- **Response:** gunakan `CommonResponse.data(...)` / `Response<T>` — **samakan** dengan `admin-be`.
- **Exception:** pakai kelas di `component/exception/` (`ConflictException`, `NotFoundEntity`, dst). Jangan lempar `RuntimeException` mentah.
- **ID:** gunakan `Constants.idGenerator()` (pola `admin-be`) — jangan autoincrement untuk entitas bisnis yang perlu ID global.
- **Uang:** selalu `Long` rupiah integer. Format ke tampilan hanya di FE.
- **Waktu:** `LocalDateTime` disimpan dalam zona sekolah; gunakan jam server terkonfigurasi.
- **Bahasa komentar:** Bahasa Indonesia (konsisten dengan `admin-be`).

---

## 6. Alur Kerja Git untuk Tim

Detail di [`WORKFLOW.md`](./WORKFLOW.md). Ringkasan:

```
main            ← production-ready, dilindungi (protected)
 └── develop    ← integrasi harian
      ├── feat/<modul>-<deskripsi>       mis. feat/kasir-tap-validasi
      ├── fix/<modul>-<deskripsi>        mis. fix/ledger-race-debit
      └── chore/<deskripsi>
```

- **Satu fitur = satu branch = satu PR.**
- PR **wajib** lewat review minimal 1 orang lain + lulus CI.
- Commit message: `type(scope): deskripsi` (Conventional Commits).
- **Dilarang** push langsung ke `main`/`develop`.

---

## 7. Pembagian Peran Tim (usulan — sesuaikan saat kick-off)

| Peran | Fokus | Modul utama |
|---|---|---|
| **BE-1 · Fondasi** | Auth JWT, tenant scoping, RBAC, infra | `config/security`, `security`, `component` |
| **BE-2 · Ledger & Kasir** | Ledger append-only, tap, void, sesi kasir | `service/kasir`, `model/*Ledger*` |
| **BE-3 · Katalog & Stok** | Menu, barang masuk, opname, HPP | `service/katalog`, `service/stok` |
| **BE-4 · Saldo & Kartu** | Top-up tunai, Kartu Tamu, refund, limit/blokir | `service/saldo`, `service/kartu` |
| **BE-5 · Integrasi & Laporan** | BukuKasClient, notifikasi, laporan Excel | `service/integrasi`, `service/laporan` |
| **FE-1 · Layar Kasir** | UI kasir, RFID bridge, beep | `kantin-fe/routes/kasir` |
| **FE-2 · Back Office** | CRUD menu/stok/saldo/kartu/laporan | `kantin-fe/routes/backoffice` |

Setiap peran menulis **ADR** untuk keputusan besarnya. Lihat [`adr/`](./adr/).

---

## 8. Titik Integrasi dengan SKOOLIA (RINGKASAN KRITIS)

Detail lengkap di [`INTEGRATIONS.md`](./INTEGRATIONS.md).

### 8.1 Buku Kas (`admin-be`)
- Panggil `BukuKasService.catatTransaksi(...)`. Enum: `TipeTransaksi{MASUK,KELUAR}`, `MetodePembayaran{TUNAI,NON_TUNAI,DANA_BOS}`.
- Penjualan → `MASUK`/`NON_TUNAI`/kategori `"Pendapatan Kantin"`.
- Belanja stok → `KELUAR`/kategori `"Belanja Stok Kantin"`.
- ⚠️ **`refModul` baru (`KANTIN_*`) belum dikenal `migrateBukuKas()` admin-be** → koordinasikan sebelum memakai. Sementara: set `refModul=null` agar di-skip migrasi.
- **Idempotency Buku Kas TIDAK ada di admin-be** → kantin-be wajib jaga sendiri.

### 8.2 Lookup Kartu RFID (`admin-be`)
- Sumber: `siswa.rfid_uid VARCHAR(64)` (unique partial index).
- `SiswaRepository.findByRfidUid(uid)` — ⚠️ **tidak filter sekolah/status**. kantin-be **wajib** tambah cek tenant + siswa aktif.
- Anti-tabrakan: UID Kartu Tamu tidak boleh = `rfid_uid` siswa (dan sebaliknya) → di sisi kantin-be.

### 8.3 Autentikasi
- `kantin-be` **tanpa login sendiri**. Verifikasi JWT RS256 dari `admin-be` (staf) & `mobile-be` (ortu).

### 8.4 Masalah Terbuka → lihat [`OPEN-QUESTIONS.md`](./OPEN-QUESTIONS.md)

---

## 9. Definition of Done (DoD) untuk Setiap Fitur

Sebuah tugas dianggap **selesai** hanya jika:

- [ ] Implementasi sesuai PRD (sebutkan nomor § di deskripsi PR).
- [ ] Ada **test** (unit + integration bila menyentuh ledger/saldo/stok).
- [ ] Tenant scoping & RBAC diuji (uji akses sekolah lain → 404).
- [ ] Audit log ditulis bila termasuk daftar §3 poin 7.
- [ ] Tidak ada hard delete pada data bisnis.
- [ ] File migrasi Flyway baru sudah dijalankan & lolos `flyway:validate`.
- [ ] Dokumentasi diperbarui (`architecture/` bila mengubah keputusan).
- [ ] PR lulus review + CI hijau.

---

## 10. Yang DILARANG (Daftar Merah)

- ❌ Menyimpan status kartu/saldo di cache FE atau di client.
- ❌ Hard delete data transaksi/saldo/stok/menu.
- ❌ `UPDATE`/`DELETE` pada baris ledger.
- ❌ Menyimpan uang sebagai float/double.
- ❌ Menaruh logika bisnis di controller.
- ❌ Menjawab 403 untuk data sekolah lain (harus 404).
- ❌ Menambah `mvn`/dependency tanpa ADR bila menyimpang dari §2.
- ❌ Commit secret / `.properties` berisi kredensial.
- ❌ Push langsung ke `main`/`develop`.

---

## 11. Referensi Cepat

| Butuh | Lihat |
|---|---|
| Konvensi kode lengkap | [`CONVENTIONS.md`](./CONVENTIONS.md) |
| Alur Git & PR | [`WORKFLOW.md`](./WORKFLOW.md) |
| Detail integrasi SKOOLIA | [`INTEGRATIONS.md`](./INTEGRATIONS.md) |
| Keputusan arsitektur | [`adr/`](./adr/) |
| Pertanyaan terbuka | [`OPEN-QUESTIONS.md`](./OPEN-QUESTIONS.md) |
| Onboarding anggota baru | [`ONBOARDING.md`](./ONBOARDING.md) |
| Glosarium istilah | [`GLOSSARY.md`](./GLOSSARY.md) |
| Peta modul → file | [`MODULE-MAP.md`](./MODULE-MAP.md) |
| Alur tap kasir | [`diagrams/tap-flow.md`](./diagrams/tap-flow.md) |
