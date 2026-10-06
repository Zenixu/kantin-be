-- ============================================================
-- V11 — Batch opname: idempotency per berita acara (PRD §7.3)
--
-- Fitur baru: POST /api/stok/opname-batch menyesuaikan BANYAK menu dalam
-- SATU transaksi (all-or-nothing). Satu `referensiId` (nomor berita acara,
-- mis. "OPN-20261006-001") berlaku untuk seluruh item batch.
--
-- Idempotency: retry batch dengan nomor berita acara yang sama tidak boleh
-- menerapkan penyesuaian dua kali. Ditegakkan UNIQUE parsial
-- (sekolah, referensi, menu) khusus baris ber-referensi_tipe 'OPNAME_BATCH'.
--
-- Ledger tetap append-only: migrasi ini HANYA menambah INDEX (tidak ada
-- UPDATE/DELETE baris). `jenis` tidak punya CHECK constraint di skema, jadi
-- nilai baru BARANG_RUSAK tidak butuh perubahan kolom.
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS uq_mutasi_stok_opname_batch_referensi
    ON mutasi_stok (sekolah_id, referensi_id, menu_id)
    WHERE referensi_tipe = 'OPNAME_BATCH' AND referensi_id IS NOT NULL;

COMMENT ON INDEX uq_mutasi_stok_opname_batch_referensi IS
    'Idempotency batch opname: satu nomor berita acara per (sekolah, menu). Retry tidak menerapkan penyesuaian dua kali (PRD 7.3, 11.3).';
