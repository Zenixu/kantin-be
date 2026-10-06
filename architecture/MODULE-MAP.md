# 🗺️ MODULE-MAP.md — Peta Modul & Urutan Pengerjaan

> Dari PRD → kode. Setiap modul dipetakan ke folder & ketergantungan. **Kerjakan dari fondasi, bukan dari fitur paling menarik.**

---

## 1. Urutan Fase (rekomendasi)

```
FASE 0  Fondasi Repo        →  git, docs, .gitignore, scaffold               [tidak ada blocker]
FASE 1  Scaffold Spring     →  pom.xml, struktur, Docker, compose            [tidak ada blocker]
FASE 2  Infra Dev Lokal     →  PostgreSQL+Redis, application-local           [tidak ada blocker]
FASE 3  Auth & Tenant       →  JWT RS256, TenantContext, SekolahGuard, RBAC  [🔴 blokir: Q1,Q2,Q7]
FASE 4  Ledger Inti         →  saldo_ledger, mutasi_stok, locking, test      [tidak ada blocker]
FASE 5  Katalog & Stok/HPP  →  menu, kategori, barang masuk, opname          [tidak ada blocker]
FASE 6  Kasir (tap/void)    →  validasi 6 tahap, transaksi, sesi, tutup      [🔴 blokir: Q7 (lookup kartu)]
FASE 7  Saldo & Kartu Tamu  →  top-up tunai, refund, limit, blokir, kartu    [🔴 blokir: Q4 (topup online)]
FASE 8  Integrasi & Laporan →  BukuKasClient, notifikasi, laporan Excel      [🔴 blokir: Q3,Q5]
FASE 9  Aktivasi & Fee      →  toggle modul, fee platform                    [🟡 Q6]
FASE 10 Hardening & UAT     →  audit, performa, keamanan, DoD                —
```

**Strategi:** sementara Q1–Q7 🔴 belum terjawab, tim bisa mengerjakan **Fase 0,1,2,4,5** yang tidak terblokir.

---

## 2. Peta Modul → Folder → PRD

| Modul | PRD § | Folder | Model/Tabel | Catatan |
|---|---|---|---|---|
| Fondasi/Auth | §4 | `config/security`, `security` | — | 2 decoder JWT RS256 |
| Tenant & RBAC | §11.4, §9.6 | `security` | — | 404 untuk sekolah lain |
| Ledger saldo | §11.1 | `service/kasir`, `model` | `saldo_ledger` | append-only |
| Ledger stok | §11.1, §7.4 | `service/stok` | `mutasi_stok` | append-only |
| Kasir / tap | §6.1–6.2 | `service/kasir` | `transaksi`, `transaksi_item` | idempoten, atomik |
| Sesi & tutup kasir | §6.4 | `service/kasir` | `sesi_kasir`, `titik_kasir` | posting Buku Kas |
| Void | §6.3 | `service/kasir` | `transaksi` (status) | + audit |
| Katalog menu | §7.1 | `service/katalog` | `menu`, `kategori` | soft delete |
| Barang masuk | §7.2 | `service/stok` | `barang_masuk`, `barang_masuk_pembalik` | update HPP |
| Stok opname | §7.3 | `service/stok` | `stok_opname`, `penyesuaian_stok` | alasan wajib |
| HPP | §7.4 | `service/kasir` (HppService) | — | rata-rata tertimbang |
| Top-up tunai | §9.2 | `service/saldo` | `topup`, `setoran_tu` | + bukti bernomor |
| Top-up online (webhook) | §8.2 | `service/webhook`, `service/saldo` | `webhook_event` | HMAC + idempoten per `refId` PG |
| Refund/pindah | §9.3 | `service/saldo` | `refund`, `pindah-saldo` | + audit; saldo→0 |
| Koreksi | §9.2 | `service/saldo` | mutasi pembalik | + audit |
| Kartu Tamu | §9.4 | `service/kartu` | `kartu_tamu` | saldo ikut nomor |
| Blokir kartu | §6.1, §11.11 | `service/kartu` | `blokir_kartu` | **tanpa cache** |
| Limit & blokir item | §8.3 | `service/kartu` | `limit_harian`, `blokir_item` | diperiksa tiap tap |
| Kontrol atas nama ortu | §8.6 | `service/kartu` | (pakai limit/blokir) | + audit |
| Laporan | §9.5 | `service/laporan` | — (query) | ekspor POI ✅ (Excel; PDF menunggu Q3/Q5) |
| Buku Kas integrasi | §5.1 | `service/integrasi` | — | idempoten |
| Notifikasi | §8.4 | `service/integrasi` | — | ke mobile-be |
| Aktivasi & fee | §10 | `service/aktivasi` | `aktivasi_modul`, `fee_platform` | internal-be |
| Audit log | §11.7 | `service/audit` | `audit_log` | lintas modul |
| Pengaturan kantin | §9.1 | `service/aktivasi` | `sekolah_kantin_config` | jam, durasi foto, dll |

---

## 3. Ketergantungan Antar-Modul

```
Auth/Tenant ──┬─→ semua modul (wajib duluan)
              │
Ledger ───────┼─→ Kasir ──→ Void
              │      │
              │      └─→ Sesi/Tutup Kasir ──→ Buku Kas integrasi
              │
Katalog ──────┼─→ Barang Masuk ──→ HPP ──→ Kasir
              │
Kartu Tamu ───┼─→ Kasir (validasi)
Blokir/Limit ─┘
```

**Aturan:** modul hilir tidak boleh dikerjakan sebelum modul hulunya stabil + ter-test.

---

## 4. Titik Kritis (jangan dianggap remeh)

| Titik | Mengapa kritis | Mitigasi |
|---|---|---|
| **Locking ledger** | Saldo minus saat tap bersamaan = fatal | `FOR UPDATE` + Testcontainers race test |
| **Idempotency tap** | Tap ganda/retry potong 2× | idempotency key UNIQUE dari klien |
| **Blokir instan** | Jeda = kartu blokir tetap bisa dipakai | cek server tiap tap, **tanpa cache TTL** |
| **Tenant scoping** | Kebocoran data antar sekolah | guard + 404, uji lintas-sekolah |
| **Lookup kartu latency** | p95<1dtk | cache aman (kecuali status blokir) + index `rfid_uid` |
| **Posting Buku Kas ganda** | Buku Kas tak idempoten | cek `existsByReferensiId` sebelum posting |
