# Spesifikasi — Layer Service & Repository (Fase 4 Ledger + Fase 5 Stok/HPP)

> Status: **DIIMPLEMENTASI & DIVERIFIKASI** (2026-10-02)
> Kode: `model/` (8 entitas) · `repository/` (8 repo) · `service/` (9 service + port) · `helper/`
> Acuan: PRD §6.1–6.4, §7.2–7.4, §11.1–11.6 · ADR-0003/0004 · CONVENTIONS.md §4, §8, §9
> Pasangan dokumen: `docs/spesifikasi-fase4-ledger.md` (skema DB) — dokumen ini adalah **sisi kode**-nya.

## 0. List Tugas (bagian ini)

- Menulis alur logika bisnis utama (tap, ledger saldo, ledger stok/HPP, sesi kasir, void)
- Mengelola lapisan layanan (Service Layer) dengan satu transaksi DB atomik
- Membuat lapisan akses data (Repository) + JPQL agregat & native query
- Memetakan entitas JPA/Hibernate selaras skema Flyway (tanpa `ddl-auto`)
- Menegakkan locking, idempotency, append-only, dan anti saldo/stok minus di kode
- Menguji: unit + integrasi Testcontainers (race, idempotency, saldo/stok tak minus)

## 0b. Output (bagian ini)

- 8 entitas JPA (V2–V4) + 8 repository Spring Data JPA
- 9 service bisnis + 2 port integrasi (Kartu/Menu) + fallback
- Query JPQL agregat (saldo, belanja harian net, stok, rekap sesi, laba HPP) + native `INSERT .. ON CONFLICT`
- Locking `SELECT .. FOR UPDATE` pada baris cache (ADR-0003)
- 40 pengujian lolos: **17 unit + 23 integrasi Testcontainers** → `mvn verify` = BUILD SUCCESS
- Dokumentasi ini (`docs/spesifikasi-service-repository.md`)

---

## 1. Prinsip Desain (kenapa begini)

| Prinsip | Alasan |
|---|---|
| **Satu transaksi DB per tap** | `TapService` dibungkus `TransactionTemplate`: potong stok + catat transaksi/item + debit saldo **atomik**. Gagal di tengah → semua rollback, tak ada state separuh. |
| **Idempotency tahan balapan** | Memakai `TransactionTemplate` (bukan `@Transactional`) supaya pelanggaran UNIQUE `idempotency_key` ditangkap **setelah rollback bersih** → request yang kalah mengembalikan **hasil pemenang**, bukan error 409 (PRD §11.3). |
| **Locking di baris cache** | `saldo_cache` & `stok_cache` dikunci `PESSIMISTIC_WRITE` (`SELECT .. FOR UPDATE`) per subjek/menu → menyerialkan tap bersamaan (ADR-0003). **Bukan** `@Version` — kolom `version` tidak ada di skema. |
| **Ledger selalu INSERT** | Entitas ledger `implements Persistable` + `isNew()==true` + `@Immutable` → `save()` tak pernah UPDATE (append-only, PRD §11.1). |
| **Anti minus di kode + DB** | Dicek di service (409, pesan jelas) **dan** dijaga CHECK DB. Kode memberi pesan ramah; DB jadi jaring terakhir. |
| **Uang = integer rupiah** | `BigDecimal` hanya alat hitung sementara (HALF_UP), hasil disimpan `long` (PRD §11.6, CONVENTIONS §4). |
| **Waktu lewat `JamKantin`** | Satu sumber waktu, zona sekolah konsisten (PRD §11.9). Tak ada `OffsetDateTime.now()` liar di service. |
| **`sekolahId` eksplisit di setiap query** | Tenant scoping terlihat jelas di tiap repository (PRD §11.4). |
| **Integrasi di balik port** | `KartuLookupPort`/`MenuLookupPort` menyembunyikan **Q7** (kontrak admin-be) & **Fase 5** (katalog) → alur tap bisa dibangun & diuji sekarang, tanpa menunggu. |

## 2. Komponen

### 2.1 Entity JPA/Hibernate — `model/` (selaras V2–V4)
| Entitas | Tabel | Catatan |
|---|---|---|
| `SaldoLedger` | `saldo_ledger` | `@Immutable` + `Persistable` (isNew selalu true) → INSERT-only |
| `SaldoCache` | `saldo_cache` | `@IdClass(subjek_tipe, subjek_id)` — **hot row** `FOR UPDATE` |
| `MutasiStok` | `mutasi_stok` | `@Immutable` + `Persistable`; `hpp_snapshot`, `alasan` |
| `StokCache` | `stok_cache` | PK `menu_id` — **hot row** `FOR UPDATE` |
| `Transaksi` | `transaksi` | `Persistable` (flag `baru`) — id di-assign aplikasi |
| `TransaksiItem` | `transaksi_item` | `Persistable`; snapshot harga & HPP saat jual |
| `TitikKasir` | `titik_kasir` | titik kasir per sekolah |
| `SesiKasir` | `sesi_kasir` | buka/tutup + rekap, `posting_buku_kas` |

> **Catatan `Persistable`:** `transaksi.id` & `transaksi_item.id` di-assign aplikasi (bukan autoincrement), jadi Hibernate tak tahu baris sudah ada. Tanpa `Persistable.isNew()`, `save()` memakai `merge()` → SELECT baris yang belum ada → `ObjectRetrievalFailureException`. Penanda `@Transient @Builder.Default boolean baru` + `@PostPersist/@PostLoad` menyelesaikannya.

### 2.2 Repository — `repository/`
| Repository | Query kunci |
|---|---|
| `SaldoLedgerRepository` | JPQL `Σ kredit − Σ debit` (hitung ulang saldo dari ledger); belanja harian **net** (DEBIT penjualan − KREDIT void) untuk limit tap |
| `SaldoCacheRepository` | native `INSERT .. ON CONFLICT DO NOTHING` (buat baris cache tanpa race); `findById` + lock |
| `MutasiStokRepository` | JPQL `Σ masuk − Σ keluar` |
| `StokCacheRepository` | native `INSERT .. ON CONFLICT DO NOTHING`; query stok menipis |
| `TransaksiRepository` | JPQL rekap sesi per status (SUKSES/VOID); laporan laba (Σ HPP) |
| `TransaksiItemRepository` | item per transaksi |
| `SesiKasirRepository` | ambil sesi per titik+tanggal (UNIQUE) |
| `TitikKasirRepository` | titik kasir per sekolah |

### 2.3 Service — `service/`
| Service | Tanggung jawab |
|---|---|
| `kasir/LedgerSaldoService` | KREDIT/DEBIT atomik + idempotency + anti saldo-minus; lock `saldo_cache`; tulis `saldo_setelah` (running balance) |
| `kasir/LedgerStokService` | barang masuk / penjualan / void / opname; HPP snapshot; anti stok-minus |
| `kasir/HppService` | rata-rata tertimbang (murni, mudah diuji): `HPP' = (stok·HPP + qtyMasuk·hargaBeli) / (stok+qtyMasuk)` |
| `kasir/SesiKasirService` | buka/tutup sesi; rekap `bruto = bersih + void`; tutup mengunci transaksi sesi |
| `kasir/TapValidator` | 6 tahap validasi (PRD §6.1) — murni, tanpa I/O |
| `kasir/TapService` | orkestrasi tap, 1 transaksi DB, idempotency tahan balapan |
| `kasir/VoidService` | void beralasan + kompensasi pembalik (saldo & stok) |
| `saldo/SaldoTopUpService` | top-up saldo (KREDIT) |
| `integrasi/KartuLookupPort`, `MenuLookupPort` (+ `*Fallback`) | lookup kartu/menu (Q7 & Fase 5) |

### 2.4 Helper — `helper/`
- `JamKantin` — sumber waktu tunggal (zona sekolah).
- `IdGenerator` — `id = epochMillis×1000 + (offsetNode + counter) mod 1000`: **monoton naik** dalam JVM (aman `ORDER BY id`), **unik** pada ms sama, **anti-tabrakan antar-JVM** via offset acak. Mitigasi pragmatis atas temuan **§5.5** dokumen skema (pola lama epoch+random rawan tabrakan PK); keputusan final (sequence vs ULID) tetap menunggu tim, mudah ditukar karena semua lewat bean ini.

## 3. Alur Logika Bisnis Utama

### 3.1 Tap — `TapService.tap(...)` (PRD §6.1–6.2)
Satu panggilan = **satu transaksi DB**:
1. **Idempotency** — key sama → kembalikan hasil transaksi pertama.
2. **Lookup kartu** via `KartuLookupPort` (status blokir dicek server tiap tap, **tanpa cache**, PRD §11.11).
3. **Validasi 6 tahap** (`TapValidator`): dikenal → tak diblokir → item/kategori tak diblokir ortu → stok cukup → ≤ limit harian → saldo ≥ total.
4. **Eksekusi atomik**: sesi kasir → potong stok (`FOR UPDATE` per menu) → tulis `transaksi`+`transaksi_item` (snapshot harga & HPP) → debit saldo (`FOR UPDATE` per subjek).

### 3.2 Ledger Saldo — `LedgerSaldoService` (PRD §11.1–11.3)
- **Append-only**; koreksi = baris `arah` berlawanan.
- **Locking** `saldo_cache` `FOR UPDATE` (ADR-0003).
- **Anti-minus** cek service (409) + CHECK DB.
- **Idempotency** `idempotency_key` UNIQUE.
- **Belanja harian net** → transaksi yang di-void tak lagi memakan jatah limit.

### 3.3 Ledger Stok & HPP — `LedgerStokService` + `HppService` (PRD §7.2–7.4)
- **Masuk** → stok naik, HPP rata-rata dihitung ulang (HALF_UP, rupiah).
- **Penjualan** → stok turun, HPP berjalan jadi **snapshot** (`transaksi_item.hpp_snapshot`, `mutasi_stok.hpp_snapshot`).
- **Void** → stok kembali pakai HPP snapshot → rata-rata **tidak** berubah.
- **Opname** → selisih dicatat mutasi MASUK/KELUAR, **alasan wajib**; HPP tidak berubah.

### 3.4 Sesi Kasir — `SesiKasirService` (PRD §6.4)
- Satu titik = satu sesi per tanggal (UNIQUE); tap pertama otomatis buka.
- `totalBersih = Σ SUKSES` · `totalVoid = Σ VOID` · `totalBruto = bersih + void`.
- Tutup sesi mengunci transaksi sesi → void setelah tutup ditolak.

### 3.5 Void — `VoidService` (PRD §6.3)
- Wajib **alasan**; hanya sesi **belum ditutup**.
- Saldo & stok dikembalikan via **mutasi pembalik baru** — baris ledger asli tak disentuh.

## 4. Bukti Verifikasi (dijalankan 2026-10-02)

`mvn verify` → **BUILD SUCCESS**, PostgreSQL 18 nyata via Testcontainers:

| # | Uji (unit) | Hasil |
|---|---|---|
| 1 | `HppServiceTest` (5) — rata-rata, pembulatan HALF_UP, stok kosong | ✅ |
| 2 | `IdGeneratorTest` (3) — monoton, unik pada trafik tinggi | ✅ |
| 3 | `TapValidatorTest` (9) — 6 tahap validasi + kasus batas | ✅ |

| # | Uji (integrasi `*IT`) | Hasil |
|---|---|---|
| 4 | `LedgerSaldoServiceIT` (5) — append-only (trigger tolak UPDATE/DELETE), idempotency, saldo tak minus, **race** 8 debit paralel (5 sukses/3 gagal), hitung ulang dari ledger | ✅ |
| 5 | `LedgerStokServiceIT` (6) — HPP rata-rata, snapshot penjualan, stok tak minus saat race, opname tanpa selisih, append-only | ✅ |
| 6 | `TapServiceIT` (4) — tap sukses (saldo+stok+snapshot), tap ganda idempotent, saldo/stok kurang **tak mengubah apa pun** | ✅ |
| 7 | `VoidServiceIT` (5) — saldo+stok kembali via pembalik, alasan wajib, void ganda & sesi tertutup ditolak, tenant lain → 404 | ✅ |
| 8 | `SesiKasirServiceIT` (3) — rekap bruto/void/bersih, tutup sesi mengunci & simpan rekap, auto-tutup | ✅ |

**Total: 40 test (17 unit + 23 integrasi), Failures: 0, Errors: 0.**

### 4.1 Cara menjalankan
- **JDK 25 wajib** (Lombok rusak di JDK 27). Set `JAVA_HOME` ke JDK 25.
- Docker hidup untuk `*IT`. Bila Engine **29+**: patok `-Dapi.version=1.44` (sudah di `pom.xml` via properti `docker.api.version`; override `-Ddocker.api.version=1.43` bila Docker lebih lama).
- `mvn test` = unit saja (tetap hijau tanpa Docker) · `mvn verify` = ikut integrasi.
- `*IT` **di-skip otomatis** bila Docker mati (`@EnabledIfDockerAvailable`).

## 5. Jebakan yang Ditemukan (penting untuk tim)

1. **Entitas ber-ID assigned wajib `Persistable`.** `Transaksi`/`TransaksiItem` semula tak punya penanda → `save()` pakai `merge()` → `ObjectRetrievalFailureException: No row with the given identifier exists` saat tap pertama. Perbaikan: flag `@Transient @Builder.Default boolean baru` + `isNew()`/`@PostPersist`/`@PostLoad`.
2. **Testcontainers 1.21 vs Docker Engine 29+.** docker-java bernegosiasi versi API lama → HTTP 400 *"Could not find a valid Docker environment"* → semua `*IT` ter-skip. Perbaikan: patok `api.version=1.44` (Docker 25+).
3. **`@Builder.Default` wajib** bila field punya nilai awal dan dipakai lewat builder — tanpa itu Lombok mengeset `false`.
4. **Idempotency harus di luar transaksi.** Bila pelanggaran UNIQUE ditangkap **di dalam** `@Transactional`, transaksi sudah ditandai rollback-only → tak bisa lagi mengembalikan hasil pemenang. Karena itu `TapService` memakai `TransactionTemplate`.
5. **Jangan pakai `@Version`** untuk locking di sini — kolom `version` **tidak ada** di skema Flyway; locking lewat `FOR UPDATE` pada baris cache (ADR-0003).

## 6. Yang Belum Termasuk (dan alasannya)

| Item | Menunggu |
|---|---|
| `KartuLookupPort` nyata (REST admin-be) | **Q7** — fallback mengembalikan "tidak dikenal" |
| `MenuLookupPort` nyata (katalog) | **Fase 5** — fallback mengembalikan `null` |
| Posting Buku Kas saat tutup sesi | **Q3** — service hanya menyiapkan angka rekap (`posting_buku_kas=false`) |
| `AuditLogger` → tabel `audit_log` | Tabel belum ada (V5); sekarang placeholder log |
| `TenantResolver` (isi `sekolahId` dari token) | **Q1/Q2** — klaim JWT admin-be belum membawa `sekolah_id` |
| `KasirController` | Lapisan API — di luar ruang lingkup *Service & Repository* |
