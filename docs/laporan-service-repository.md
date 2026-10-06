# Laporan — Layer Service & Repository (Fase 4 Ledger + Fase 5 Stok/HPP)

**Ruang lingkup:** *Business Logic · Service Layer · Repository · JPA/Hibernate · Query (JPQL/Native)*
**Status:** ✅ Selesai & teruji — `mvn verify` hijau (72 tes, 0 gagal; unit + integrasi Testcontainers).

> Dokumen ini merangkum **apa yang dibangun, di mana, dan kenapa**. Aturan main
> tetap mengacu ke `architecture/` (AGENTS.md, CONVENTIONS.md, ADR-0003/0004, PRD §11).

---

## 1. Ringkasan hasil

| Lapisan | Isi |
|---|---|
| **Entity (JPA/Hibernate)** | 8 entitas untuk tabel V2–V4 (`saldo_ledger`, `saldo_cache`, `mutasi_stok`, `stok_cache`, `titik_kasir`, `sesi_kasir`, `transaksi`, `transaksi_item`) |
| **Repository** | 8 repository Spring Data JPA + JPQL agregat + native query `INSERT … ON CONFLICT` + `SELECT … FOR UPDATE` |
| **Service (bisnis)** | `LedgerSaldoService`, `LedgerStokService`, `HppService`, `SesiKasirService`, `TapService`, `TapValidator`, `VoidService`, `SaldoTopUpService` |
| **Integrasi (port)** | `KartuLookupPort`, `MenuLookupPort`, `BukuKasPort` + fallback (menyembunyikan Q7, Fase 5, & Q3) |
| **Helper** | `JamKantin` (zona sekolah), `IdGenerator` (ID monoton unik) |
| **Test** | 3 unit test + 6 kelas integrasi Testcontainers (race, idempotency, saldo/stok tak minus, append-only, posting Buku Kas) |

Semua **satu transaksi DB atomik** untuk tap, dan **append-only** untuk kedua ledger.

---

## 2. Peta paket

```
com.asqi.scholia_kantin_be
├── model/            # ENTITY JPA (Hibernate) — selaras 100% dengan skema Flyway
│   ├── SaldoLedger.java      @Immutable, Persistable (isNew=true)
│   ├── SaldoCache.java       @IdClass(subjek_tipe, subjek_id) — hot row FOR UPDATE
│   ├── MutasiStok.java       @Immutable, Persistable
│   ├── StokCache.java        PK menu_id — hot row FOR UPDATE
│   ├── TitikKasir.java
│   ├── SesiKasir.java
│   ├── Transaksi.java        Persistable (flag `baru`) — id di-assign aplikasi
│   └── TransaksiItem.java    Persistable (flag `baru`) — snapshot harga/HPP
│
├── repository/       # AKSES DATA
│   ├── SaldoLedgerRepository      JPQL Σ kredit−debit, belanja bersih harian
│   ├── SaldoCacheRepository       native INSERT … ON CONFLICT DO NOTHING
│   ├── MutasiStokRepository       JPQL Σ masuk−keluar
│   ├── StokCacheRepository        native INSERT … ON CONFLICT; query stok menipis
│   ├── TransaksiRepository        JPQL rekap sesi & laporan laba
│   ├── TransaksiItemRepository
│   ├── SesiKasirRepository        FOR UPDATE per titik+tanggal
│   └── TitikKasirRepository
│
├── service/
│   ├── kasir/
│   │   ├── LedgerSaldoService     KREDIT/DEBIT atomik + idempotency + anti-minus
│   │   ├── LedgerStokService      barang masuk/penjualan/void/opname
│   │   ├── HppService             rata-rata tertimbang (murni, mudah diuji)
│   │   ├── SesiKasirService       buka/tutup kasir + rekap bruto/void/bersih
│   │   ├── TapValidator           6 tahap validasi (PRD §6.1)
│   │   ├── TapService             orkestrasi tap (TransactionTemplate)
│   │   └── VoidService            void + kompensasi pembalik
│   ├── stok/
│   │   ├── LedgerStokService      (lihat di atas)
│   │   └── HppService
│   ├── saldo/SaldoTopUpService
│   └── integrasi/
│       ├── KartuLookupPort + KartuLookupFallback
│       ├── MenuLookupPort + MenuLookupFallback
│       └── BukuKasPort + BukuKasFallback   posting Buku Kas sesi (Q3, mitigasi refModul=null)
│           └── BukuKasPostingService       orkestrasi posting + idempotency (flag + ref unik)
│
├── helper/
│   ├── JamKantin.java     sumber waktu tunggal (zona sekolah)
│   └── IdGenerator.java   id = epochMillis×1000 + (offset+counter) mod 1000
│
├── enums/                 ArahMutasi, ArahStok, JenisMutasiSaldo/Stok,
│                          StatusTransaksi, StatusSesiKasir, ReferensiTipe
└── component/logging/AuditLogger.java   (placeholder log → nanti tabel audit_log)
```

---

## 3. Alur logika bisnis utama

### 3.1 Tap di kasir — `TapService.tap(...)` (PRD §6.1–6.2)

Satu panggilan = **satu transaksi DB** (dibungkus `TransactionTemplate`):

1. **Idempotency** — key dari klien diperiksa; key yang sama mengembalikan hasil
   transaksi pertama (tap ganda **tidak** memotong dua kali, PRD §11.3).
2. **Lookup kartu** lewat `KartuLookupPort` (status blokir diperiksa server tiap
   tap, **tanpa cache** — PRD §11.11).
3. **Validasi 6 tahap** (`TapValidator`, urut):
   `kartu dikenal → tidak diblokir → item/kategori tak diblokir ortu → stok cukup
   → ≤ limit harian → saldo ≥ total`.
4. **Eksekusi atomik:**
   buka/ambil sesi kasir → **potong stok** (`FOR UPDATE` per menu) →
   catat `transaksi` + `transaksi_item` (snapshot harga & HPP) → **debit saldo**
   (`FOR UPDATE` per subjek).

> **Kenapa `TransactionTemplate`, bukan `@Transactional`?** Bila dua request dengan
> idempotency key sama masuk nyaris bersamaan, yang kalah kena pelanggaran UNIQUE
> saat `save(transaksi)`. Karena kita menangkapnya **di luar** transaksi (sudah
> rollback bersih), request yang kalah bisa mengembalikan **hasil pemenang** —
> bukan error 409. Kontrak idempotency tetap benar pada kondisi balapan.

### 3.2 Ledger saldo — `LedgerSaldoService` (PRD §11.1–11.3)

- **Append-only:** setiap perubahan = satu baris `saldo_ledger` baru. Koreksi =
  baris baru dengan `arah` berlawanan (tak pernah UPDATE/DELETE).
- **Locking:** `saldo_cache` dikunci `SELECT … FOR UPDATE` (ADR-0003) → dua kasir
  tak bisa memotong saldo yang sama bersamaan.
- **Anti-minus:** diperiksa di service (409, pesan jelas) + dijaga CHECK DB.
- **Idempotency:** `idempotency_key` UNIQUE; key sama → hasil lama.
- `belanjaHariIni` dihitung **net** (Σ DEBIT `PENJUALAN` − Σ KREDIT
  `VOID_PENJUALAN`) agar transaksi yang di-void tidak lagi memakan jatah limit.

### 3.3 Ledger stok & HPP — `LedgerStokService` + `HppService` (PRD §7.2–7.4)

- **Barang masuk** → stok naik + HPP rata-rata tertimbang dihitung ulang:
  `HPP baru = (stok×HPP + qtyMasuk×hargaBeli) / (stok + qtyMasuk)` (HALF_UP, rupiah).
- **Penjualan** → stok turun, memakai HPP berjalan sebagai **snapshot** (disimpan
  di `transaksi_item.hpp_snapshot` & `mutasi_stok.hpp_snapshot`).
- **Void** → stok kembali memakai HPP snapshot transaksi → HPP rata-rata **tidak**
  berubah (konsisten).
- **Opname** → selisih dicatat sebagai mutasi MASUK/KELUAR dengan **alasan wajib**;
  HPP tidak berubah.

### 3.4 Sesi kasir — `SesiKasirService` (PRD §6.4)

- Satu titik kasir = satu sesi per tanggal (UNIQUE). Tap pertama otomatis membuka sesi.
- **Rekap:** `totalBersih = Σ transaksi SUKSES` (diposting ke Buku Kas saat tutup),
  `totalVoid = Σ transaksi VOID`, `totalBruto = bersih + void`.
- **Tutup sesi** mengunci transaksi sesi itu → void setelah tutup ditolak
  (koreksi hanya oleh bendahara).
- **Posting Buku Kas** (`BukuKasPostingService`, PRD §13 poin 7 / INTEGRATIONS §3):
  saat sesi ditutup (manual, auto-tutup per-sekolah, atau auto-tutup lintas-tenant)
  total bersih diposting sebagai pendapatan lewat `BukuKasPort`. Idempoten dua lapis:
  flag `posting_buku_kas` + `referensi_buku_kas` UNIQUE. Kegagalan posting **tidak**
  menggagalkan penutupan sesi (dicatat untuk retry via `POST /kasir/sesi/{id}/posting-buku-kas`).

### 3.5 Void — `VoidService` (PRD §6.3)

- Wajib **alasan**; hanya untuk sesi **belum ditutup**.
- Saldo dikembalikan penuh + stok dikembalikan (HPP snapshot) lewat **mutasi
  pembalik baru** — baris ledger asli tidak disentuh.
- `transaksi` boleh di-UPDATE statusnya (bukan ledger).

---

## 4. Keputusan teknis penting

| # | Keputusan | Alasan |
|---|---|---|
| 1 | **Pessimistic lock** (`PESSIMISTIC_WRITE`), bukan `@Version` | Hot row (saldo/stok) — ADR-0003; juga menghindari kolom `version` yang **tidak ada** di skema |
| 2 | Ledger `implements Persistable` + `isNew()==true` + `@Immutable` | `save()` selalu INSERT (append-only), tak pernah UPDATE |
| 3 | `Transaksi`/`TransaksiItem` `Persistable` dengan flag `baru` | `id` di-assign aplikasi → tanpa flag, `save()` memakai `merge()` dan error "No row with the given identifier" |
| 4 | `IdGenerator` (epochMillis×1000 + offset/counter) | Ganti pola lama (epoch-millis + 3 acak) yang rawan tabrakan PK & tak monoton |
| 5 | Uang = **integer rupiah** (`BigDecimal` hanya alat hitung sementara) | PRD §11.6, CONVENTIONS §4 |
| 6 | Waktu **wajib** lewat `JamKantin` | Zona sekolah konsisten (PRD §11.9) |
| 7 | `sekolahId` eksplisit di **setiap** query | Tenant scoping terlihat jelas (PRD §11.4) |
| 8 | Lookup kartu/menu di balik **port** | Menyembunyikan Q7 (kontrak admin-be) & Fase 5 (katalog) → alur tap tetap bisa dibangun & diuji |
| 9 | `AuditLogger` = placeholder log | Tabel `audit_log` belum ada; kontrak pemanggil tidak akan berubah saat tabel dibuat |

---

## 5. Test

**Unit** (`mvn test`, tanpa Docker):
`HppServiceTest` (5) · `IdGeneratorTest` (3) · `TapValidatorTest` (9).

**Integrasi Testcontainers** (`mvn verify`, butuh Docker) — PostgreSQL 18 nyata:

| Kelas | Menegakkan |
|---|---|
| `LedgerSaldoServiceIT` (5) | append-only (trigger DB menolak UPDATE/DELETE), idempotency, saldo tak minus, **race** debit bersamaan (5 sukses / 3 gagal dari 8 paralel), hitung ulang dari ledger |
| `LedgerStokServiceIT` (6) | HPP rata-rata tertimbang, snapshot penjualan, stok tak minus saat race, opname tanpa selisih, append-only |
| `TapServiceIT` (4) | tap sukses (saldo+stok+snapshot HPP), idempotency tap ganda, saldo kurang & stok kurang **tidak mengubah apa pun** |
| `VoidServiceIT` (5) | saldo+stok kembali via mutasi pembalik, alasan wajib, void ganda ditolak, sesi tertutup ditolak, tenant lain → 404 |
| `SesiKasirServiceIT` (4) | rekap bruto/void/bersih, tutup sesi mengunci & menyimpan rekap, auto-tutup, auto-tutup lintas-tenant (hanya sesi tertinggal) |
| `BukuKasPostingServiceIT` (5) | posting pendapatan saat tutup sesi, idempotency (flag + ref unik) dobel-tutup & retry, nominal 0 dilewati, gagal-posting tak menggagalkan tutup, fallback fail-safe `DILEWATI` |

**Hasil:** `Tests run: 72, Failures: 0, Errors: 0` → **BUILD SUCCESS**.

### 5.1 Catatan menjalankan test

- **JDK 25 wajib** (Lombok rusak di JDK 27). Set `JAVA_HOME` ke JDK 25 sebelum build.
- Docker harus hidup. Bila Docker Desktop memakai engine **29+**, Testcontainers
  1.21 (docker-java) gagal negosiasi versi API → sudah diatasi dengan mematok
  `-Dapi.version=1.44` (lihat properti `docker.api.version` di `pom.xml`;
  override bila Docker lebih lama: `-Ddocker.api.version=1.43`).
- Uji integrasi **di-skip otomatis** bila Docker mati (`@EnabledIfDockerAvailable`),
  sehingga `mvn test` tetap hijau di CI tanpa Docker.
- Surefire = `*Test` (unit) · Failsafe = `*IT` (integrasi) — dipisah agar
  `mvn test` cepat dan tak butuh Docker.

---

## 6. Yang **belum** termasuk (dan alasannya)

| Item | Menunggu |
|---|---|
| `KartuLookupPort` nyata (REST ke admin-be) | **Q7** (kontrak lookup kartu) — fallback mengembalikan "tidak dikenal" |
| `MenuLookupPort` nyata (katalog) | **Fase 5** (modul katalog) — fallback mengembalikan `null` |
| Posting Buku Kas saat tutup sesi | **Q3** — ✅ **dibangun (2026-10-06)** lewat `BukuKasPort`; fallback `DILEWATI` sampai admin-be menambah case `refModul` kantin (mitigasi: `refModul=null`) |
| `BukuKasPort` nyata (REST ke admin-be) | **Q3** — fallback fail-safe mengembalikan `DILEWATI`; impl `@Primary` menyusul |
| `AuditLogger` ke tabel `audit_log` | Belum ada migrasi tabel audit |
| `TenantResolver` (isi `sekolahId` dari token) | **Q1/Q2** (klaim JWT admin-be belum membawa `sekolah_id`) |

Controller (`KasirController`) sengaja **belum** dihubungkan — itu bagian lapisan
API, di luar ruang lingkup *Service & Repository* ini.
