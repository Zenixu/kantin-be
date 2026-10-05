-- V7__CreateKartuTamu.sql
-- Kartu Tamu: kartu RFID untuk non-siswa (guru/staf/tamu) — PRD §9.4
-- Saldo ikut nomor kartu, bukan orang. Diisi tunai di TU.

CREATE TABLE kartu_tamu (
    -- PK
    id BIGINT PRIMARY KEY,

    -- Tenant
    sekolah_id BIGINT NOT NULL,

    -- Identitas kartu
    nomor_kartu VARCHAR(20) NOT NULL,  -- KT-001, KT-002, dll (human-readable)
    rfid_uid VARCHAR(50),               -- UID RFID (nullable: kartu belum di-bind)

    -- Status
    aktif BOOLEAN NOT NULL DEFAULT true,
    catatan TEXT,                       -- Mis: "Untuk guru matematika", "Kartu tamu umum", dll

    -- Audit
    dibuat_oleh BIGINT NOT NULL,
    dibuat_pada TIMESTAMP NOT NULL DEFAULT NOW(),
    diubah_oleh BIGINT,
    diubah_pada TIMESTAMP,

    -- Constraints
    CONSTRAINT uk_kartu_tamu_sekolah_nomor UNIQUE (sekolah_id, nomor_kartu),
    CONSTRAINT uk_kartu_tamu_rfid_uid UNIQUE (rfid_uid)
);

-- Index untuk lookup tap (by RFID UID)
CREATE INDEX idx_kartu_tamu_rfid_uid ON kartu_tamu(rfid_uid) WHERE rfid_uid IS NOT NULL AND aktif = true;

-- Index untuk tenant scoping
CREATE INDEX idx_kartu_tamu_sekolah ON kartu_tamu(sekolah_id);

COMMENT ON TABLE kartu_tamu IS 'Kartu RFID untuk non-siswa (guru/staf/tamu). Saldo ikut nomor kartu.';
COMMENT ON COLUMN kartu_tamu.nomor_kartu IS 'Nomor kartu human-readable (KT-001, KT-002, dll)';
COMMENT ON COLUMN kartu_tamu.rfid_uid IS 'UID RFID kartu (nullable jika belum di-bind). UNIQUE global untuk anti-tabrakan dengan siswa.';
COMMENT ON COLUMN kartu_tamu.aktif IS 'Kartu nonaktif tidak bisa dipakai tap (soft delete)';
COMMENT ON COLUMN kartu_tamu.catatan IS 'Catatan bebas (untuk siapa, keperluan apa, dll)';
