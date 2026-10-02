-- ============================================================
-- V1 — Baseline kantin-be
--
-- Fase 2 (infra). Tabel domain (ledger, transaksi, menu, stok,
-- dsb.) dimulai pada migrasi Fase 4 — lihat architecture/MODULE-MAP.md.
--
-- Konvensi: setiap perubahan skema = file migrasi BARU, idempoten bila
-- memungkinkan, tidak pernah mengedit migrasi yang sudah di-commit
-- (CONVENTIONS.md §8).
-- ============================================================

-- pgcrypto: gen_random_uuid() untuk idempotency key & referensi acak.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
