-- ============================================================
-- V22 — Kartu Tamu: label pemegang (PRD §9.4, issue #121)
--
-- PRD §9.4: TU mendaftarkan kartu dengan tap → sistem memberi NOMOR KARTU
-- (KT-012, dicetak/ditempel) → isi LABEL PEMEGANG opsional (nama guru/staf,
-- atau "Tamu"). Label pemegang dikosongkan saat kartu dikembalikan.
--
-- Sebelumnya kartu hanya punya `catatan` bebas — tak ada field khusus label
-- pemegang yang bisa dikosongkan. Migrasi ini menambah `label_pemegang`.
--
-- Catatan: NOMOR KARTU kini digenerate otomatis oleh aplikasi (KT- + urutan
-- per sekolah) — tidak butuh kolom baru, cukup dipakai dari kolom yang ada.
-- ============================================================

ALTER TABLE kartu_tamu
    ADD COLUMN IF NOT EXISTS label_pemegang VARCHAR(150);

COMMENT ON COLUMN kartu_tamu.label_pemegang IS
    'Label pemegang kartu (nama guru/staf, atau "Tamu"). Dikosongkan saat pengembalian (PRD 9.4).';
