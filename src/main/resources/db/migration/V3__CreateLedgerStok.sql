-- ============================================================
-- V3 — Ledger Stok (append-only) + Cache Stok
--
-- Fase 4 · PRD §11.1, §11.2 · ADR-0003
--
-- Stok = turunan dari mutasi_stok. Stok minus mustahil (CHECK >= 0).
-- HPP disimpan sebagai snapshot per mutasi (PRD §7.4).
--
-- Catatan: `menu_id` merujuk tabel `menu` yang dibuat pada Fase 5.
-- FK sengaja BELUM dipasang di sini agar Fase 4 bisa berdiri sendiri;
-- FK akan ditambahkan pada migrasi Fase 5 setelah `menu` ada.
-- ============================================================

-- ============================================================
-- mutasi_stok — mutasi stok (append-only)
-- ============================================================
CREATE TABLE IF NOT EXISTS mutasi_stok (
    id              BIGINT PRIMARY KEY,
    sekolah_id      BIGINT       NOT NULL,
    menu_id         BIGINT       NOT NULL,
    arah            VARCHAR(10)  NOT NULL,   -- MASUK | KELUAR
    jenis           VARCHAR(40)  NOT NULL,   -- BARANG_MASUK, PENJUALAN, VOID_PENJUALAN,
                                             -- OPNAME_MASUK, OPNAME_KELUAR, BARANG_MASUK_PEMBALIK
    qty             INTEGER      NOT NULL,   -- selalu positif; arah menentukan tanda
    stok_setelah    INTEGER      NOT NULL,   -- stok setelah mutasi ini (audit)
    hpp_snapshot    BIGINT,                  -- HPP/unit saat mutasi (rupiah integer)
    transaksi_id    BIGINT,                  -- terisi untuk mutasi dari transaksi kasir
    referensi_tipe  VARCHAR(40),             -- BARANG_MASUK, OPNAME, TRANSAKSI, ...
    referensi_id    VARCHAR(64),
    alasan          VARCHAR(255),            -- wajib untuk penyesuaian/opname (PRD §7.3)
    aktor_id        BIGINT,
    waktu           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_mutasi_stok_arah
        CHECK (arah IN ('MASUK', 'KELUAR')),
    CONSTRAINT ck_mutasi_stok_qty_positif
        CHECK (qty > 0),
    -- Stok tidak boleh minus (PRD §11.2) — dijamin di level DB.
    CONSTRAINT ck_mutasi_stok_tidak_minus
        CHECK (stok_setelah >= 0)
);

-- Hitung stok berjalan per item.
CREATE INDEX IF NOT EXISTS idx_mutasi_stok_menu
    ON mutasi_stok (sekolah_id, menu_id, id);

CREATE INDEX IF NOT EXISTS idx_mutasi_stok_transaksi
    ON mutasi_stok (transaksi_id) WHERE transaksi_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_mutasi_stok_waktu
    ON mutasi_stok (sekolah_id, waktu);

-- Trigger: tolak UPDATE & DELETE (append-only).
DROP TRIGGER IF EXISTS trg_mutasi_stok_append_only ON mutasi_stok;
CREATE TRIGGER trg_mutasi_stok_append_only
    BEFORE UPDATE OR DELETE ON mutasi_stok
    FOR EACH ROW EXECUTE FUNCTION tolak_perubahan_ledger();

COMMENT ON TABLE  mutasi_stok IS
    'Ledger stok append-only (PRD 11.1). Sumber kebenaran stok; hanya INSERT.';
COMMENT ON COLUMN mutasi_stok.hpp_snapshot IS
    'HPP/unit saat mutasi (PRD 7.4). Void memakai snapshot ini untuk mengembalikan stok.';
COMMENT ON COLUMN mutasi_stok.stok_setelah IS
    'Snapshot stok setelah mutasi, untuk audit & deteksi drift.';

-- ============================================================
-- stok_cache — stok berjalan per menu (turunan, bisa dihitung ulang)
-- ============================================================
-- Baris ini dikunci (FOR UPDATE) saat penjualan agar dua kasir tidak
-- bisa menjual stok yang sama secara bersamaan.
CREATE TABLE IF NOT EXISTS stok_cache (
    menu_id     BIGINT       NOT NULL,
    sekolah_id  BIGINT       NOT NULL,
    stok        INTEGER      NOT NULL DEFAULT 0,
    hpp         BIGINT       NOT NULL DEFAULT 0,   -- HPP rata-rata tertimbang berjalan
    stok_minimum INTEGER     NOT NULL DEFAULT 0,   -- ambang peringatan "stok menipis" (§7.5)
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_stok_cache PRIMARY KEY (menu_id),
    -- Jaring pengaman terakhir: stok minus mustahil (PRD §11.2).
    CONSTRAINT ck_stok_cache_tidak_minus
        CHECK (stok >= 0)
);

CREATE INDEX IF NOT EXISTS idx_stok_cache_sekolah
    ON stok_cache (sekolah_id);

COMMENT ON TABLE stok_cache IS
    'Cache stok & HPP berjalan per menu. Selalu dapat dihitung ulang dari mutasi_stok. Baris dikunci saat penjualan.';
