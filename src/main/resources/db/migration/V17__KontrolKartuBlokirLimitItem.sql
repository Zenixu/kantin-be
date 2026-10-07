-- ============================================================
-- V17 — Kontrol kartu: blokir kartu, limit harian & blokir item
-- PRD §6.1 (validasi tap tahap 2/3/5), §8.3 (kontrol ortu), §11.7, §11.11
--
-- Catatan:
--   * Kontrol bersifat TENANT-SCOPED (sekolah_id) — PRD §11.4.
--   * Status blokir WAJIB instan / TANPA cache (PRD §11.11): dibaca server
--     setiap tap, sehingga tabel ini menjadi sumber kebenaran sisi kantin-be.
--   * Kunci kontrol = (sekolah_id, subjek_tipe, subjek_id). Kontrol melekat ke
--     SISWA (saldo terikat siswa — PRD §8.3) atau KARTU_TAMU (§9.4).
--   * Kartu Tamu tidak punya limit harian / blokir item (PRD §9.4) — ditegakkan
--     di layer service (pesan ramah), bukan CHECK.
-- ============================================================

CREATE TABLE IF NOT EXISTS blokir_kartu (
    id           BIGINT PRIMARY KEY,
    sekolah_id   BIGINT       NOT NULL,
    subjek_tipe  VARCHAR(20)  NOT NULL,
    subjek_id    BIGINT       NOT NULL,
    diblokir     BOOLEAN      NOT NULL DEFAULT TRUE,
    alasan       VARCHAR(500),
    diubah_oleh  BIGINT,
    diubah_pada  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_blokir_kartu_subjek CHECK (subjek_tipe IN ('SISWA', 'KARTU_TAMU')),
    CONSTRAINT uq_blokir_kartu_subjek UNIQUE (sekolah_id, subjek_tipe, subjek_id)
);

CREATE INDEX IF NOT EXISTS idx_blokir_kartu_sekolah ON blokir_kartu (sekolah_id);

COMMENT ON TABLE blokir_kartu IS
    'Status blokir kartu per subjek (siswa/kartu tamu). Berlaku INSTAN tanpa cache (PRD 8.3, 11.11).';

CREATE TABLE IF NOT EXISTS limit_harian (
    id           BIGINT PRIMARY KEY,
    sekolah_id   BIGINT       NOT NULL,
    subjek_tipe  VARCHAR(20)  NOT NULL,
    subjek_id    BIGINT       NOT NULL,
    nominal      BIGINT,
    diubah_oleh  BIGINT,
    diubah_pada  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_limit_harian_subjek CHECK (subjek_tipe IN ('SISWA', 'KARTU_TAMU')),
    CONSTRAINT ck_limit_harian_nominal CHECK (nominal IS NULL OR nominal >= 0),
    CONSTRAINT uq_limit_harian_subjek UNIQUE (sekolah_id, subjek_tipe, subjek_id)
);

CREATE INDEX IF NOT EXISTS idx_limit_harian_sekolah ON limit_harian (sekolah_id);

COMMENT ON TABLE limit_harian IS
    'Limit belanja harian per subjek. nominal NULL = tanpa limit. Reset 00:00 zona sekolah (PRD 8.3, 11.9).';

CREATE TABLE IF NOT EXISTS blokir_item (
    id           BIGINT PRIMARY KEY,
    sekolah_id   BIGINT       NOT NULL,
    subjek_tipe  VARCHAR(20)  NOT NULL,
    subjek_id    BIGINT       NOT NULL,
    menu_id      BIGINT,
    kategori_id  BIGINT,
    diblokir     BOOLEAN      NOT NULL DEFAULT TRUE,
    diubah_oleh  BIGINT,
    diubah_pada  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_blokir_item_subjek CHECK (subjek_tipe IN ('SISWA', 'KARTU_TAMU')),
    CONSTRAINT ck_blokir_item_target CHECK ((menu_id IS NOT NULL) <> (kategori_id IS NOT NULL))
);

-- Satu subjek hanya boleh punya satu baris blokir per menu / per kategori.
CREATE UNIQUE INDEX IF NOT EXISTS uq_blokir_item_menu
    ON blokir_item (sekolah_id, subjek_tipe, subjek_id, menu_id) WHERE menu_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_blokir_item_kategori
    ON blokir_item (sekolah_id, subjek_tipe, subjek_id, kategori_id) WHERE kategori_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_blokir_item_sekolah ON blokir_item (sekolah_id);

COMMENT ON TABLE blokir_item IS
    'Blokir item/kategori oleh ortu (PRD 8.3). Tepat satu dari menu_id/kategori_id terisi.';
