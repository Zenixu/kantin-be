-- ============================================================
-- V14 — Posting Buku Kas "Penyesuaian Kantin" untuk koreksi saldo (PRD §5.1, §9.2)
--
-- MASALAH: koreksi bendahara (`SaldoTopUpService.koreksi`) mengubah saldo sebagai
-- mutasi pembalik beralasan (PRD §9.2) dan WAJIB diposting sebagai entri
-- penyesuaian pos "Penyesuaian Kantin" ke Buku Kas (PRD §5.1) — bukan mengubah
-- entri lama. Namun tabel penanda `posting_buku_kas` (V13) menuntut
-- `mutasi_id NOT NULL` dan hanya dipakai untuk mutasi stok, sedangkan koreksi
-- saldo tidak punya baris `mutasi_stok`.
--
-- KENAPA ALTER (bukan tabel baru): `posting_buku_kas` adalah penanda posting
-- GENERIK — kunci idempotency sesungguhnya adalah (sekolah_id, referensi_id),
-- lihat `uq_posting_buku_kas_sekolah_referensi`. `mutasi_id` hanya untuk telusur
-- ke entitas sumber, yang bisa berupa `mutasi_stok` ATAU `saldo_ledger`. Karena
-- itu kolom dibuat nullable; `entitas` diisi 'KOREKSI_SALDO' untuk membedakan.
--
-- Tidak ada UPDATE/DELETE baris lama; hanya melonggarkan satu constraint.
-- ============================================================

ALTER TABLE posting_buku_kas
    ALTER COLUMN mutasi_id DROP NOT NULL;

COMMENT ON COLUMN posting_buku_kas.mutasi_id IS
    'ID entitas sumber (mutasi_stok atau saldo_ledger) — telusur. NULL bila sumber tidak ber-ID mutasi stok (mis. koreksi saldo).';
COMMENT ON COLUMN posting_buku_kas.entitas IS
    'Jenis sumber posting: BARANG_MASUK | BARANG_MASUK_PEMBALIK | KOREKSI_SALDO.';
