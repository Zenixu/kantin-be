# 📐 CONVENTIONS.md — Konvensi Kode `kantin-be`

> Tujuan: kode dari 5+ orang + agen AI harus terlihat seperti ditulis satu orang. Ikuti `admin-be` sebagai referensi gaya.

---

## 1. Penamaan

| Elemen | Konvensi | Contoh |
|---|---|---|
| Package | `snake_case` lowercase | `com.asqi.scholia_kantin_be.service.kasir` |
| Class | `PascalCase` | `KasirService`, `KartuTamu`, `TransaksiRepository` |
| Entity | `PascalCase` **tunggal** | `Transaksi`, `Menu`, `SesiKasir` |
| Tabel DB | `snake_case` plural | `transaksi`, `menu`, `sesi_kasir`, `kartu_tamu` |
| Kolom DB | `snake_case` | `sekolah_id`, `hpp_snapshot`, `created_at` |
| Method | `camelCase` kata kerja | `catatTransaksi()`, `hitungHppBaru()` |
| Konstanta | `UPPER_SNAKE_CASE` | `KATEGORI_PENDAPATAN_KANTIN` |
| Enum | `PascalCase` + nilai `UPPER_SNAKE` | `AlasanPenyesuaian.RUSAK` |
| File migrasi | `V{n}__{PascalCase}.sql` | `V3__CreateLedgerSaldo.sql` |

**Istilah domain (konsisten, jangan diterjemahkan):**
`Saldo`, `Mutasi`, `Void`, `Hpp`, `Opname`, `KartuTamu`, `SesiKasir`, `TitikKasir`, `LimitHarian`, `BlokirItem`.

---

## 2. Struktur Layer

```
controller  →  service  →  repository  →  model
                  │
                  └→ service/integrasi/*Client  (panggilan ke SKOOLIA)
```

**Aturan:**
- Controller **tipis**: `@RequestBody` → panggil service → bungkus `CommonResponse`. Tanpa logika bisnis.
- Service = tempat semua aturan bisnis & transaksi (`@Transactional`).
- Repository = akses data. Query kompleks pakai `@Query`. Ledger pakai locking.
- Entity **tidak** keluar dari layer service — selalu konversi ke DTO/payload.

---

## 3. Response & Exception

```java
// Sukses — samakan admin-be
return CommonResponse.data(dto);
return CommonResponse.data(dto, "Pesan sukses");

// Exception — pakai kelas yang sudah ada
throw new ConflictException("Saldo kurang");
throw new NotFoundEntity("Menu tidak ditemukan");
throw new ForbiddenException("...");
```
- Semua exception ditangani `GlobalExceptionHandler` (copy pola admin-be).
- **Dilarang** `throw new RuntimeException()` mentah.
- **Data sekolah lain → jawab 404** (`NotFoundEntity`), **bukan** 403.

---

## 4. Uang, Waktu, ID

| Hal | Aturan |
|---|---|
| **Uang** | `Long` rupiah integer, kolom `BIGINT`. **Tidak pernah** `float`/`double`/`BigDecimal` untuk data kanonik kantin. (Konversi `BigDecimal` hanya saat kirim ke Buku Kas.) |
| **Qty** | `Integer`. |
| **Waktu** | `LocalDateTime`, zona sekolah. Reset limit 00:00 waktu lokal sekolah. |
| **ID** | `Constants.idGenerator()` (pola admin-be) untuk entitas bisnis. `BIGSERIAL` hanya untuk tabel murni internal bila disetujui. |
| **Idempotency key** | Dibuat **klien kasir**, kolom `idempotency_key` UNIQUE. |

---

## 5. Ledger (saldo & stok)

- Tabel ledger **hanya INSERT**. Repository ledger **tidak punya** method `delete`/`update`.
- Saldo/stok berjalan = `SELECT SUM(...)` dari mutasi, atau kolom cache yang **selalu** bisa dihitung ulang.
- Debit saldo: `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`) pada baris saldo siswa di dalam `@Transactional`.
- **Wajib** test Testcontainers untuk: race debit bersamaan, idempotency tap ganda, saldo tak minus.

---

## 6. Audit Log

Tulis entri audit untuk aksi (PRD §11.7):
`void`, `koreksi`, `refund/pindah saldo`, `ubah harga jual`, `barang masuk & pembalik`, `penyesuaian stok`, `ubah limit/blokir`.

Field minimum: `aktor_id`, `sekolah_id`, `aksi`, `entitas`, `entitas_id`, `nilai_lama`, `nilai_baru`, `alasan`, `waktu`.

---

## 7. Keamanan

- Setiap endpoint: cek **JWT valid** → **tenant (sekolah)** → **RBAC (role/aksi)**.
- RBAC di backend; tombol tersembunyi di FE bukan pengaman.
- **Dilarang** menyimpan status kartu/saldo di cache FE atau client.
- **Dilarang** commit secret. Gunakan `.properties.example` + env.

---

## 8. Migration (Flyway)

- Setiap perubahan skema = **satu file migrasi baru**. Tidak pernah mengedit migrasi yang sudah di-commit & dijalankan.
- Nama: `V{nomor}__{DeskripsiSingkat}.sql`.
- Selalu idempoten bila memungkinkan (`IF NOT EXISTS`, `IF EXISTS`).
- Jalankan `./mvnw flyway:validate` sebelum PR.

---

## 9. Testing

| Jenis | Untuk | Tool |
|---|---|---|
| Unit | Aturan murni (HPP, validasi) | JUnit + Mockito |
| Integration | DB, ledger, locking, idempotency | Testcontainers PostgreSQL |
| e2e (FE) | Alur tap, void, blokir instan | Playwright |

- Coverage wajib untuk `KasirService`, ledger, HPP.
- Setiap bug yang diperbaiki → tambah test regresi.

---

## 10. Gaya Umum

- **Lombok**: `@Getter/@Setter` di entity, `@RequiredArgsConstructor` untuk DI, `@Data/@Builder` di DTO.
- **Bahasa komentar & Javadoc: Bahasa Indonesia** (konsisten admin-be).
- Tidak ada `System.out.println` — pakai logger (`HttpLogger`, `ErrorLogger`).
- Baris maksimal ~120 karakter; format konsisten.
- Import spesifik, hindari wildcard (kecuali `@Entity` Jakarta bila sudah jadi pola).

---

## 11. Toolchain & Versi (WAJIB parity `admin-be`)

> Lingkungan lengkap, cara install, & troubleshooting: **[`ENVIRONMENT.md`](./ENVIRONMENT.md)**.

| Komponen | Versi | Aturan |
|---|---|---|
| **JDK** | **25** (Temurin) | ❌ Jangan 21 / 26. `<java.version>25</java.version>` |
| **Spring Boot** | **4.0.2** (`spring-boot-starter-parent`) | samakan persis `admin-be` |
| **Build** | Maven **3.9.12** via `./mvnw` | ❌ jangan `mvn` global |
| **Timezone** | `Asia/Jakarta` | app + DB + container |
| Database | PostgreSQL + **Flyway** | migrasi bernomor, tak pernah di-edit |
| Persistence | Spring Data JPA (Hibernate) + starter-jdbc | pola `admin-be` |
| Cache | Redis | cache, blacklist token, rate limit |
| Security | Spring Security — JWT **RS256**, cookie HttpOnly | + Google OAuth2 (bila perlu) |
| JWT lib | **jjwt 0.12.6** | hash: BCrypt |
| File/Objek | MinIO (S3) | parity admin-be |
| Dokumen | Apache POI (Excel) · iText7 (PDF) · docx4j (Word) | parity admin-be |
| Email | starter-mail (SMTP) + Thymeleaf | parity admin-be |
| Lain | OWASP html-sanitizer · Lombok · Actuator | parity admin-be |

**Verifikasi wajib sebelum PR:** `./mvnw --version` menampilkan `Java version: 25`.
