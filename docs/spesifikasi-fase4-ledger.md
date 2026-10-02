# Spesifikasi Skema — Fase 4: Ledger (Saldo & Stok)

> Status: **DIIMPLEMENTASI & DIVERIFIKASI** (2026-10-02)
> Migrasi: `V2__CreateLedgerSaldo.sql`, `V3__CreateLedgerStok.sql`, `V4__CreateTransaksi.sql`
> Acuan: PRD §6.2–6.6, §11.1–11.3 · ADR-0003 · CONVENTIONS.md §1 & §8

## 1. Prinsip Desain (kenapa begini)

| Prinsip | Alasan |
|---|---|
| **Ledger append-only** | `saldo_ledger` & `mutasi_stok` tidak boleh di-UPDATE/DELETE. Koreksi = baris baru dengan `arah` berlawanan (ADR-0003). Ditegakkan **di database** oleh trigger `tolak_perubahan_ledger()`. |
| **Saldo = turunan** | Saldo/stok **tidak** disimpan sebagai satu-satunya sumber. Sumber kebenaran = SUM(ledger). `saldo_cache`/`stok_cache` hanya *cache* yang bisa dibangun ulang. |
| **Cache bisa diverifikasi** | Setiap baris ledger menyimpan `saldo_setelah` (running balance) → audit & deteksi drift. |
| **Locking lewat baris cache** | `SELECT ... FOR UPDATE` pada `saldo_cache`/`stok_cache` menyerialkan tap bersamaan per subjek/menu (ADR-0003). |
| **Idempotency** | `idempotency_key` UNIQUE mencegah tap ganda & posting Buku Kas ganda. |
| **Anti saldo/stok minus** | CHECK `saldo_setelah >= 0` & `stok_setelah >= 0` di level DB (bukan hanya kode). |
| **Tenant scoping** | Semua tabel ledger/transaksi punya `sekolah_id` dan indeks dimulai dengan `sekolah_id`. |
| **Uang = BIGINT rupiah** | Tidak pakai FLOAT/NUMERIC pecahan; rupiah bilangan bulat. |
| **ID dari aplikasi** | `Constants.idGenerator()` (pola admin-be, CONVENTIONS §1). Tidak ada BIGSERIAL untuk entitas bisnis. |

## 2. Tabel

### `saldo_ledger` — buku besar saldo siswa/tamu (append-only)
| Kolom | Tipe | Catatan |
|---|---|---|
| `id` | BIGINT PK | dari aplikasi |
| `sekolah_id` | BIGINT NOT NULL | tenant |
| `subjek_tipe` | VARCHAR(20) | `SISWA` \| `KARTU_TAMU` |
| `subjek_id` | BIGINT | id siswa (lokal) / id kartu tamu |
| `arah` | VARCHAR(10) | `KREDIT` (masuk) \| `DEBIT` (keluar) |
| `jenis` | VARCHAR(40) | `TOPUP`, `PENJUALAN`, `KOREKSI`, `REFUND`, `TRANSFER`, … |
| `nominal` | BIGINT > 0 | selalu positif; arah ditentukan `arah` |
| `saldo_setelah` | BIGINT >= 0 | running balance (audit) |
| `transaksi_id` | BIGINT | bila berasal dari transaksi kasir |
| `idempotency_key` | VARCHAR(64) UNIQUE | anti-ganda |
| `referensi_tipe` / `referensi_id` | VARCHAR | tautan ke sumber (mis. `TOPUP`/id midtrans) |
| `aktor_id`, `keterangan`, `waktu`, `created_at` | | |

Indeks: `(sekolah_id, subjek_tipe, subjek_id, id)`, `(sekolah_id, waktu)`, debit harian (limit tap).

### `saldo_cache` — saldo terkini (di-lock saat mutasi)
`subjek_tipe`, `subjek_id`, `sekolah_id`, `saldo`, `updated_at` · PK gabungan `(subjek_tipe, subjek_id)`.

### `mutasi_stok` / `stok_cache`
Sama polanya untuk stok: `arah` = `MASUK` \| `KELUAR`, `qty`, `stok_setelah`, `hpp_snapshot` (untuk HPP/laba), `alasan` (wajib untuk koreksi).

### `transaksi`, `transaksi_item`, `titik_kasir`, `sesi_kasir`
- `transaksi`: header penjualan; `subjek_tipe` = `SISWA`|`KARTU_TAMU`; `status` = `SUKSES`|`VOID`; CHECK **void wajib beralasan** (`alasan_void`, `void_at`); `total_hpp` untuk laba.
- `transaksi_item`: baris item + `hpp_satuan` (snapshot HPP saat jual).
- `titik_kasir`: titik kasir per sekolah (bisa >1).
- `sesi_kasir`: buka/tutup kasir, total penjualan tunai vs saldo, status posting Buku Kas.

## 3. Trigger append-only

```sql
CREATE TRIGGER trg_saldo_ledger_append_only
    BEFORE UPDATE OR DELETE ON saldo_ledger
    FOR EACH ROW EXECUTE FUNCTION tolak_perubahan_ledger();
```
Fungsi `tolak_perubahan_ledger()` menolak UPDATE/DELETE dengan pesan jelas (menyebut PRD §11.1). Dipasang pada `saldo_ledger` **dan** `mutasi_stok`.

## 4. Bukti Verifikasi (dijalankan 2026-10-02)

Diuji pada **PostgreSQL 18.6** (cluster sementara) + boot aplikasi:

| # | Uji | Hasil |
|---|---|---|
| 1 | Flyway apply V1–V4 saat boot | ✅ `Successfully applied 4 migrations` |
| 2 | 8 tabel domain terbentuk | ✅ |
| 3 | `UPDATE saldo_ledger` | ✅ DITOLAK trigger |
| 4 | `DELETE saldo_ledger` | ✅ DITOLAK trigger |
| 5 | `saldo_setelah` negatif | ✅ DITOLAK `ck_saldo_ledger_saldo_tidak_minus` |
| 6 | `nominal` = 0 | ✅ DITOLAK `ck_saldo_ledger_nominal_positif` |
| 7 | `arah` tidak valid | ✅ DITOLAK `ck_saldo_ledger_arah` |
| 8 | `stok_setelah` negatif | ✅ DITOLAK `ck_mutasi_stok_tidak_minus` |
| 9 | `idempotency_key` duplikat | ✅ DITOLAK `uq_saldo_ledger_idempotency_key` |
| 10 | **Row lock** `FOR UPDATE` | ✅ Sesi kedua menunggu **4098 ms** sampai commit |
| 11 | Aplikasi start + Tomcat | ✅ port 18085, 8.1 dtk |
| 12 | `/actuator/health/readiness` | ✅ `UP` |

## 5. Jebakan yang Ditemukan (penting untuk tim)

1. **Spring Boot 4 = Jackson 3.** `spring.jackson.serialization.write-dates-as-timestamps` **tidak ada lagi** → aplikasi **GAGAL START**. Jangan disalin dari `admin-be`. Paket berubah ke `tools.jackson`.
2. **MinIO di konstruktor = app gagal boot** bila kredensial kosong. `S3Storage` sudah dibuat *lazy*.
3. **`admin-be` mengirim JWT tanpa `sekolah_id`/`role`** (hanya `sub/typ/jti/iat/exp`) → resolusi tenant kantin **tidak bisa** mengandalkan claim; perlu endpoint/lookup (lihat OPEN-QUESTIONS Q1/Q2).
4. **`siswa` di admin-be tidak punya `sekolah_id`** → tenant lewat `siswa → kelas → sekolah_id`.
5. **`Constants.idGenerator()` admin-be = epoch_ms + random(100–999)** → berisiko **tabrakan PK** pada trafik tinggi (tap bersamaan). Untuk ledger, pertimbangkan ID berbasis sequence/ULID atau retry-on-conflict. **Perlu keputusan tim.**
