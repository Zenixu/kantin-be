-- ============================================================
-- V21 — Transaksi menunggu konfirmasi manual (PRD §6.1, §9.1)
--
-- Bila sekolah mengaktifkan langkah "konfirmasi manual", tap TIDAK langsung
-- memotong saldo/stok. Sebagai gantinya dibuat baris pending di sini; petugas
-- menekan "Konfirmasi" untuk mengeksekusi commit (atau membatalkannya).
--
-- Catatan:
--   * PK = id (dibuat aplikasi via IdGenerator).
--   * idempotency_key UNIQUE per sekolah — tap ganda dengan key sama hanya
--     membuat satu baris pending (PRD §11.3).
--   * items_json = snapshot item {menuId,qty} untuk divalidasi ulang saat
--     konfirmasi (stok/saldo bisa berubah antara tap & konfirmasi).
--   * TIDAK menyentuh saldo_ledger/mutasi_stok sampai dikonfirmasi — pending
--     bukan ledger.
-- ============================================================

CREATE TABLE IF NOT EXISTS transaksi_menunggu_konfirmasi (
    id                BIGINT PRIMARY KEY,
    sekolah_id        BIGINT       NOT NULL,
    idempotency_key   VARCHAR(64)  NOT NULL,
    rfid_uid          VARCHAR(64)  NOT NULL,
    titik_kasir_id    BIGINT       NOT NULL,
    subjek_tipe       VARCHAR(20),
    subjek_id         BIGINT,
    petugas_id        BIGINT       NOT NULL,
    total             BIGINT       NOT NULL DEFAULT 0,
    items_json        TEXT         NOT NULL,
    status            VARCHAR(15)  NOT NULL DEFAULT 'MENUNGGU',
    transaksi_id      BIGINT,
    dibuat_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    kedaluwarsa_at    TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_pending_sekolah_key UNIQUE (sekolah_id, idempotency_key),
    CONSTRAINT ck_pending_status CHECK (status IN ('MENUNGGU', 'DIKONFIRMASI', 'DIBATALKAN'))
);

CREATE INDEX idx_pending_sekolah_status
    ON transaksi_menunggu_konfirmasi (sekolah_id, status);

COMMENT ON TABLE transaksi_menunggu_konfirmasi IS
    'Tap yang menunggu konfirmasi manual petugas (PRD 6.1/9.1). Bukan ledger.';
COMMENT ON COLUMN transaksi_menunggu_konfirmasi.items_json IS
    'Snapshot item {menuId,qty} untuk divalidasi ulang saat konfirmasi.';
COMMENT ON COLUMN transaksi_menunggu_konfirmasi.status IS
    'MENUNGGU | DIKONFIRMASI (sudah jadi transaksi) | DIBATALKAN.';
