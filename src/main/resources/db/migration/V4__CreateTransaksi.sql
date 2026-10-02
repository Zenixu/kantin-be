-- ============================================================
-- V4 — Transaksi Kasir, Item, Titik Kasir & Sesi Kasir
--
-- Fase 4 · PRD §6.2, §6.3, §6.4, §6.6, §11.3
--
-- Catatan:
--   * `transaksi.id` dibuat oleh KLIEN kasir (PRD §6.2) — bukan BIGSERIAL.
--   * `idempotency_key` UNIQUE: tap ganda / retry jaringan tidak boleh
--     memotong saldo & stok dua kali.
--   * Item menyimpan SNAPSHOT (nama, harga jual, kategori, HPP) agar laba
--     lama tidak berubah ketika harga/HPP diubah kemudian (PRD §6.2, §7.4).
--   * `transaksi` BUKAN ledger — void boleh meng-UPDATE status di sini,
--     tetapi TIDAK pernah mengubah/menghapus baris saldo_ledger / mutasi_stok
--     (kompensasi dilakukan dengan mutasi pembalik baru).
-- ============================================================

-- ============================================================
-- titik_kasir — satu perangkat kasir (PRD §6.6)
-- ============================================================
CREATE TABLE IF NOT EXISTS titik_kasir (
    id          BIGINT PRIMARY KEY,
    sekolah_id  BIGINT       NOT NULL,
    nama        VARCHAR(100) NOT NULL,
    kode        VARCHAR(30),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_titik_kasir_kode UNIQUE (sekolah_id, kode)
);

CREATE INDEX IF NOT EXISTS idx_titik_kasir_sekolah
    ON titik_kasir (sekolah_id);

COMMENT ON TABLE titik_kasir IS
    'Perangkat kasir. Satu kantin bisa punya lebih dari satu titik (PRD 6.6).';

-- ============================================================
-- sesi_kasir — periode transaksi per titik per hari (PRD §6.4)
-- ============================================================
CREATE TABLE IF NOT EXISTS sesi_kasir (
    id                 BIGINT PRIMARY KEY,
    sekolah_id         BIGINT       NOT NULL,
    titik_kasir_id     BIGINT       NOT NULL,
    tanggal            DATE         NOT NULL,      -- zona waktu sekolah
    status             VARCHAR(15)  NOT NULL DEFAULT 'TERBUKA', -- TERBUKA | DITUTUP
    total_bruto        BIGINT       NOT NULL DEFAULT 0,
    total_void         BIGINT       NOT NULL DEFAULT 0,
    total_bersih       BIGINT       NOT NULL DEFAULT 0,
    dibuka_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ditutup_at         TIMESTAMPTZ,
    ditutup_oleh       BIGINT,
    auto_tutup         BOOLEAN      NOT NULL DEFAULT FALSE,  -- ditutup otomatis (default 23:59)
    -- Posting Buku Kas: idempoten lewat referensi unik (INTEGRATIONS §3.4)
    posting_buku_kas   BOOLEAN      NOT NULL DEFAULT FALSE,
    referensi_buku_kas VARCHAR(64),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_sesi_kasir_status
        CHECK (status IN ('TERBUKA', 'DITUTUP')),
    CONSTRAINT ck_sesi_kasir_total_tidak_negatif
        CHECK (total_bruto >= 0 AND total_void >= 0 AND total_bersih >= 0),
    -- Satu titik kasir hanya boleh punya satu sesi terbuka per hari.
    CONSTRAINT uq_sesi_kasir_titik_tanggal UNIQUE (titik_kasir_id, tanggal),
    CONSTRAINT fk_sesi_kasir_titik FOREIGN KEY (titik_kasir_id)
        REFERENCES titik_kasir (id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_sesi_kasir_sekolah_tanggal
    ON sesi_kasir (sekolah_id, tanggal);

-- Cegah posting Buku Kas ganda untuk sesi yang sama.
CREATE UNIQUE INDEX IF NOT EXISTS uq_sesi_kasir_referensi_buku_kas
    ON sesi_kasir (referensi_buku_kas) WHERE referensi_buku_kas IS NOT NULL;

COMMENT ON TABLE sesi_kasir IS
    'Sesi kasir per titik per hari. Total bersih diposting ke Buku Kas saat tutup (PRD 6.4, 5.1).';

-- ============================================================
-- transaksi — satu transaksi kasir (PRD §6.2)
-- ============================================================
CREATE TABLE IF NOT EXISTS transaksi (
    id               BIGINT PRIMARY KEY,          -- ID dari klien kasir
    idempotency_key  VARCHAR(64)  NOT NULL,       -- UNIQUE (PRD §11.3)
    sekolah_id       BIGINT       NOT NULL,
    sesi_kasir_id    BIGINT       NOT NULL,
    titik_kasir_id   BIGINT       NOT NULL,
    subjek_tipe      VARCHAR(20)  NOT NULL,       -- SISWA | KARTU_TAMU
    subjek_id        BIGINT       NOT NULL,
    kartu_uid        VARCHAR(64),                 -- kartu fisik yang dipakai
    petugas_id       BIGINT       NOT NULL,       -- akun petugas (PRD §6.6)
    total            BIGINT       NOT NULL,       -- rupiah integer (PRD §11.6)
    total_hpp        BIGINT       NOT NULL DEFAULT 0, -- Σ HPP snapshot item (laba kotor)
    status           VARCHAR(10)  NOT NULL DEFAULT 'SUKSES', -- SUKSES | VOID
    alasan_void      VARCHAR(255),
    void_at          TIMESTAMPTZ,
    void_oleh        BIGINT,
    waktu            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_transaksi_subjek_tipe
        CHECK (subjek_tipe IN ('SISWA', 'KARTU_TAMU')),
    CONSTRAINT ck_transaksi_status
        CHECK (status IN ('SUKSES', 'VOID')),
    CONSTRAINT ck_transaksi_total_tidak_negatif
        CHECK (total >= 0 AND total_hpp >= 0),
    -- Void wajib beralasan (PRD §6.3).
    CONSTRAINT ck_transaksi_void_beralasan
        CHECK (status <> 'VOID' OR (alasan_void IS NOT NULL AND void_at IS NOT NULL)),
    CONSTRAINT uq_transaksi_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_transaksi_sesi FOREIGN KEY (sesi_kasir_id)
        REFERENCES sesi_kasir (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transaksi_titik FOREIGN KEY (titik_kasir_id)
        REFERENCES titik_kasir (id) ON DELETE RESTRICT
);

-- Idempotency & laporan.
CREATE INDEX IF NOT EXISTS idx_transaksi_sekolah_waktu
    ON transaksi (sekolah_id, waktu);
CREATE INDEX IF NOT EXISTS idx_transaksi_sesi
    ON transaksi (sesi_kasir_id, status);
CREATE INDEX IF NOT EXISTS idx_transaksi_subjek
    ON transaksi (sekolah_id, subjek_tipe, subjek_id, waktu);

COMMENT ON TABLE transaksi IS
    'Transaksi kasir. idempotency_key UNIQUE mencegah tap ganda memotong saldo dua kali (PRD 11.3).';
COMMENT ON COLUMN transaksi.total_hpp IS
    'Total HPP snapshot item saat transaksi — dipakai menghitung laba kotor (PRD 5).';

-- ============================================================
-- transaksi_item — rincian item per transaksi (snapshot)
-- ============================================================
CREATE TABLE IF NOT EXISTS transaksi_item (
    id            BIGINT PRIMARY KEY,
    transaksi_id  BIGINT       NOT NULL,
    menu_id       BIGINT       NOT NULL,
    nama_menu     VARCHAR(150) NOT NULL,   -- snapshot nama saat transaksi
    kategori_id   BIGINT,                  -- snapshot kategori saat transaksi
    harga_jual    BIGINT       NOT NULL,   -- snapshot harga jual per unit
    qty           INTEGER      NOT NULL,
    hpp_snapshot  BIGINT       NOT NULL,   -- HPP per unit saat transaksi (PRD §7.4)
    subtotal      BIGINT       NOT NULL,   -- harga_jual × qty
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_transaksi_item_qty_positif
        CHECK (qty > 0),
    CONSTRAINT ck_transaksi_item_harga_tidak_negatif
        CHECK (harga_jual >= 0 AND hpp_snapshot >= 0 AND subtotal >= 0),
    CONSTRAINT fk_transaksi_item_transaksi FOREIGN KEY (transaksi_id)
        REFERENCES transaksi (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_transaksi_item_transaksi
    ON transaksi_item (transaksi_id);

-- Penjualan per menu (laporan terlaris & rekonsiliasi stok).
CREATE INDEX IF NOT EXISTS idx_transaksi_item_menu
    ON transaksi_item (menu_id);

COMMENT ON TABLE transaksi_item IS
    'Rincian item transaksi dengan snapshot harga/HPP agar laba lama tidak berubah (PRD 6.2, 7.4).';
