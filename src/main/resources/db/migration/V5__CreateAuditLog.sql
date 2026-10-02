-- ============================================================
-- V5 — Audit Log (append-only)
--
-- Fase 3/4 · PRD §11.7 · CONVENTIONS.md §6
--
-- Jejak audit tahan-restart & bisa di-query untuk aksi sensitif:
--   void, koreksi, top-up, refund, ubah harga jual, barang masuk &
--   pembaliknya, penyesuaian stok (opname), ubah limit/blokir.
--
-- Prinsip:
--   * Append-only — HANYA INSERT (dijaga trigger `tolak_perubahan_ledger`
--     yang sudah dibuat di V2).
--   * `aktor_id` boleh NULL untuk aksi sistem/scheduler; `sekolah_id` WAJIB
--     untuk scoping tenant (PRD §11.4).
--   * `nilai_lama`/`nilai_baru` disimpan sebagai TEXT agar fleksibel (angka,
--     enum, atau JSON ringkas) — audit tidak boleh gagal karena tipe.
-- ============================================================

CREATE TABLE IF NOT EXISTS audit_log (
    id          BIGINT       PRIMARY KEY,
    sekolah_id  BIGINT       NOT NULL,          -- tenant (PRD §11.4)
    aktor_id    BIGINT,                          -- user pelaku (NULL = sistem)
    aksi        VARCHAR(60)  NOT NULL,           -- mis. VOID_TRANSAKSI, OPNAME_STOK
    entitas     VARCHAR(60)  NOT NULL,           -- mis. Transaksi, Stok
    entitas_id  VARCHAR(64),                     -- id entitas (teks agar fleksibel)
    nilai_lama  TEXT,                            -- sebelum (boleh NULL)
    nilai_baru  TEXT,                            -- sesudah (boleh NULL)
    alasan      VARCHAR(500),                    -- wajib utk void/koreksi/opname
    waktu       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Daftar audit satu sekolah, terbaru dulu (default tampilan audit).
CREATE INDEX IF NOT EXISTS idx_audit_log_sekolah_waktu
    ON audit_log (sekolah_id, waktu DESC);

-- Telusur riwayat satu entitas (mis. semua audit untuk satu transaksi).
CREATE INDEX IF NOT EXISTS idx_audit_log_entitas
    ON audit_log (sekolah_id, entitas, entitas_id);

-- Filter per jenis aksi (mis. semua VOID/OPNAME pada rentang waktu).
CREATE INDEX IF NOT EXISTS idx_audit_log_aksi
    ON audit_log (sekolah_id, aksi, waktu DESC);

-- Trigger: tolak UPDATE & DELETE (append-only). Memakai fungsi yang sudah
-- dibuat di V2 (tolak_perubahan_ledger).
DROP TRIGGER IF EXISTS trg_audit_log_append_only ON audit_log;
CREATE TRIGGER trg_audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION tolak_perubahan_ledger();

COMMENT ON TABLE audit_log IS
    'Jejak audit append-only untuk aksi sensitif (PRD 11.7). Hanya INSERT.';
COMMENT ON COLUMN audit_log.nilai_lama IS
    'Nilai sebelum aksi (teks bebas) — untuk merekonstruksi perubahan.';
COMMENT ON COLUMN audit_log.aktor_id IS
    'User pelaku; NULL berarti aksi sistem/scheduler (mis. auto-tutup kasir).';
