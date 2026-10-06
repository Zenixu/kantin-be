-- ============================================================
-- V9 — Idempotency nomor bukti barang masuk (PRD §7.2, §11.3)
--
-- MASALAH (audit keamanan): endpoint POST /api/stok/barang-masuk menerima
-- `referensiId` (nomor bukti penerimaan) dan mendokumentasikannya sebagai
-- idempotency key — "input ulang tidak menggandakan stok". Namun TIDAK ADA
-- kunci unik maupun jalur replay di kode, sehingga klien yang retry (jaringan
-- timeout / double-tap) MENGGANDAKAN stok & nilai persediaan.
--
-- PERBAIKAN: kunci unik parsial per (sekolah, nomor bukti, menu). Menu ikut
-- kunci agar satu bukti penerimaan tetap boleh memuat beberapa baris item
-- (kiriman multi-item), sementara pengulangan baris yang sama ditolak.
--
-- Ledger tetap append-only: migrasi ini hanya menambah INDEX (tidak ada
-- UPDATE/DELETE baris). Replay di layer service memakai indeks ini sebagai
-- jaring terakhir saat dua request dengan bukti sama tiba bersamaan.
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS uq_mutasi_stok_barang_masuk_referensi
    ON mutasi_stok (sekolah_id, referensi_id, menu_id)
    WHERE jenis = 'BARANG_MASUK' AND referensi_id IS NOT NULL;

COMMENT ON INDEX uq_mutasi_stok_barang_masuk_referensi IS
    'Idempotency barang masuk: satu nomor bukti per (sekolah, menu). Retry tidak menggandakan stok (PRD 7.2, 11.3).';
