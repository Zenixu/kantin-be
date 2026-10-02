# ADR-0003 — Ledger Append-Only + Locking Pessimistic

- **Status:** Diterima (2026-10-02) — diimplementasi: ledger append-only, `SELECT ... FOR UPDATE` pessimistic pada debit, isolasi tenant teruji (`IsolasiTenantLockIT`)
- **Tanggal:** 2026-10-02
- **Pengusul:** BE-2
- **Terkait:** PRD §11.1, §11.2, §11.3, Q12

## Konteks

Saldo dan stok adalah inti kepercayaan sistem. PRD mewajibkan: ledger append-only, saldo/stok tak boleh minus termasuk saat tap bersamaan dari dua titik kasir, dan transaksi idempoten.

## Keputusan

1. Saldo & stok disimpan sebagai **ledger append-only** (tabel `saldo_ledger` & `mutasi_stok`): **hanya INSERT**, tidak ada `UPDATE`/`DELETE`.
2. Saldo/stok berjalan adalah turunan (`SUM` mutasi) atau kolom cache yang selalu bisa dihitung ulang.
3. Pemotongan saldo + pengurangan stok + pencatatan transaksi = **satu transaksi DB**.
4. Debit saldo memakai **locking pessimistic** (`SELECT ... FOR UPDATE` / `@Lock(PESSIMISTIC_WRITE)`) pada baris saldo entitas (siswa/Kartu Tamu).
5. Transaksi kasir memakai **idempotency key** dari klien (kolom UNIQUE); retry dengan key sama mengembalikan hasil transaksi pertama.

## Alasan

- Append-only memberi jejak audit lengkap & bisa diaudit (PRD §11.1, §11.7).
- Locking pessimistic sederhana & pasti mencegah race pada hot row (saldo siswa saat jam istirahat).
- Idempotency key melindungi dari tap ganda & retry jaringan (PRD §11.3).

## Alternatif yang Dipertimbangkan

| Alternatif | Kenapa tidak dipilih |
|---|---|
| Optimistic locking (version) | Retry membingungkan di jalur tap; gagal di contention tinggi |
| Update kolom saldo langsung (tanpa ledger) | Melanggar §11.1, tak bisa diaudit |
| Menghitung saldo penuh tiap tap (tanpa cache) | Bisa lambat → ancam p95<1dtk; cache boleh, selama bisa dihitung ulang |

## Konsekuensi

**Positif:** konsistensi kuat, audit lengkap, aman dari race & double-spend.

**Negatif / risiko:** lock contention pada hot row saat istirahat → perlu uji beban; ledger tumbuh cepat → pertimbangkan **partitioning tabel** transaksi/mutasi.

## Tindak Lanjut

- [ ] Skema tabel `saldo_ledger`, `mutasi_stok`, `transaksi` (+ idempotency key UNIQUE)
- [ ] Test Testcontainers: race debit 2 thread, tap ganda, saldo tak minus
- [ ] Pertimbangkan partitioning bila volume besar
