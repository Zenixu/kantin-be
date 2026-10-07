-- ============================================================
-- V16 — Fitur DEMO untuk pertanyaan terblokir (#21–#25)
--
-- Catatan: kantin-be belum rilis penuh; migrasi ini menyediakan
-- solusi "seadanya" agar modul tetap jalan tanpa menunggu tim lain:
--   * #21 (Q8)  pos_buku_kas   — pos Buku Kas dibuat OTOMATIS per sekolah
--                (fallback lokal sampai admin-be mengonfirmasi).
--   * #23 (Q14) insiden_offline — pencatatan insiden offline (prosedur darurat).
--   * #25 (Q16) kebijakan_kantin — kebijakan saldo mengendap per sekolah.
--
-- Semua tabel tenant-scoped (PRD §11.4). insiden_offline append-only.
-- ============================================================

-- ============================================================
-- pos_buku_kas — pos Buku Kas yang dipakai kantin-be (DEMO, #21 / Q8)
-- ============================================================
-- Fallback lokal: kantin-be mencatat pos yang ia butuhkan ("Pendapatan
-- Kantin", "Belanja Stok Kantin", "Penyesuaian Kantin") per sekolah secara
-- otomatis saat pertama dipakai. Bila admin-be (Q8) sudah membuat pos ini,
-- cukup arahkan posting ke admin-be — tabel ini jadi cache/rujukan.
CREATE TABLE IF NOT EXISTS pos_buku_kas (
    id          BIGINT       PRIMARY KEY,
    sekolah_id  BIGINT       NOT NULL,           -- tenant (PRD §11.4)
    nama        VARCHAR(80)  NOT NULL,           -- nama pos (mis. "Pendapatan Kantin")
    tipe        VARCHAR(10)  NOT NULL,           -- MASUK | KELUAR
    keterangan  VARCHAR(255),
    aktif       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_pos_buku_kas_tipe CHECK (tipe IN ('MASUK', 'KELUAR')),
    -- Satu nama pos unik per sekolah → seeding idempoten.
    CONSTRAINT uq_pos_buku_kas_sekolah_nama UNIQUE (sekolah_id, nama)
);

CREATE INDEX IF NOT EXISTS idx_pos_buku_kas_sekolah
    ON pos_buku_kas (sekolah_id, aktif);

COMMENT ON TABLE pos_buku_kas IS
    'Pos Buku Kas kantin (DEMO, Q8/#21). Dibuat otomatis per sekolah; fallback sampai admin-be mengonfirmasi.';

-- ============================================================
-- insiden_offline — prosedur darurat saat internet/server mati (DEMO, #23 / Q14)
-- ============================================================
-- Petugas mencatat insiden (waktu mulai/selesai, titik kasir, keterangan)
-- agar sekolah bisa memutuskan apakah mode offline perlu dimajukan.
-- Append-only: insiden tidak diedit/dihapus; koreksi = baris baru.
CREATE TABLE IF NOT EXISTS insiden_offline (
    id               BIGINT       PRIMARY KEY,
    sekolah_id       BIGINT       NOT NULL,       -- tenant (PRD §11.4)
    titik_kasir_id   BIGINT,                      -- titik kasir terdampak (boleh null)
    mulai            TIMESTAMPTZ  NOT NULL,       -- waktu mulai gangguan
    selesai          TIMESTAMPTZ,                 -- waktu pulih (null = masih berlangsung)
    durasi_menit     INTEGER,                     -- diisi saat selesai
    keterangan       VARCHAR(500) NOT NULL,       -- kronologi singkat
    dilaporkan_oleh  BIGINT,                      -- aktor pelapor (audit)
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_insiden_offline_durasi CHECK (durasi_menit IS NULL OR durasi_menit >= 0)
);

CREATE INDEX IF NOT EXISTS idx_insiden_offline_sekolah
    ON insiden_offline (sekolah_id, mulai DESC);

-- Trigger: tolak UPDATE & DELETE (append-only) — pakai fungsi dari V2.
DROP TRIGGER IF EXISTS trg_insiden_offline_append_only ON insiden_offline;
CREATE TRIGGER trg_insiden_offline_append_only
    BEFORE UPDATE OR DELETE ON insiden_offline
    FOR EACH ROW EXECUTE FUNCTION tolak_perubahan_ledger();

COMMENT ON TABLE insiden_offline IS
    'Insiden offline kasir (DEMO, Q14/#23). Append-only; dasar keputusan memajukan mode offline.';

-- ============================================================
-- kebijakan_kantin — kebijakan per sekolah (DEMO, #25 / Q16)
-- ============================================================
-- Kebijakan saldo mengendap siswa lulus/keluar yang tak diklaim.
-- Default: REFUND (kembalikan ke ortu). Mutable (bukan append-only) —
-- perubahan dicatat di audit_log.
CREATE TABLE IF NOT EXISTS kebijakan_kantin (
    sekolah_id                 BIGINT       PRIMARY KEY,   -- satu baris per sekolah
    kebijakan_saldo_mengendap  VARCHAR(30)  NOT NULL DEFAULT 'REFUND',
    ambang_hari                INTEGER      NOT NULL DEFAULT 90,
    catatan                    VARCHAR(500),
    diperbarui_oleh            BIGINT,
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_kebijakan_kantin_saldo
        CHECK (kebijakan_saldo_mengendap IN ('REFUND', 'PINDAH_SAUDARA', 'TETAP_MENGENDAP')),
    CONSTRAINT ck_kebijakan_kantin_ambang CHECK (ambang_hari >= 0)
);

COMMENT ON TABLE kebijakan_kantin IS
    'Kebijakan kantin per sekolah (DEMO, Q16/#25). Default saldo mengendap = REFUND.';
COMMENT ON COLUMN kebijakan_kantin.kebijakan_saldo_mengendap IS
    'REFUND (kembali ke ortu) | PINDAH_SAUDARA (pindah ke saudara aktif) | TETAP_MENGENDAP (dibiarkan).';
