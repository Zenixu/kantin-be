-- ============================================================
-- V2 — Ledger Saldo (append-only) + Cache Saldo
--
-- Fase 4 · PRD §11.1, §11.2, §11.3 · ADR-0003
--
-- Prinsip:
--   * `saldo_ledger`  = sumber kebenaran, HANYA INSERT (dijaga trigger).
--   * `saldo_cache`   = turunan yang selalu bisa dihitung ulang
--                       (SUM mutasi). Dipakai sebagai baris kunci
--                       (SELECT ... FOR UPDATE) saat debit.
--   * Saldo minus MUSTAHIL: dijaga CHECK (saldo >= 0) di DB.
--   * Uang = BIGINT rupiah integer (CONVENTIONS §4). Bukan float/BigDecimal.
--
-- Catatan: saldo_ledger TIDAK memakai FK ke `siswa` karena `siswa` milik
-- admin-be (DB terpisah) — lihat ADR-0004. Integritas lintas sistem dijaga
-- di layer service lewat lookup API, bukan FK.
-- ============================================================

-- ---------- Fungsi penjaga append-only (dipakai ulang tabel lain) ----------
CREATE OR REPLACE FUNCTION tolak_perubahan_ledger() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Ledger append-only: operasi % ditolak pada tabel % (PRD 11.1)',
        TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- saldo_ledger — mutasi saldo (append-only)
-- ============================================================
CREATE TABLE IF NOT EXISTS saldo_ledger (
    id               BIGINT PRIMARY KEY,
    sekolah_id       BIGINT       NOT NULL,
    subjek_tipe      VARCHAR(20)  NOT NULL,   -- SISWA | KARTU_TAMU
    subjek_id        BIGINT       NOT NULL,   -- id siswa / id kartu_tamu
    arah             VARCHAR(10)  NOT NULL,   -- KREDIT (nambah) | DEBIT (kurang)
    jenis            VARCHAR(40)  NOT NULL,   -- TOPUP_TUNAI, PENJUALAN, VOID_PENJUALAN, ...
    nominal          BIGINT       NOT NULL,   -- selalu positif; arah menentukan tanda
    saldo_setelah    BIGINT       NOT NULL,   -- saldo setelah mutasi ini (audit)
    transaksi_id     BIGINT,                  -- terisi untuk mutasi dari transaksi kasir
    idempotency_key  VARCHAR(64),             -- UNIQUE: cegah tap/retry ganda
    referensi_tipe   VARCHAR(40),             -- TOPUP, TRANSAKSI, OPNAME, ...
    referensi_id     VARCHAR(64),
    keterangan       VARCHAR(255),
    aktor_id         BIGINT,                  -- user yang memicu (audit)
    waktu            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_saldo_ledger_subjek_tipe
        CHECK (subjek_tipe IN ('SISWA', 'KARTU_TAMU')),
    CONSTRAINT ck_saldo_ledger_arah
        CHECK (arah IN ('KREDIT', 'DEBIT')),
    CONSTRAINT ck_saldo_ledger_nominal_positif
        CHECK (nominal > 0),
    -- Saldo tidak boleh minus (PRD §11.2) — dijamin di level DB.
    CONSTRAINT ck_saldo_ledger_saldo_tidak_minus
        CHECK (saldo_setelah >= 0)
);

-- Idempotency: satu key hanya boleh menghasilkan satu mutasi.
CREATE UNIQUE INDEX IF NOT EXISTS uq_saldo_ledger_idempotency_key
    ON saldo_ledger (idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Query saldo berjalan: SUM per subjek, urut id.
CREATE INDEX IF NOT EXISTS idx_saldo_ledger_subjek
    ON saldo_ledger (sekolah_id, subjek_tipe, subjek_id, id);

CREATE INDEX IF NOT EXISTS idx_saldo_ledger_transaksi
    ON saldo_ledger (transaksi_id) WHERE transaksi_id IS NOT NULL;

-- Laporan harian / rekonsiliasi.
CREATE INDEX IF NOT EXISTS idx_saldo_ledger_waktu
    ON saldo_ledger (sekolah_id, waktu);

-- Batas belanja harian (limit) menghitung DEBIT per subjek per hari.
CREATE INDEX IF NOT EXISTS idx_saldo_ledger_debit_harian
    ON saldo_ledger (sekolah_id, subjek_tipe, subjek_id, waktu)
    WHERE arah = 'DEBIT';

-- Trigger: tolak UPDATE & DELETE (append-only).
DROP TRIGGER IF EXISTS trg_saldo_ledger_append_only ON saldo_ledger;
CREATE TRIGGER trg_saldo_ledger_append_only
    BEFORE UPDATE OR DELETE ON saldo_ledger
    FOR EACH ROW EXECUTE FUNCTION tolak_perubahan_ledger();

COMMENT ON TABLE  saldo_ledger IS
    'Ledger saldo append-only (PRD 11.1). Sumber kebenaran saldo; hanya INSERT.';
COMMENT ON COLUMN saldo_ledger.saldo_setelah IS
    'Snapshot saldo setelah mutasi, untuk audit & deteksi drift.';
COMMENT ON COLUMN saldo_ledger.idempotency_key IS
    'Key dari klien kasir/callback PG; UNIQUE agar retry tidak memotong dua kali.';

-- ============================================================
-- saldo_cache — saldo berjalan (turunan, bisa dihitung ulang)
-- ============================================================
-- Baris di sini adalah "hot row" yang dikunci (FOR UPDATE) saat debit
-- supaya dua kasir tidak bisa memotong saldo yang sama secara bersamaan.
CREATE TABLE IF NOT EXISTS saldo_cache (
    subjek_tipe  VARCHAR(20)  NOT NULL,
    subjek_id    BIGINT       NOT NULL,
    sekolah_id   BIGINT       NOT NULL,
    saldo        BIGINT       NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_saldo_cache PRIMARY KEY (subjek_tipe, subjek_id),
    CONSTRAINT ck_saldo_cache_subjek_tipe
        CHECK (subjek_tipe IN ('SISWA', 'KARTU_TAMU')),
    -- Jaring pengaman terakhir: saldo minus mustahil (PRD §11.2).
    CONSTRAINT ck_saldo_cache_tidak_minus
        CHECK (saldo >= 0)
);

CREATE INDEX IF NOT EXISTS idx_saldo_cache_sekolah
    ON saldo_cache (sekolah_id, subjek_tipe);

COMMENT ON TABLE saldo_cache IS
    'Cache saldo berjalan. Selalu dapat dihitung ulang dari saldo_ledger. Baris dikunci saat debit.';
