# 🤝 Integrasi Anti-Tabrakan UID Kartu Tamu ↔ Siswa (issue #29)

- **Tanggal:** 2026-10-08
- **Pengusul:** Tim kantin-be
- **Terkait:** `KartuTamuService.java` TODO; PRD §4.3, §9.4; INTEGRATIONS.md §4.2;
  ADR-0004 (pola lookup kartu); OPEN-QUESTIONS **Q7**; issue **#29** & #38.
- **Untuk:** tim **admin-be** (M-Arkan-Raihan-Nugraha) & tim kantin-be.

> Tujuan: mencegah **dua pemegang kartu dengan `rfid_uid` sama** (kartu tamu vs
> siswa), yang menyebabkan **tabrakan scan** (kartu yang di-tap terbaca sebagai
> pemilik lain). Validasi harus **dua arah**.

---

## 1. Masalah

- `kartu_tamu.rfid_uid` (kantin-be) bersifat **UNIQUE global**.
- `siswa.rfid_uid` (admin-be) bersifat **UNIQUE** (partial index, `admin-be`
  migrasi `V250__AddRfidUidToSiswa.sql`).
- **Kedua tabel di DB terpisah** (ADR-0001/ADR-0010) ⇒ masing-masing **tidak tahu**
  UID milik tabel yang lain. Tanpa koordinasi, UID sama bisa terdaftar di
  keduanya → saat tap, pemetaan UID→pemilik menjadi ambigu.

**Sudah ada (kantin-be):** tolak UID yang sudah dipakai **Kartu Tamu lain**
(`KartuTamuRepository.existsByRfidUidExcluding`).

**Belum ada:** validasi lintas-sistem (kartu tamu ↔ siswa).

---

## 2. Kontrak Integrasi (dua arah)

### 2.1 admin-be → kantin-be — "apakah UID ini dipakai Kartu Tamu?"

Disediakan kantin-be sebagai **endpoint internal** (mesin-ke-mesin):

```
GET /api/internal/kartu-tamu/cek-uid?rfidUid=<UID>&sekolahId=<opsional>

Header:
  X-Internal-Timestamp: <epoch detik>
  X-Internal-Signature: sha256=<hex HMAC-SHA256(rahasia, timestamp + "." + body)>
```

- **Rahasia:** `KANTIN_INTERNAL_SECRET` (terpisah dari `KANTIN_WEBHOOK_SECRET`).
- **Anti-replay:** timestamp di luar `±kantin.internal.tolerance-seconds` (default 300s) → **401**.
- **Fail-closed:** rahasia kosong / endpoint dinonaktifkan → **503**.
- **Allowlist IP** opsional (`kantin.internal.allowed-ips`).

**Respons (200):**
```json
{ "code": 200, "data": { "sekolahId": 12, "rfidUid": "ABCDEF1234", "dipakai": true } }
```

- `dipakai=true` ⇒ **admin-be WAJIB menolak** `rfid_uid` itu di `SiswaService`.
- UID dicek **lintas sekolah** (UID global), jadi `sekolahId` hanya untuk jejak.
- GET **tanpa body**; HMAC dihitung atas `timestamp + "." + ""` (body kosong).

### 2.2 kantin-be → admin-be — "apakah UID ini dipakai siswa?"

Disediakan kantin-be lewat **port** `UidSiswaPort` (kantin-be memanggil admin-be).
Kontrak REST-nya **belum final (Q7)**; saat terjawab, admin-be menyediakan
endpoint internal, mis.:

```
GET /api/internal/siswa/cek-uid?rfidUid=<UID>&sekolahId=<ID>
  → { "dipakai": true|false }   (HMAC/rahasia internal admin-be)
```

Selama belum ada, `UidSiswaFallback` mengembalikan `null` (“tidak diketahui”) →
kantin-be **fail-open** (tidak menolak keliru).

---

## 3. Aturan Validasi (wajib di kedua sisi)

| Sisi | Aksi | Status di kode |
|---|---|---|
| kantin-be (lokal) | Tolak UID yang sudah dipakai Kartu Tamu lain | ✅ `existsByRfidUidExcluding` |
| kantin-be → admin-be | Tolak UID yang sudah dipakai **siswa** | ✅ `UidSiswaPort` (fallback fail-open sampai Q7) |
| admin-be → kantin-be | Tolak `rfid_uid` yang sudah dipakai **Kartu Tamu** | ✅ endpoint internal disediakan; **⏳ admin-be memanggilnya di `SiswaService`** |

**Semantik:** saat bind/ubah UID pada salah satu sistem, panggil cek sisi lain;
bila `dipakai=true` → **409 Conflict** ("UID sudah terdaftar pada kartu lain").

---

## 4. Contoh Integrasi admin-be (pseudo)

```java
// SiswaService.java (admin-be) — saat bind/ubah rfid_uid
boolean dipakaiKartuTamu = kantinClient.cekUidKartuTamu(sekolahId, rfidUid);
if (dipakaiKartuTamu) {
    throw new ConflictException("RFID UID " + rfidUid + " sudah dipakai Kartu Tamu kantin");
}
```

`kantinClient` menandatangani request (HMAC + timestamp) memakai
`KANTIN_INTERNAL_SECRET`.

---

## 5. Catatan Keamanan

- Endpoint internal **bukan** endpoint user — jangan buka tanpa HMAC.
- **Jangan** menaruh rahasia di kode/commit (AGENTS.md §10) — pakai environment.
- Bila admin-be di-deploy dengan IP tetap, isi `kantin.internal.allowed-ips`.
- Endpoint hanya mengembalikan **boolean** (tanpa data pribadi) — minim informasi.

---

## 6. Tindak Lanjut

- [ ] **Tim admin-be:** panggil `GET /api/internal/kartu-tamu/cek-uid` di
      `SiswaService` (bind/ubah `rfid_uid`) + sediakan endpoint cek UID siswa
      untuk arah sebaliknya (Q7).
- [ ] **Tim kantin-be:** saat Q7 terjawab, buat implementasi nyata `UidSiswaPort`
      (mis. `SiswaUidClient`) & tandai `@Primary`.
- [ ] Set `KANTIN_INTERNAL_SECRET` (env) di staging/produksi.
- [ ] Uji lintas-sistem: daftar UID yang sama di kedua sisi → keduanya menolak.
