package com.asqi.scholia_kantin_be.service.katalog;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.enums.SatuanMenu;
import com.asqi.scholia_kantin_be.service.integrasi.InfoMenu;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi katalog (PRD §7.1) dengan PostgreSQL nyata.
 *
 * <p>Menegakkan: soft delete, tenant-scoped 404, kategori terpakai tak bisa
 * dinonaktifkan, ubah harga tercatat di audit, dan adapter lookup menu
 * (dipakai kasir) mengembalikan harga &amp; status aktif yang benar.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class KatalogServiceIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;

    @Autowired
    private KatalogService katalog;

    @Autowired
    private MenuLookupKatalogAdapter lookup;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE menu, kategori_menu CASCADE");
    }

    @Test
    @DisplayName("buat kategori & menu, lalu lookup menu mengembalikan harga benar")
    void buatDanLookup() {
        var kategori = katalog.buatKategori(SEKOLAH, "Makanan Berat", 1, 1L);
        var menu = katalog.buatMenu(SEKOLAH, kategori.getId(), "Nasi Goreng",
                15_000L, SatuanMenu.PORSI, null, 5, 1L);

        assertThat(menu.getIsActive()).isTrue();
        assertThat(menu.getHargaJual()).isEqualTo(15_000L);

        InfoMenu info = lookup.cari(SEKOLAH, menu.getId());
        assertThat(info).isNotNull();
        assertThat(info.getNama()).isEqualTo("Nasi Goreng");
        assertThat(info.getHargaJual()).isEqualTo(15_000L);
        assertThat(info.getKategoriId()).isEqualTo(kategori.getId());
        assertThat(info.isAktif()).isTrue();
    }

    @Test
    @DisplayName("lookup menu sekolah lain = null (tenant-scoped, PRD §11.4)")
    void lookupIsolasiTenant() {
        var kategori = katalog.buatKategori(SEKOLAH, "Snack", 1, 1L);
        var menu = katalog.buatMenu(SEKOLAH, kategori.getId(), "Keripik",
                5_000L, SatuanMenu.PCS, null, 0, 1L);

        assertThat(lookup.cari(SEKOLAH_LAIN, menu.getId())).isNull();
    }

    @Test
    @DisplayName("ubah harga jual tercatat sebagai audit UBAH_HARGA_JUAL")
    void ubahHargaTercatatAudit() {
        var menu = katalog.buatMenu(SEKOLAH, null, "Es Teh",
                3_000L, SatuanMenu.BOTOL, null, 0, 1L);

        var diubah = katalog.ubahMenu(SEKOLAH, menu.getId(), null, null,
                4_000L, null, null, null, null, 7L);

        assertThat(diubah.getHargaJual()).isEqualTo(4_000L);
        Integer jumlah = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE aksi = 'UBAH_HARGA_JUAL' AND entitas_id = ?",
                Integer.class, String.valueOf(menu.getId()));
        assertThat(jumlah).isEqualTo(1);
    }

    @Test
    @DisplayName("kategori yang masih dipakai item aktif tidak boleh dinonaktifkan")
    void kategoriTerpakaiTidakBisaDinonaktifkan() {
        var kategori = katalog.buatKategori(SEKOLAH, "Minuman", 1, 1L);
        katalog.buatMenu(SEKOLAH, kategori.getId(), "Teh Manis",
                3_000L, SatuanMenu.PCS, null, 0, 1L);

        assertThatThrownBy(() -> katalog.nonaktifkanKategori(SEKOLAH, kategori.getId(), 1L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("masih dipakai");
    }

    @Test
    @DisplayName("soft delete: menu nonaktif tetap ada & lookup menandai nonaktif")
    void softDeleteMenu() {
        var menu = katalog.buatMenu(SEKOLAH, null, "Kue",
                2_000L, SatuanMenu.PCS, null, 0, 1L);
        katalog.nonaktifkanMenu(SEKOLAH, menu.getId(), 1L);

        InfoMenu info = lookup.cari(SEKOLAH, menu.getId());
        assertThat(info).isNotNull();
        assertThat(info.isAktif()).isFalse();
        // Nama/harga tetap tersimpan (riwayat).
        assertThat(info.getNama()).isEqualTo("Kue");
    }

    @Test
    @DisplayName("kategori/menu sekolah lain = 404 saat diubah")
    void tenantScoped404() {
        var menu = katalog.buatMenu(SEKOLAH, null, "Roti",
                2_500L, SatuanMenu.PCS, null, 0, 1L);

        assertThatThrownBy(() -> katalog.ubahMenu(SEKOLAH_LAIN, menu.getId(), null, null,
                3_000L, null, null, null, null, 1L))
                .isInstanceOf(NotFoundEntity.class);
    }

    @Test
    @DisplayName("nama kategori duplikat (beda besar-kecil) ditolak")
    void kategoriDuplikatDitolak() {
        katalog.buatKategori(SEKOLAH, "Snack", 1, 1L);

        assertThatThrownBy(() -> katalog.buatKategori(SEKOLAH, "snack", 2, 1L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("sudah ada");
    }

    @Test
    @DisplayName("stokBerjalan batch: hanya menu tenant yang terisi (baris stok dibuat otomatis)")
    void stokBerjalanBatch() {
        var menuA = katalog.buatMenu(SEKOLAH, null, "Nasi Goreng",
                15_000L, SatuanMenu.PORSI, null, 5, 1L);
        var menuB = katalog.buatMenu(SEKOLAH, null, "Es Teh",
                3_000L, SatuanMenu.BOTOL, null, 0, 1L);
        var menuC = katalog.buatMenu(SEKOLAH, null, "Kue",
                2_000L, SatuanMenu.PCS, null, 0, 1L);
        // Menu sekolah lain — tak boleh ikut terhitung.
        var menuSekolahLain = katalog.buatMenu(SEKOLAH_LAIN, null, "Roti",
                2_500L, SatuanMenu.PCS, null, 0, 1L);

        // Baris stok sudah dibuat otomatis saat buatMenu (#113) → cukup UPDATE.
        jdbc.update("UPDATE stok_cache SET stok = ? WHERE menu_id = ?", 12, menuA.getId());
        jdbc.update("UPDATE stok_cache SET stok = ? WHERE menu_id = ?", 4, menuB.getId());
        jdbc.update("UPDATE stok_cache SET stok = ? WHERE menu_id = ?", 99, menuSekolahLain.getId());

        var stok = katalog.stokBerjalan(SEKOLAH,
                java.util.List.of(menuA.getId(), menuB.getId(), menuC.getId()));

        assertThat(stok).containsEntry(menuA.getId(), 12);
        assertThat(stok).containsEntry(menuB.getId(), 4);
        // Menu C punya baris stok (dibuat otomatis) dengan stok 0.
        assertThat(stok).containsEntry(menuC.getId(), 0);
        assertThat(stok).doesNotContainKey(menuSekolahLain.getId());
    }

    @Test
    @DisplayName("stokBerjalan satu menu: 0 saat baru dibuat, nilai benar setelah diisi")
    void stokBerjalanSatuMenu() {
        var menu = katalog.buatMenu(SEKOLAH, null, "Bakso",
                10_000L, SatuanMenu.PORSI, null, 0, 1L);

        assertThat(katalog.stokBerjalan(SEKOLAH, menu.getId())).isZero();

        jdbc.update("UPDATE stok_cache SET stok = ? WHERE menu_id = ?", 8, menu.getId());

        assertThat(katalog.stokBerjalan(SEKOLAH, menu.getId())).isEqualTo(8);
        // Sekolah lain tak melihat stok sekolah pemilik baris.
        assertThat(katalog.stokBerjalan(SEKOLAH_LAIN, menu.getId())).isZero();
    }

    // ────────────────────────────────────────────────────────────────
    // #113 — sinkron stok_minimum menu → stok_cache
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("#113: buat menu → baris stok_cache dibuat dengan stok_minimum = menu.stok_minimum")
    void buatMenuSinkronStokMinimum() {
        var menu = katalog.buatMenu(SEKOLAH, null, "Nasi Uduk",
                8_000L, SatuanMenu.PORSI, null, 7, 1L);

        Integer min = jdbc.queryForObject(
                "SELECT stok_minimum FROM stok_cache WHERE menu_id = ?", Integer.class, menu.getId());
        assertThat(min).isEqualTo(7);
    }

    @Test
    @DisplayName("#113: ubah stok_minimum menu → stok_cache ikut tersinkron")
    void ubahMenuSinkronStokMinimum() {
        var menu = katalog.buatMenu(SEKOLAH, null, "Es Jeruk",
                5_000L, SatuanMenu.BOTOL, null, 3, 1L);

        katalog.ubahMenu(SEKOLAH, menu.getId(), null, null, null, null, null, 10, null, 1L);

        Integer min = jdbc.queryForObject(
                "SELECT stok_minimum FROM stok_cache WHERE menu_id = ?", Integer.class, menu.getId());
        assertThat(min).isEqualTo(10);
    }
}
