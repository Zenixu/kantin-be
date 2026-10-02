-- ============================================================
-- V6 — Katalog Menu & Kategori (Fase 5 · PRD §7.1)
--
-- Catatan desain:
--   * Soft delete: kolom `is_active`. Item/kategori yang dinonaktifkan tidak
--     menghapus riwayat transaksi (transaksi menyimpan SNAPSHOT, PRD §6.2, §7.1).
--   * `menu.menu_id` BUKAN BIGSERIAL — id dibuat aplikasi (IdGenerator sortable)
--     agar seragam dengan titik_kasir/sesi_kasir/transaksi.
--   * Mengubah `harga_jual` TIDAK mengubah transaksi lama (snapshot) dan wajib
--     tercatat di audit log (dilakukan di layer service, PRD §7.1, §11.7).
--   * `stok_minimum` = ambang peringatan restock (dipakai StokController.menipis).
--   * Kategori yang masih dipakai item hanya boleh DINONAKTIFKAN (ditegakkan
--     di service), bukan dihapus.
-- ============================================================

-- ============================================================
-- kategori_menu — kategori item (mis. Makanan Berat, Snack, Minuman)
-- ============================================================
CREATE TABLE IF NOT EXISTS kategori_menu (
    id          BIGINT PRIMARY KEY,
    sekolah_id  BIGINT       NOT NULL,
    nama        VARCHAR(100) NOT NULL,
    urutan      INTEGER      NOT NULL DEFAULT 0,   -- urutan tampil di kasir
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Nama kategori unik per sekolah (case-sensitive; normalisasi di service).
    CONSTRAINT uq_kategori_menu_sekolah_nama UNIQUE (sekolah_id, nama)
);

CREATE INDEX IF NOT EXISTS idx_kategori_menu_sekolah
    ON kategori_menu (sekolah_id, is_active, urutan);

COMMENT ON TABLE kategori_menu IS
    'Kategori item katalog per sekolah. Jadi dasar blokir per kategori oleh ortu (PRD 7.1, 8.3).';
COMMENT ON COLUMN kategori_menu.is_active IS
    'Soft delete: FALSE = dinonaktifkan, riwayat tetap ada (PRD 7.1).';

-- ============================================================
-- menu — item katalog (PRD §7.1)
-- ============================================================
CREATE TABLE IF NOT EXISTS menu (
    id            BIGINT PRIMARY KEY,
    sekolah_id    BIGINT       NOT NULL,
    kategori_id   BIGINT,
    nama          VARCHAR(150) NOT NULL,
    harga_jual    BIGINT       NOT NULL,           -- rupiah integer (PRD §11.6)
    satuan        VARCHAR(20)  NOT NULL DEFAULT 'PCS', -- PCS | PORSI | BOTOL
    foto_url      VARCHAR(500),
    stok_minimum  INTEGER      NOT NULL DEFAULT 0, -- ambang peringatan (PRD §7.1)
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_menu_harga_tidak_negatif CHECK (harga_jual >= 0),
    CONSTRAINT ck_menu_stok_minimum_tidak_negatif CHECK (stok_minimum >= 0),
    CONSTRAINT ck_menu_satuan CHECK (satuan IN ('PCS', 'PORSI', 'BOTOL')),
    -- ON DELETE RESTRICT: kategori yang dipakai item tak boleh dihapus keras.
    -- Nonaktifkan kategori (is_active=FALSE) bila masih dipakai (PRD §7.1).
    CONSTRAINT fk_menu_kategori FOREIGN KEY (kategori_id)
        REFERENCES kategori_menu (id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_menu_sekolah_aktif
    ON menu (sekolah_id, is_active);
CREATE INDEX IF NOT EXISTS idx_menu_kategori
    ON menu (kategori_id);

COMMENT ON TABLE menu IS
    'Item katalog kantin. Harga jual rupiah integer; perubahan harga membuat snapshot transaksi lama tetap (PRD 7.1, 11.6).';
COMMENT ON COLUMN menu.stok_minimum IS
    'Ambang peringatan restock — stok <= nilai ini dianggap menipis (PRD 7.1, 7.5).';
COMMENT ON COLUMN menu.is_active IS
    'Soft delete. Menu nonaktif tidak boleh dijual (PRD 7.1).';
