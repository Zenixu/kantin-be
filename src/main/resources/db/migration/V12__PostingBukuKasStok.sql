-- ============================================================
-- V12 — Penanda posting Buku Kas untuk mutasi stok (PRD §5.1, §7.2, §11.3)
--
-- MASALAH: setiap barang masuk wajib diposting sebagai pengeluaran Buku Kas
-- pos "Belanja Stok Kantin" (PRD §5.1, §7.2), dan koreksinya (barang masuk
-- pembalik) sebagai entri koreksi. Namun Buku Kas admin-be TIDAK idempoten —
-- `catatTransaksi()` selalu `save` (INTEGRATIONS.md §3.4 poin 2) — sehingga
-- kantin-be yang harus menjamin sekali-posting. Retry jaringan / double-submit
-- TIDAK boleh menggandakan entri Buku Kas.
--
-- KENAPA TABEL BARU: `mutasi_stok` bersifat append-only (trigger
-- `trg_mutasi_stok_append_only`) dan `@Immutable`, jadi tak bisa menyimpan flag
-- "sudah diposting". Migrasi ini menambah tabel penanda `posting_buku_kas`:
-- satu baris per posting SUKSES, unik per (sekolah, referensi). Baris ini
-- menjadi jalur cepat idempotency; UNIQUE index menjadi jaring terakhir bila
-- dua request dengan referensi sama tiba bersamaan.
--
-- Tidak ada UPDATE/DELETE baris pada `mutasi_stok`; tabel penanda murni INSERT
-- dari sisi alur normal (kolom `status` disiapkan untuk retry di issue #33).
-- ============================================================

CREATE TABLE IF NOT EXISTS posting_buku_kas (
    id            BIGINT       PRIMARY KEY,
    sekolah_id    BIGINT       NOT NULL,           -- tenant (PRD §11.4)
    referensi_id  VARCHAR(120) NOT NULL,           -- refId deterministik (idempotency)
    entitas       VARCHAR(40)  NOT NULL,           -- BARANG_MASUK | BARANG_MASUK_PEMBALIK
    mutasi_id     BIGINT       NOT NULL,           -- baris mutasi_stok terkait
    ref_buku_kas  VARCHAR(120),                    -- referensi entri dari Buku Kas
    jumlah        BIGINT       NOT NULL,           -- rupiah integer (dikirim sbg BigDecimal)
    status        VARCHAR(20)  NOT NULL,           -- SUKSES (cadangan: DILEWATI/GAGAL)
    waktu         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Idempotency: satu referensi Buku Kas per sekolah (jaring terakhir anti-ganda).
CREATE UNIQUE INDEX IF NOT EXISTS uq_posting_buku_kas_sekolah_referensi
    ON posting_buku_kas (sekolah_id, referensi_id);

-- Telusur: entri Buku Kas mana yang lahir dari mutasi stok tertentu.
CREATE INDEX IF NOT EXISTS idx_posting_buku_kas_mutasi
    ON posting_buku_kas (sekolah_id, mutasi_id);

-- Laporan/rekonsiliasi posting per periode.
CREATE INDEX IF NOT EXISTS idx_posting_buku_kas_waktu
    ON posting_buku_kas (sekolah_id, waktu DESC);

COMMENT ON TABLE posting_buku_kas IS
    'Penanda posting Buku Kas per mutasi stok (PRD 5.1, 7.2). Idempotency: satu referensi per sekolah; retry tidak menggandakan entri.';
COMMENT ON COLUMN posting_buku_kas.referensi_id IS
    'refId deterministik entri Buku Kas (mis. KANTIN-BM-<bukti>-<menuId>) — kunci idempotency.';
COMMENT ON COLUMN posting_buku_kas.status IS
    'Hasil posting. Baris hanya ditulis saat SUKSES; DILEWATI/GAGAL tidak menyimpan baris (bisa di-retry).';
