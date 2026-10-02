# 🔌 INTEGRATIONS.md — Peta Integrasi `kantin-be` dengan SKOOLIA

> Semua panggilan lintas-sistem **WAJIB** melewati lapisan `service/integrasi/`. Tidak ada kode di modul lain yang boleh memanggil SKOOLIA langsung.
>
> Sumber analisa: pembedahan repo SKOOLIA yang di-clone (`admin-be`). Nomor baris bisa bergeser — verifikasi ulang saat implementasi.

---

## 1. Ikhtisar Arsitektur Integrasi

```
┌─────────────────────── SKOOLIA (sumber kebenaran identitas) ───────────────────────┐
│  admin-be    : user staf, role, sekolah, siswa+rfid_uid, Buku Kas, SekolahBalance   │
│  mobile-be   : user ortu, relasi ortu–anak, push notification                       │
│  callback-be : callback payment gateway (top-up online)                             │
│  internal-be : aktivasi modul & fee platform                                        │
└────────────────────────────────────────────────────────────────────────────────────┘
              ▲  verifikasi JWT (public key RS256)  ▲  REST API internal
              │                                    │
                          ┌───────────────────────┐
                          │      kantin-be        │
                          │  ledger, transaksi,   │
                          │  menu, stok, HPP,     │
                          │  sesi kasir, limit,   │
                          │  blokir, Kartu Tamu   │
                          └───────────────────────┘
```

**Prinsip:** kantin-be **memanggil** SKOOLIA via REST (bukan akses DB bersama). Identitas *dibaca*, data kantin *dimiliki* kantin-be.

---

## 2. Autentikasi (JWT RS256)

- kantin-be **TIDAK punya login sendiri** (PRD §4.1).
- Terima JWT dari dua penerbit:
  | Penerbit | Untuk siapa | Public key |
  |---|---|---|
  | `admin-be` | staf sekolah (petugas, pengelola, TU, bendahara, admin, kepsek) | ⛔ **PERLU KONFIRMASI** (path/endpoint) |
  | `mobile-be` | orang tua | ⛔ **PERLU KONFIRMASI** (repo belum ada di clone) |
- Verifikasi pakai `jjwt` 0.12.6, algoritma **RS256**. **Fallback HS256 DILARANG** di production.
- Klaim yang dipakai: `user_id`, `sekolah_id`, `role` (perlu konfirmasi bentuk persisnya).
- Implementasi: dua `JwtDecoder` + `AdminJwtAuthTokenFilter` / `MobileJwtAuthTokenFilter`.

> ⛔ **BLOCKING:** tanpa format klaim & public key yang pasti, fondasi auth tidak bisa ditulis. Lihat [`OPEN-QUESTIONS.md`](./OPEN-QUESTIONS.md) Q1.

---

## 3. Buku Kas (`admin-be`)

### 3.1 API yang dipanggil
```
BukuKasService.catatTransaksi(
    Sekolah sekolah, LocalDateTime tanggal, TipeTransaksi tipe,
    String kategori, BigDecimal jumlah, MetodePembayaran metode,
    String keterangan, [String sumberDana,] String refId, String refModul
)
```

### 3.2 Enum yang WAJIB sama (`com.asqi.scholia_admin_be.helper`)
| Enum | Nilai |
|---|---|
| `TipeTransaksi` | `MASUK`, `KELUAR` |
| `MetodePembayaran` | `TUNAI("1")`, `NON_TUNAI("2")`, `DANA_BOS("3")` |
| `SumberDana` | `KAS_SEKOLAH("KAS SEKOLAH")`, `DANA_BOS("DANA BOS")` |

### 3.3 Pemetaan posting kantin
| Event | tipe | kategori | metode | refModul (usulan) |
|---|---|---|---|---|
| Tutup kasir (total bersih sesi) | `MASUK` | `"Pendapatan Kantin"` | `NON_TUNAI` | `KANTIN_SESI` ⚠️ |
| Barang masuk (belanja stok) | `KELUAR` | `"Belanja Stok Kantin"` | `NON_TUNAI`/`TUNAI` | `KANTIN_BARANG_MASUK` ⚠️ |
| Koreksi sesi tertutup | `MASUK`/`KELUAR` | `"Penyesuaian Kantin"` | sesuai | `KANTIN_KOREKSI` ⚠️ |
| **Top-up** | ❌ **TIDAK diposting** | — | — | — |

### 3.4 ⚠️ Temuan kritis
1. **`refModul` baru belum dikenal `migrateBukuKas()`** (switch di baris ~396). Entri kantin berisiko dianggap orphan/duplikat saat migrasi. **Mitigasi sementara:** set `refModul = null` → masuk `remainingBks`, tidak dihapus (baris ~390). **Jangka panjang:** minta tim admin-be menambah case kantin.
2. **Buku Kas tidak idempoten** — `catatTransaksi()` selalu `save`. **kantin-be wajib** cek `existsByReferensiIdAndReferensiModul` atau simpan flag "sudah diposting" di tabel sesi kantin.
3. **Tipe `jumlah` di Buku Kas = `BigDecimal(15,2)`**, sedangkan kantin-be pakai integer rupiah. Kirim `BigDecimal.valueOf(rupiahLong)`.

### 3.5 Endpoint admin-be yang sudah ada (referensi konsumsi)
- `GET api/buku-kas/saldo` → `SaldoSekolahDTO{saldoTunai, saldoNonTunai, saldoDanaBos, saldoOnline}`
- `GET api/buku-kas/laporan-grafik`
- `GET api/buku-kas/metode-pembayaran`, `GET api/buku-kas/sumber-dana`

---

## 4. Lookup Kartu RFID & Siswa (`admin-be`)

### 4.1 Sumber data
```sql
-- migrasi V250__AddRfidUidToSiswa.sql
ALTER TABLE siswa ADD COLUMN IF NOT EXISTS rfid_uid VARCHAR(64);
CREATE UNIQUE INDEX idx_siswa_rfid_uid ON siswa (rfid_uid) WHERE rfid_uid IS NOT NULL;
```
```java
// SiswaRepository
Optional<Siswa> findByRfidUid(String rfidUid);
```

### 4.2 ⚠️ Temuan kritis
- `findByRfidUid` **tidak** memfilter sekolah & **tidak** memfilter status aktif. kantin-be **WAJIB** menambah:
  - cek `siswa.sekolah_id == jwt.sekolah_id` → jika beda, jawab **404** (PRD §11.4);
  - cek siswa aktif;
  - kartu sekolah lain = "Kartu tidak dikenal" (§6.1).
- **Anti-tabrakan UID:** saat bind UID siswa, admin-be (`SiswaService` ~baris 296) sudah tolak UID milik siswa lain — tapi **tidak** tahu tentang `kartu_tamu` (milik kantin-be). Tabrakan UID↔Kartu Tamu harus dicegah **di sisi kantin-be**.

### 4.3 Sinkronisasi kartu (PRD §4.3)
- Perubahan di admin-be (lepas/ganti kartu, siswa nonaktif) **harus dikirim ke kantin-be saat itu juga** (webhook + retry).
- Bila kantin-be meng-cache data kartu/siswa, cache **WAJIB di-invalidate** oleh notifikasi — **dilarang** mengandalkan TTL.
- Status **blokir** kartu **tidak boleh** di-cache sama sekali (§11.11).

---

## 5. Top-up Online (callback-be)

- Ortu top-up via PG → callback PG → `callback-be` → diteruskan ke kantin-be.
- Pemisahan dari callback tagihan sekolah = berdasarkan **referensi transaksi**.
- **Idempotency berdasarkan ID referensi PG** (§11.3).
- Saldo bertambah **hanya setelah callback sukses**. Callback duplikat ≠ 2×.

> ⛔ **BLOCKING:** kontrak payload callback dari callback-be perlu dikonfirmasi. Lihat Q4.

---

## 6. Notifikasi (mobile-be)

- Push ke ortu untuk: belanja, void, top-up online berhasil, top-up tunai TU, refund/pindah.
- Contoh: `"Budi belanja Rp8.000 di Kantin: Nasi Uduk, Es Jeruk"`.
- Implementasi: `NotifikasiClient` → API internal mobile-be.

> ⛔ **BLOCKING:** repo mobile-be belum ada di clone. Perlu endpoint & format. Lihat Q5.

---

## 7. Aktivasi Modul & Fee Platform (internal-be)

- Toggle aktivasi modul kantin **per sekolah**. Nonaktif → kantin-be **menolak seluruh request** sekolah tersebut.
- Konfigurasi fee platform per sekolah (per top-up nominal/persentase &/atau langganan).
- Laporan fee per sekolah per periode.

> ⛔ **BLOCKING:** kontrak API internal-be perlu dikonfirmasi. Lihat Q6.

---

## 8. Checklist Koordinasi dengan Tim SKOOLIA

Sebelum mulai Fase 3 (auth) & Fase 6 (fitur yang posting/notif):

- [ ] **Q1** Format klaim JWT staf + public key RS256 admin-be
- [ ] **Q2** Public key RS256 mobile-be + format klaim ortu
- [ ] **Q3** Tambah `refModul` kantin ke `migrateBukuKas()` admin-be (atau konfirmasi pakai `null`)
- [ ] **Q4** Kontrak payload callback top-up (refId PG)
- [ ] **Q5** Endpoint & format push notification mobile-be
- [ ] **Q6** Kontrak API aktivasi modul & fee (internal-be)
- [ ] **Q7** Mekanisme lookup kartu: API internal vs akses langsung; SLA latency
- [ ] **Q8** Pos Buku Kas "Pendapatan Kantin" & "Belanja Stok Kantin" dibuat otomatis saat aktivasi?
