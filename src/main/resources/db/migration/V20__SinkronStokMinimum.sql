-- ============================================================
-- V20 — Sinkronkan stok_cache.stok_minimum dari menu.stok_minimum
--       (PRD §7.5, issue #113)
--
-- Masalah: stok_cache.stok_minimum selalu diisi 0 saat baris dibuat dan tidak
-- pernah diperbarui, sehingga endpoint GET /api/stok/menipis dan flag `menipis`
-- pada GET /api/stok/{menuId} memakai ambang 0 — item baru dianggap "menipis"
-- saat stok ≤ 0, bukan saat menyentuh stok minimum yang diset pengelola.
--
-- Sumber kebenaran ambang = menu.stok_minimum (diisi pengelola lewat katalog).
-- Migrasi ini (a) memastikan setiap menu punya baris stok_cache agar ikut
-- terpantau, dan (b) mem-backfill stok_minimum untuk baris yang sudah ada.
--
-- Penegakan berkelanjutan dilakukan di KatalogService (upsert saat menu
-- dibuat/diubah) sehingga kolom tetap sinkron ke depan.
-- ============================================================

-- (a) Setiap menu wajib punya baris stok_cache (stok awal 0).
INSERT INTO stok_cache (menu_id, sekolah_id, stok, hpp, stok_minimum, updated_at)
SELECT m.id, m.sekolah_id, 0, 0, COALESCE(m.stok_minimum, 0), now()
FROM menu m
ON CONFLICT (menu_id) DO NOTHING;

-- (b) Backfill ambang untuk baris yang sudah ada agar sesuai katalog.
UPDATE stok_cache sc
SET stok_minimum = COALESCE(m.stok_minimum, 0),
    updated_at   = now()
FROM menu m
WHERE sc.menu_id = m.id
  AND sc.stok_minimum IS DISTINCT FROM COALESCE(m.stok_minimum, 0);
