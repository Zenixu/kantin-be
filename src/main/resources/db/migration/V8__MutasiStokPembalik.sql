-- ============================================================
-- V8 — Koreksi Barang Masuk (pembalik) + riwayat
--
-- Fase 5 · PRD §7.2: "Barang masuk yang salah input dikoreksi dengan
-- barang masuk pembalik (wajib alasan), bukan diedit/dihapus."
--
-- Ledger stok tetap append-only: migrasi ini HANYA menambah kolom
-- (tidak ada UPDATE/DELETE baris). Pembalik dicatat sebagai mutasi baru
-- ber-jenis BARANG_MASUK_PEMBALIK yang menunjuk baris asal.
--
--   * harga_beli_satuan — harga beli/unit saat BARANG_MASUK; dipakai untuk
--     menghitung ulang HPP rata-rata ketika dibalik & untuk laporan belanja.
--   * mutasi_asal_id    — pada baris BARANG_MASUK_PEMBALIK: menunjuk baris
--     barang masuk yang dibatalkan (jejak koreksi).
-- ============================================================

ALTER TABLE mutasi_stok
    ADD COLUMN IF NOT EXISTS harga_beli_satuan BIGINT;

ALTER TABLE mutasi_stok
    ADD COLUMN IF NOT EXISTS mutasi_asal_id BIGINT;

COMMENT ON COLUMN mutasi_stok.harga_beli_satuan IS
    'Harga beli/unit saat BARANG_MASUK (rupiah integer). Dasar hitung HPP saat pembalik.';
COMMENT ON COLUMN mutasi_stok.mutasi_asal_id IS
    'Baris BARANG_MASUK yang dibalik (terisi hanya untuk jenis BARANG_MASUK_PEMBALIK).';

-- Cari cepat pembalik per barang masuk asal (hitung sisa yang dapat dibalik).
CREATE INDEX IF NOT EXISTS idx_mutasi_stok_asal
    ON mutasi_stok (sekolah_id, mutasi_asal_id)
    WHERE mutasi_asal_id IS NOT NULL;

-- Idempotency bukti pembalik: satu nomor bukti pembalik per sekolah,
-- sehingga retry jaringan tidak membalik dua kali.
CREATE UNIQUE INDEX IF NOT EXISTS uq_mutasi_stok_pembalik_referensi
    ON mutasi_stok (sekolah_id, referensi_id)
    WHERE referensi_tipe = 'BARANG_MASUK_PEMBALIK' AND referensi_id IS NOT NULL;
