-- ============================================================
-- V12 — Jurnal & idempotency event webhook (PRD §11.3, SECURITY.md §5)
--
-- MASALAH (audit keamanan B34): endpoint /api/webhook/** dibuka `permitAll`
-- (tanpa token user). Saat webhook benar-benar diimplementasikan, pengirim
-- (SKOOLIA/callback-be) akan ME-RETRY event yang sama berkali-kali (jaringan
-- timeout, ack hilang). Tanpa kunci idempotency, retry menggandakan efek
-- (mis. saldo bertambah dua kali).
--
-- PERBAIKAN: tabel `webhook_event` mencatat tiap event yang DITERIMA dengan
-- kunci unik (sumber, event_id). Retry event yang sama → baris sudah ada →
-- TIDAK diproses ulang (dijawab sebagai replay). `payload_hash` (SHA-256 hex)
-- mendeteksi event id sama tetapi isi berbeda (indikasi penyalahgunaan) → 409.
--
-- Tabel bersifat append-only (jurnal), dijaga trigger `tolak_perubahan_ledger`
-- yang sudah dibuat di V2 — konsisten dengan saldo_ledger/mutasi_stok/audit_log.
-- ============================================================

CREATE TABLE IF NOT EXISTS webhook_event (
    id            BIGINT       PRIMARY KEY,
    sumber        VARCHAR(40)  NOT NULL,          -- mis. SKOOLIA, CALLBACK_BE
    event_id      VARCHAR(128) NOT NULL,          -- id unik dari pengirim (header/body)
    event_type    VARCHAR(80),                    -- mis. TOPUP_ONLINE_SUKSES
    payload_hash  VARCHAR(64)  NOT NULL,          -- SHA-256 hex badan request
    sekolah_id    BIGINT,                         -- tenant (bila event memuatnya)
    status        VARCHAR(20)  NOT NULL,          -- DIPROSES | DIABAIKAN
    keterangan    VARCHAR(255),
    waktu         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Idempotency: satu (sumber, event_id) hanya boleh dicatat sekali.
CREATE UNIQUE INDEX IF NOT EXISTS uq_webhook_event_sumber_event
    ON webhook_event (sumber, event_id);

-- Telusur event per sekolah & waktu (observability / rekonsiliasi).
CREATE INDEX IF NOT EXISTS idx_webhook_event_sekolah_waktu
    ON webhook_event (sekolah_id, waktu DESC);

-- Filter per jenis event (mis. semua TOPUP_ONLINE pada rentang waktu).
CREATE INDEX IF NOT EXISTS idx_webhook_event_type_waktu
    ON webhook_event (event_type, waktu DESC);

-- Append-only: tolak UPDATE & DELETE (memakai fungsi dari V2).
DROP TRIGGER IF EXISTS trg_webhook_event_append_only ON webhook_event;
CREATE TRIGGER trg_webhook_event_append_only
    BEFORE UPDATE OR DELETE ON webhook_event
    FOR EACH ROW EXECUTE FUNCTION tolak_perubahan_ledger();

COMMENT ON TABLE webhook_event IS
    'Jurnal event webhook masuk (append-only). Kunci idempotency (sumber, event_id) mencegah retry diproses dua kali (PRD 11.3).';
COMMENT ON COLUMN webhook_event.payload_hash IS
    'SHA-256 hex badan request; mendeteksi event_id sama dengan payload berbeda (409).';
COMMENT ON COLUMN webhook_event.status IS
    'DIPROSES bila ada handler yang menanganinya; DIABAIKAN bila jenis event belum dikenal.';
