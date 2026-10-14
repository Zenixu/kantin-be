-- ============================================================
-- V23 — Trigger: jamin stok_cache.stok_minimum selalu sinkron dengan
--       menu.stok_minimum (PRD §7.5 · lanjutan #113 · debt B38 / issue #196)
--
-- Masalah: stok_minimum adalah atribut KATALOG — sumber kebenarannya
-- `menu.stok_minimum` (diisi pengelola). Nilainya disalin ke `stok_cache`
-- supaya GET /api/stok/menipis dan flag `menipis` pada GET /api/stok/{menuId}
-- cukup membaca SATU tabel. Tetapi sinkronisasinya dulu hanya diandalkan pada
-- panggilan manual di KatalogService, sehingga jalur tulis lain — seed,
-- migrasi, UPDATE SQL langsung, update massal — bisa membuat keduanya
-- DIVERGEN tanpa terdeteksi (itulah bug #113 yang terulang).
--
-- Keputusan issue #196 = OPSI 2: kolom DIPERTAHANKAN (stok_cache tetap cache
-- baca satu-tabel; jalur tap tidak perlu join ke menu), tetapi konsistensinya
-- kini dijamin di level DB oleh trigger — bukan oleh disiplin pemanggil.
-- Opsi 1 (hapus kolom + join ke menu) ditolak karena menambah join pada jalur
-- baca dan memerlukan migrasi destruktif tanpa manfaat tambahan.
--
-- Trigger idempoten & aman dijalankan ulang.
-- ============================================================

-- Upsert ambang: membuat baris stok_cache bila belum ada (stok/hpp 0 —
-- baris baru memang belum punya saldo), atau hanya memperbarui ambangnya bila
-- sudah ada sehingga stok & hpp berjalan TIDAK tersentuh.
CREATE OR REPLACE FUNCTION sinkron_stok_minimum() RETURNS trigger AS $$
BEGIN
    INSERT INTO stok_cache (menu_id, sekolah_id, stok, hpp, stok_minimum, updated_at)
    VALUES (NEW.id, NEW.sekolah_id, 0, 0, COALESCE(NEW.stok_minimum, 0), now())
    ON CONFLICT (menu_id) DO UPDATE
        SET stok_minimum = EXCLUDED.stok_minimum,
            updated_at   = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Menangkap SEMUA jalur tulis ke menu (service, seed, migrasi, SQL manual).
DROP TRIGGER IF EXISTS trg_menu_sinkron_stok_minimum ON menu;
CREATE TRIGGER trg_menu_sinkron_stok_minimum
    AFTER INSERT OR UPDATE OF stok_minimum ON menu
    FOR EACH ROW EXECUTE FUNCTION sinkron_stok_minimum();

-- (a) Setiap menu wajib punya baris stok_cache agar ikut terpantau.
INSERT INTO stok_cache (menu_id, sekolah_id, stok, hpp, stok_minimum, updated_at)
SELECT m.id, m.sekolah_id, 0, 0, COALESCE(m.stok_minimum, 0), now()
FROM menu m
ON CONFLICT (menu_id) DO NOTHING;

-- (b) Backfill ambang baris yang sudah ada agar sesuai katalog.
UPDATE stok_cache sc
SET stok_minimum = COALESCE(m.stok_minimum, 0),
    updated_at   = now()
FROM menu m
WHERE sc.menu_id = m.id
  AND sc.stok_minimum IS DISTINCT FROM COALESCE(m.stok_minimum, 0);

COMMENT ON FUNCTION sinkron_stok_minimum() IS
    'Jaga stok_cache.stok_minimum = menu.stok_minimum (PRD 7.5, issue #196). Dipanggil oleh trigger trg_menu_sinkron_stok_minimum pada menu (INSERT / UPDATE OF stok_minimum).';

COMMENT ON COLUMN stok_cache.stok_minimum IS
    'Salinan ambang dari menu.stok_minimum, dijaga trigger trg_menu_sinkron_stok_minimum (issue #196). Sumber kebenaran tetap menu.stok_minimum.';
