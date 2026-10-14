package com.asqi.scholia_kantin_be.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.asqi.scholia_kantin_be.model.StokCache;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Uji integrasi invarian <b>{@code stok_cache.stok_minimum ≡ menu.stok_minimum}</b>
 * (PRD §7.5 · debt B38 · issue #196).
 *
 * <p>Sebelum perbaikan, sinkronisasi hanya terjadi lewat panggilan manual di
 * {@code KatalogService}. Jalur tulis lain — seed, migrasi, {@code UPDATE} SQL
 * langsung, update massal — bisa membuat kedua kolom <b>divergen</b> tanpa
 * terdeteksi (akar bug #113 terulang). Migrasi {@code V23} memasang trigger
 * {@code trg_menu_sinkron_stok_minimum} di tabel {@code menu}.
 *
 * <p>Uji ini sengaja menulis lewat <b>SQL langsung</b> (mem-bypass service) supaya
 * benar-benar menguji jaminan level DB: <b>gagal bila trigger tidak ada</b>.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class StokMinimumSinkronIT {

    private static final long SEKOLAH = 1L;
    private static final long MENU = 100L;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StokCacheRepository cacheRepo;

    @BeforeEach
    void siapkan() {
        jdbc.execute("TRUNCATE TABLE mutasi_stok, stok_cache CASCADE");
        jdbc.execute("TRUNCATE TABLE menu CASCADE");
    }

    @Test
    @DisplayName("#196: INSERT menu lewat SQL langsung → baris stok_cache dibuat & ambang ikut")
    void insertLangsungSinkron() {
        menu(MENU, 7);

        assertThat(ambangCache()).isEqualTo(7);
        assertThat(jdbc.queryForObject(
                "SELECT stok FROM stok_cache WHERE menu_id = ?", Integer.class, MENU)).isZero();
    }

    @Test
    @DisplayName("#196: UPDATE menu.stok_minimum lewat SQL langsung → stok_cache ikut (REGRESI: gagal tanpa trigger)")
    void updateLangsungSinkron() {
        menu(MENU, 5);
        assertThat(ambangCache()).isEqualTo(5);

        jdbc.update("UPDATE menu SET stok_minimum = ? WHERE id = ?", 12, MENU);

        assertThat(ambangCache())
                .as("trigger trg_menu_sinkron_stok_minimum harus menyinkronkan walau lewat SQL langsung")
                .isEqualTo(12);
    }

    @Test
    @DisplayName("#196: ubah ambang TIDAK mereset stok & hpp berjalan (cache tidak rusak)")
    void ambangTidakMerusakStokBerjalan() {
        menu(MENU, 5);
        jdbc.update("UPDATE stok_cache SET stok = 40, hpp = 2500 WHERE menu_id = ?", MENU);

        jdbc.update("UPDATE menu SET stok_minimum = ? WHERE id = ?", 9, MENU);

        Map<String, Object> baris = jdbc.queryForMap(
                "SELECT stok, hpp, stok_minimum FROM stok_cache WHERE menu_id = ?", MENU);
        assertThat(baris.get("stok")).isEqualTo(40);
        assertThat(baris.get("hpp")).isEqualTo(2500L);
        assertThat(baris.get("stok_minimum")).isEqualTo(9);
    }

    @Test
    @DisplayName("#196: daftar 'stok menipis' tetap benar setelah ambang disinkron trigger")
    void stokMenipisTetapBenar() {
        menu(MENU, 10); // ambang 10

        jdbc.update("UPDATE stok_cache SET stok = 10 WHERE menu_id = ?", MENU); // tepat di ambang
        assertThat(cacheRepo.stokMenipis(SEKOLAH))
                .extracting(StokCache::getMenuId)
                .containsExactly(MENU);

        jdbc.update("UPDATE stok_cache SET stok = 11 WHERE menu_id = ?", MENU); // di atas ambang
        assertThat(cacheRepo.stokMenipis(SEKOLAH)).isEmpty();
    }

    @Test
    @DisplayName("#196: ambang tenant lain tidak bocor ke daftar 'stok menipis'")
    void tenantScoped() {
        menu(MENU, 10);
        jdbc.update("INSERT INTO menu (id, sekolah_id, nama, harga_jual, stok_minimum) "
                + "VALUES (?, ?, ?, 10000, ?)", 101L, 2L, "Menu Sekolah Lain", 10);
        jdbc.update("UPDATE stok_cache SET stok = 1 WHERE menu_id IN (?, ?)", MENU, 101L);

        assertThat(cacheRepo.stokMenipis(SEKOLAH))
                .extracting(StokCache::getMenuId)
                .containsExactly(MENU);
    }

    // ────────────────────────────────────────────────────────────────
    // Helper seed — sengaja SQL langsung, bukan lewat service
    // ────────────────────────────────────────────────────────────────

    private void menu(long id, int stokMinimum) {
        jdbc.update("INSERT INTO menu (id, sekolah_id, nama, harga_jual, stok_minimum) "
                + "VALUES (?, ?, ?, 10000, ?)", id, SEKOLAH, "Menu " + id, stokMinimum);
    }

    private Integer ambangCache() {
        return jdbc.queryForObject(
                "SELECT stok_minimum FROM stok_cache WHERE menu_id = ?", Integer.class, MENU);
    }
}
