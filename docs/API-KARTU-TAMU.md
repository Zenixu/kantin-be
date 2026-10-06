# 📋 API Endpoints — Kartu Tamu

Base URL: `http://localhost:8080/api/kartu-tamu`

## Authentication
Semua endpoint memerlukan JWT Bearer token di header:
```
Authorization: Bearer <token>
```

## Endpoints

### 1. GET `/` — Daftar Kartu Tamu
Ambil daftar semua kartu tamu milik sekolah.

**RBAC:** PETUGAS_KANTIN, PENGELOLA_KANTIN, TU_SEKOLAH, ADMIN_SEKOLAH

**Query params:**
- `hanyaAktif` (boolean, default: `false`) — jika `true`, hanya kartu aktif

**Response:**
```json
{
  "code": 200,
  "data": [
    {
      "id": 1728098765000,
      "sekolahId": 10,
      "nomorKartu": "KT-001",
      "rfidUid": "04:A1:B2:C3:D4:E5:F6",
      "aktif": true,
      "catatan": "Kartu untuk guru matematika",
      "dibuatOleh": 123,
      "dibuatPada": "2026-10-05T03:00:00Z"
    }
  ]
}
```

---

### 2. GET `/{kartuId}` — Detail Kartu
Ambil detail 1 kartu tamu.

**RBAC:** PETUGAS_KANTIN, PENGELOLA_KANTIN, TU_SEKOLAH, ADMIN_SEKOLAH

**Response:** (sama dengan item di daftar)

---

### 3. POST `/` — Buat Kartu Baru
Buat kartu tamu baru.

**RBAC:** PENGELOLA_KANTIN, TU_SEKOLAH, ADMIN_SEKOLAH

**Request body:**
```json
{
  "nomorKartu": "KT-002",
  "rfidUid": "04:A1:B2:C3:D4:E5:F7",
  "catatan": "Kartu tamu umum",
  "aktif": true
}
```

**Validasi:**
- `nomorKartu`: wajib, max 20 karakter, unique per sekolah
- `rfidUid`: opsional, max 50 karakter, unique global (anti-tabrakan dengan siswa)
- `catatan`: opsional
- `aktif`: opsional, default `true`

**Response:**
```json
{
  "code": 200,
  "message": "Kartu tamu berhasil dibuat",
  "data": { ... }
}
```

**Error:**
- `409 Conflict` — nomor kartu atau UID sudah digunakan

---

### 4. PUT `/{kartuId}` — Update Kartu
Update kartu tamu (nomor, UID, catatan, status).

**RBAC:** PENGELOLA_KANTIN, TU_SEKOLAH, ADMIN_SEKOLAH

**Request body:** (semua field opsional)
```json
{
  "nomorKartu": "KT-002-UPDATED",
  "rfidUid": "",
  "catatan": "Catatan baru",
  "aktif": false
}
```

**Catatan:**
- `rfidUid: ""` (empty string) = unbind kartu (hapus binding RFID)
- `rfidUid: null` = tidak diubah
- Field lain `null` = tidak diubah

**Response:**
```json
{
  "code": 200,
  "message": "Kartu tamu berhasil diperbarui",
  "data": { ... }
}
```

---

### 5. DELETE `/{kartuId}` — Nonaktifkan Kartu
Soft delete kartu tamu. Kartu nonaktif tidak bisa dipakai tap.

**RBAC:** PENGELOLA_KANTIN, TU_SEKOLAH, ADMIN_SEKOLAH

**Response:**
```json
{
  "code": 200,
  "message": "Kartu tamu berhasil dinonaktifkan",
  "data": { "aktif": false, ... }
}
```

---

## Business Rules

### Anti-Tabrakan UID
`rfidUid` harus **UNIQUE global** untuk mencegah tabrakan dengan `rfid_uid` siswa di admin-be:
- Validasi sisi kantin-be: cek `kartu_tamu` sebelum insert/update
- Validasi sisi admin-be: **TODO** (perlu koordinasi Q-koordinasi)

### Saldo Kartu Tamu
Saldo terikat ke **nomor kartu** (bukan orang):
- Top-up: via `POST /api/saldo/topup` dengan `subjekTipe=KARTU_TAMU`, `subjekId=<kartuId>`
- Tap: kasir tap RFID → lookup `kartu_tamu.rfid_uid` → potong saldo

### Tenant Scoping
Semua endpoint tenant-scoped (404 untuk sekolah lain).

---

## Use Cases

### UC-1: Buat Kartu untuk Guru
```bash
POST /api/kartu-tamu
{
  "nomorKartu": "KT-GURU-001",
  "catatan": "Untuk Pak Ahmad (Matematika)"
}
# Kartu dibuat, belum di-bind RFID (rfidUid=null)
```

### UC-2: Bind RFID ke Kartu
```bash
PUT /api/kartu-tamu/1728098765000
{
  "rfidUid": "04:A1:B2:C3:D4:E5:F6"
}
# Kartu sekarang bisa dipakai tap
```

### UC-3: Top-Up Kartu Tamu
```bash
POST /api/saldo/topup
{
  "subjekTipe": "KARTU_TAMU",
  "subjekId": 1728098765000,
  "nominal": 50000,
  "penyetor": "Pak Ahmad",
  "referensiId": "TOPUP-2026-001"
}
```

### UC-4: Tap Kartu Tamu di Kasir
```bash
POST /api/kasir/tap
{
  "idempotencyKey": "uuid-xxx",
  "rfidUid": "04:A1:B2:C3:D4:E5:F6",
  "items": [...]
}
# Backend lookup rfid_uid → dapat kartu_tamu → potong saldo
```

### UC-5: Nonaktifkan Kartu yang Hilang
```bash
DELETE /api/kartu-tamu/1728098765000
# Kartu nonaktif, tidak bisa tap
```

---

**Status:** ✅ Implementasi lengkap (model, repo, service, controller, migrasi V7)  
**Tested:** ✅ `KartuTamuServiceIT` — 20 uji integrasi Testcontainers (PostgreSQL 18 nyata)  
**TODO:** koordinasi anti-tabrakan `rfid_uid` dengan admin-be (sisi siswa, Q7)

---

## Integration test

`src/test/java/com/asqi/scholia_kantin_be/service/kartu/KartuTamuServiceIT.java`
(Testcontainers PostgreSQL 18, dijalankan pada fase `verify`) menegakkan:

| Kelompok | Yang diuji |
|---|---|
| Create | default aktif, UID `null` bila blank, nomor UNIQUE **per sekolah**, UID UNIQUE **global** (lintas sekolah) |
| Update | bind / unbind (`""` = hapus), UID sendiri tidak dianggap bentrok (`excludeId`), nomor bentrok ditolak, field `null` = tidak diubah |
| Soft delete | `nonaktifkanKartu` → `aktif=false` baris tetap ada; `daftarKartu(hanyaAktif)` menyaring |
| Isolasi tenant | detail/update kartu sekolah lain = 404; data pemilik tak berubah |
| Lookup tap | `cariByRfidUid` ketemu / 404 |
| Integrasi ledger | top-up `subjekTipe=KARTU_TAMU, subjekId=kartu.id` masuk `saldo_ledger` & `saldo_cache`; saldo kartu tamu terpisah dari saldo siswa |

Jalankan:

```bash
# dari kantin-be/ (lihat README-DEV.md §7 untuk mvn-run.sh)
./mvn-run.sh -Dspring-boot.repackage.skip=true \
  -Dtest=NoUnitTests -DfailIfNoTests=false \
  -Dit.test=KartuTamuServiceIT verify
```

> `-Dspring-boot.repackage.skip=true` hanya perlu bila jar sedang dikunci proses
> `kantin-be` yang berjalan lokal; tidak wajib di CI.
