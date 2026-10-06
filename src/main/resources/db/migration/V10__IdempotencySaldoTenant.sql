-- ============================================================
-- V10 — Idempotency saldo tenant-scoped (PRD §11.3, §11.4)
--
-- MASALAH (audit keamanan): `saldo_ledger.idempotency_key` UNIQUE GLOBAL dan
-- pencarian replay (`findByIdempotencyKey`) juga global — tanpa `sekolah_id`.
-- Akibatnya dua sekolah yang memakai nomor bukti sama (mis. format berulang
-- "TU-2026-0001" yang dibuat TU tiap sekolah) saling menelan:
--   * top-up sekolah B dianggap "replay" milik sekolah A → saldo B TIDAK
--     bertambah, dan
--   * respons ke sekolah B memuat saldo milik sekolah A (bocor lintas-tenant).
--
-- PERBAIKAN: jadikan idempotency unik per (sekolah, key) dan ubah pencarian
-- replay menjadi tenant-scoped. Nomor bukti kini boleh berulang antar sekolah,
-- tetapi tetap mencegah dobel di dalam satu sekolah.
--
-- Ledger tetap append-only: hanya mengubah INDEX (tidak ada UPDATE/DELETE baris).
-- ============================================================

DROP INDEX IF EXISTS uq_saldo_ledger_idempotency_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_saldo_ledger_sekolah_idempotency
    ON saldo_ledger (sekolah_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

COMMENT ON INDEX uq_saldo_ledger_sekolah_idempotency IS
    'Idempotency saldo per tenant: satu key per sekolah. Retry tidak memotong dua kali; nomor bukti boleh sama antar sekolah (PRD 11.3, 11.4).';
