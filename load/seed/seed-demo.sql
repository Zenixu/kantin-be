-- ============================================================
-- seed-demo.sql — Data seed untuk DEMO E2E kasir (issue #163)
-- ------------------------------------------------------------
-- Tujuan: menyediakan data MINIMAL agar alur kasir bisa diuji
-- END-TO-END di lokal (tap -> potong saldo/stok -> tutup sesi ->
-- cek laporan) TANPA menunggu data/keys tim SKOOLIA (OPEN-QUESTIONS Q7).
--
-- ⚠️ BUKAN migrasi Flyway dan BUKAN data produksi. Jalankan manual:
--     psql "$DB_URL" -f load/seed/seed-demo.sql
--
-- Idempoten: aman dijalankan berulang (ON CONFLICT DO NOTHING/UPDATE).
-- Semua data di sini MILIK kantin-be sendiri (tabel lokal) — tak ada
-- ketergantungan ke admin-be/mobile-be.
--
-- Sekolah demo = 1. Kartu = Kartu Tamu (PRD §9.4). UID = DE000001..DE000005
-- (heksa valid, 8 karakter) — dipakai juga di docs/demo-e2e-kasir.md.
-- ============================================================

-- ── Konfigurasi kantin (konfirmasi_manual=false → tap langsung potong) ──
INSERT INTO sekolah_kantin_config (sekolah_id, nama_kantin, jam_tutup_otomatis,
                                   konfirmasi_manual, created_at, updated_at)
VALUES (1, 'Kantin Demo SKOOLIA', '23:59', false, now(), now())
ON CONFLICT (sekolah_id) DO UPDATE
    SET nama_kantin = EXCLUDED.nama_kantin,
        konfirmasi_manual = false,
        updated_at = now();

-- ── Titik kasir ──
INSERT INTO titik_kasir (id, sekolah_id, nama, kode, is_active, created_at, updated_at)
VALUES (1, 1, 'Kasir Demo', 'DEMO1', true, now(), now())
ON CONFLICT (id) DO NOTHING;

-- ── Sesi kasir hari ini (TERBUKA) → tap langsung jalan tanpa buka sesi ──
INSERT INTO sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status,
                        total_bruto, total_void, total_bersih, dibuka_at,
                        created_at, updated_at)
VALUES (1, 1, 1, CURRENT_DATE, 'TERBUKA', 0, 0, 0, now(), now(), now())
ON CONFLICT (titik_kasir_id, tanggal) DO NOTHING;

-- ── Kategori ──
INSERT INTO kategori_menu (id, sekolah_id, nama, urutan, is_active, created_at, updated_at)
VALUES (1, 1, 'Makanan', 1, true, now(), now()),
       (2, 1, 'Minuman', 2, true, now(), now())
ON CONFLICT (id) DO NOTHING;

-- ── Menu (harga rupiah integer; satuan PCS|PORSI|BOTOL) ──
INSERT INTO menu (id, sekolah_id, kategori_id, nama, harga_jual, satuan,
                  stok_minimum, is_active, created_at, updated_at)
VALUES (1, 1, 1, 'Nasi Goreng',   12000, 'PORSI', 5, true, now(), now()),
       (2, 1, 1, 'Mie Ayam',      10000, 'PORSI', 5, true, now(), now()),
       (3, 1, 2, 'Es Teh Manis',   3000, 'PCS',   10, true, now(), now())
ON CONFLICT (id) DO NOTHING;

-- ── Stok & HPP awal (cukup besar untuk demo berulang) ──
INSERT INTO stok_cache (menu_id, sekolah_id, stok, hpp, stok_minimum, updated_at)
VALUES (1, 1, 1000, 7000, 5, now()),
       (2, 1, 1000, 6000, 5, now()),
       (3, 1, 1000, 1000, 10, now())
ON CONFLICT (menu_id) DO UPDATE
    SET stok = EXCLUDED.stok, hpp = EXCLUDED.hpp,
        stok_minimum = EXCLUDED.stok_minimum, updated_at = now();

-- ── 5 Kartu Tamu (label pemegang terisi agar tampil ramah di kasir) ──
INSERT INTO kartu_tamu (id, sekolah_id, nomor_kartu, rfid_uid, aktif,
                        label_pemegang, dibuat_oleh, dibuat_pada)
VALUES (1, 1, 'KT-DEMO-001', 'DE000001', true, 'Budi (Guru)',   1, now()),
       (2, 1, 'KT-DEMO-002', 'DE000002', true, 'Sari (Staf TU)', 1, now()),
       (3, 1, 'KT-DEMO-003', 'DE000003', true, 'Tamu Umum',      1, now()),
       (4, 1, 'KT-DEMO-004', 'DE000004', true, 'Rina (Guru)',    1, now()),
       (5, 1, 'KT-DEMO-005', 'DE000005', true, 'Tamu Umum',      1, now())
ON CONFLICT (id) DO NOTHING;

-- ── Saldo awal Kartu Tamu (Rp 100.000) — saldo_cache = turunan ──
INSERT INTO saldo_cache (subjek_tipe, subjek_id, sekolah_id, saldo, updated_at)
VALUES ('KARTU_TAMU', 1, 1, 100000, now()),
       ('KARTU_TAMU', 2, 1, 100000, now()),
       ('KARTU_TAMU', 3, 1, 100000, now()),
       ('KARTU_TAMU', 4, 1, 100000, now()),
       ('KARTU_TAMU', 5, 1, 100000, now())
ON CONFLICT (subjek_tipe, subjek_id) DO UPDATE
    SET saldo = EXCLUDED.saldo, updated_at = now();

-- ── Ledger saldo awal (sumber kebenaran; append-only → hanya INSERT) ──
-- Saldo di atas HARUS dapat dihitung ulang dari sini (audit & deteksi drift).
-- Idempotency key unik agar re-run tidak menambah mutasi dobel.
INSERT INTO saldo_ledger (id, sekolah_id, subjek_tipe, subjek_id, arah, jenis,
                          nominal, saldo_setelah, idempotency_key,
                          referensi_tipe, keterangan, waktu, created_at)
VALUES (900001, 1, 'KARTU_TAMU', 1, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, 'seed-demo-kt1', 'TOPUP', 'Saldo awal demo', now(), now()),
       (900002, 1, 'KARTU_TAMU', 2, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, 'seed-demo-kt2', 'TOPUP', 'Saldo awal demo', now(), now()),
       (900003, 1, 'KARTU_TAMU', 3, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, 'seed-demo-kt3', 'TOPUP', 'Saldo awal demo', now(), now()),
       (900004, 1, 'KARTU_TAMU', 4, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, 'seed-demo-kt4', 'TOPUP', 'Saldo awal demo', now(), now()),
       (900005, 1, 'KARTU_TAMU', 5, 'KREDIT', 'TOPUP_TUNAI', 100000, 100000, 'seed-demo-kt5', 'TOPUP', 'Saldo awal demo', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ── Ringkas ──
DO $$
BEGIN
    RAISE NOTICE 'Seed DEMO siap: sekolah 1 | 1 titik kasir (DEMO1) | sesi TERBUKA | 2 kategori | 3 menu | 5 Kartu Tamu (UID DE000001..DE000005, saldo Rp 100.000).';
END $$;
