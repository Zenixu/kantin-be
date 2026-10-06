-- ============================================================
-- V15 — Setoran kas TU harian (append-only)
--
-- PRD §9.2 · issue #39
--
-- "Setoran kas TU harian: rekap top-up tunai per petugas per hari →
--  dikonfirmasi bendahara saat uang disetor. Selisih kas dicatat,
--  tidak dihapus."
--
-- Prinsip:
--   * Append-only — HANYA INSERT (trigger `tolak_perubahan_ledger` dari V2).
--     Selisih kas TIDAK pernah diedit/dihapus; koreksi = baris baru.
--   * `total_topup` = rekap top-up tunai petugas pada `tanggal` (dihitung
--     dari `saldo_ledger`), `jumlah_disetor` = uang fisik yang benar-benar
--     disetor ke bendahara. `selisih` = total_topup − jumlah_disetor
--     (kolom terhitung/GENERATED agar tak bisa tak-konsisten).
--   * Idempotency: UNIQUE (sekolah_id, referensi_id) — satu berita acara
--     setoran tidak boleh tercatat dua kali.
-- ============================================================

CREATE TABLE IF NOT EXISTS setoran_tu (
    id                BIGINT       PRIMARY KEY,
    sekolah_id        BIGINT       NOT NULL,           -- tenant (PRD §11.4)
    tanggal           DATE         NOT NULL,           -- hari rekap (zona sekolah)
    petugas_id        BIGINT       NOT NULL,           -- aktor_id top-up (petugas TU)
    total_topup       BIGINT       NOT NULL,           -- rekap top-up tunai petugas hari itu
    jumlah_disetor    BIGINT       NOT NULL,           -- uang fisik yang disetor
    -- Selisih selalu total_topup − jumlah_disetor (boleh negatif bila lebih setor).
    -- GENERATED: mustahil tak-konsisten, tak bisa diedit terpisah.
    selisih           BIGINT       GENERATED ALWAYS AS (total_topup - jumlah_disetor) STORED,
    referensi_id      VARCHAR(60)  NOT NULL,           -- berita acara (idempotency)
    catatan           VARCHAR(500),
    dikonfirmasi_oleh BIGINT       NOT NULL,           -- bendahara (aktor)
    waktu             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_setoran_tu_total_tidak_negatif
        CHECK (total_topup >= 0 AND jumlah_disetor >= 0),
    CONSTRAINT uq_setoran_tu_sekolah_referensi
        UNIQUE (sekolah_id, referensi_id)
);

-- Daftar setoran satu sekolah terbaru dulu (tampilan bendahara).
CREATE INDEX IF NOT EXISTS idx_setoran_tu_sekolah_tanggal
    ON setoran_tu (sekolah_id, tanggal DESC);

-- Telusur setoran per petugas (rekap per petugas).
CREATE INDEX IF NOT EXISTS idx_setoran_tu_sekolah_petugas
    ON setoran_tu (sekolah_id, petugas_id, tanggal DESC);

-- Trigger: tolak UPDATE & DELETE (append-only) — pakai fungsi dari V2.
DROP TRIGGER IF EXISTS trg_setoran_tu_append_only ON setoran_tu;
CREATE TRIGGER trg_setoran_tu_append_only
    BEFORE UPDATE OR DELETE ON setoran_tu
    FOR EACH ROW EXECUTE FUNCTION tolak_perubahan_ledger();

COMMENT ON TABLE setoran_tu IS
    'Setoran kas TU harian (PRD 9.2, issue #39). Append-only; selisih kas dicatat, tidak dihapus.';
COMMENT ON COLUMN setoran_tu.total_topup IS
    'Rekap top-up tunai petugas pada tanggal tsb, dihitung dari saldo_ledger.';
COMMENT ON COLUMN setoran_tu.selisih IS
    'total_topup - jumlah_disetor (GENERATED). Positif = kurang setor, negatif = lebih setor.';
COMMENT ON COLUMN setoran_tu.referensi_id IS
    'Nomor berita acara setoran; UNIQUE per sekolah untuk idempotency.';
