-- ============================================================
-- Query SQL MANUAL (ad-hoc) — bukan migrasi Flyway.
--
-- Simpan di sini: query eksplorasi, cek data, perbaikan manual.
-- Semua perubahan skema tetap lewat src/main/resources/db/migration/.
-- ============================================================

-- Contoh: cek versi migrasi yang sudah jalan
-- SELECT installed_rank, version, description, success, installed_on
-- FROM flyway_schema_history ORDER BY installed_rank DESC;
