-- ============================================================
-- V18 — Pengaturan kantin per sekolah (PRD §9.1)
--
-- Menyimpan pengaturan modul kantin yang dikelola admin sekolah:
--   * nama kantin
--   * jam tutup kasir otomatis (dipakai penjadwal auto-close, §6.4)
--   * langkah konfirmasi manual (default nonaktif, §6.1)
--   * durasi tampil foto setelah transaksi (default 3 detik, §6.1)
--   * min/maks per top-up (§8.2)
--   * batas saldo maksimum per siswa & per Kartu Tamu (§8.2, §9.4)
--
-- Catatan:
--   * PK = sekolah_id: satu baris pengaturan per sekolah (tenant-scoped, §11.4).
--   * Bila belum ada baris, service memakai DEFAULT aman (tanpa menyimpan) —
--     sekolah tidak wajib mengisi apa pun agar sistem tetap jalan.
--   * Aktivasi modul & fee platform (§10) DIMILIKI internal-be (OPEN-QUESTIONS
--     Q6) — TIDAK diduplikasi di sini; kantin-be mengaksesnya lewat port
--     (AktivasiModulPort) dengan fallback fail-open.
-- ============================================================

CREATE TABLE IF NOT EXISTS sekolah_kantin_config (
    sekolah_id              BIGINT PRIMARY KEY,
    nama_kantin             VARCHAR(150),
    jam_tutup_otomatis      TIME         NOT NULL DEFAULT '23:59',
    konfirmasi_manual       BOOLEAN      NOT NULL DEFAULT FALSE,
    durasi_foto_detik       INTEGER      NOT NULL DEFAULT 3,
    min_topup               BIGINT,
    maks_topup              BIGINT,
    batas_saldo_siswa       BIGINT,
    batas_saldo_kartu_tamu  BIGINT,
    diperbarui_oleh         BIGINT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_config_durasi_foto CHECK (durasi_foto_detik >= 0),
    CONSTRAINT ck_config_min_topup CHECK (min_topup IS NULL OR min_topup >= 0),
    CONSTRAINT ck_config_maks_topup CHECK (maks_topup IS NULL OR maks_topup >= 0),
    CONSTRAINT ck_config_batas_saldo_siswa CHECK (batas_saldo_siswa IS NULL OR batas_saldo_siswa >= 0),
    CONSTRAINT ck_config_batas_saldo_kt CHECK (batas_saldo_kartu_tamu IS NULL OR batas_saldo_kartu_tamu >= 0),
    CONSTRAINT ck_config_topup_urut CHECK (min_topup IS NULL OR maks_topup IS NULL OR min_topup <= maks_topup)
);

COMMENT ON TABLE sekolah_kantin_config IS
    'Pengaturan kantin per sekolah (PRD 9.1). PK = sekolah_id (satu baris per sekolah).';
COMMENT ON COLUMN sekolah_kantin_config.jam_tutup_otomatis IS
    'Jam tutup kasir otomatis (zona sekolah) — dipakai penjadwal auto-close (PRD 6.4).';
COMMENT ON COLUMN sekolah_kantin_config.min_topup IS
    'Minimum per top-up (rupiah); NULL = tanpa batas minimum (PRD 8.2).';
COMMENT ON COLUMN sekolah_kantin_config.maks_topup IS
    'Maksimum per top-up (rupiah); NULL = tanpa batas maksimum (PRD 8.2).';
COMMENT ON COLUMN sekolah_kantin_config.batas_saldo_siswa IS
    'Batas saldo maksimum per siswa (rupiah); NULL = tanpa batas (PRD 8.2).';
COMMENT ON COLUMN sekolah_kantin_config.batas_saldo_kartu_tamu IS
    'Batas saldo maksimum per Kartu Tamu (rupiah); NULL = tanpa batas (PRD 9.4).';
