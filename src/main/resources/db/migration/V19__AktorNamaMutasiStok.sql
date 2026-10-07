-- ============================================================
-- V19 — Snapshot nama aktor pada ledger stok
-- Issue #99 (FE-STOK): kolom "Aktor" pada Kartu Stok.
--
-- Catatan:
--   * Nama aktor TIDAK bisa di-lookup belakangan (kantin-be tak punya tabel
--     user — ADR-0002); nama hanya ada sesaat di klaim JWT. Karena itu nama
--     di-SNAPSHOT ke baris ledger saat mutasi dicatat.
--   * Nullable: baris lama & aksi sistem/scheduler → NULL (FE tampilkan "Sistem").
--   * Ledger tetap APPEND-ONLY: ADD COLUMN tidak memicu trigger
--     trg_mutasi_stok_append_only (trigger hanya pada INSERT/UPDATE/DELETE baris).
-- ============================================================

ALTER TABLE mutasi_stok
    ADD COLUMN IF NOT EXISTS aktor_nama VARCHAR(150);

COMMENT ON COLUMN mutasi_stok.aktor_nama IS
    'Snapshot nama aktor saat mutasi dicatat (klaim JWT nama). NULL = aksi sistem/baris lama.';
