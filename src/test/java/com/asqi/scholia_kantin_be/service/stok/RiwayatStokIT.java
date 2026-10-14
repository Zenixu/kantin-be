package com.asqi.scholia_kantin_be.service.stok;

import com.asqi.scholia_kantin_be.dto.HalamanResponse;
import com.asqi.scholia_kantin_be.dto.RiwayatStokItem;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji integrasi {@code GET /api/stok/riwayat} (PRD §9.5) dengan PostgreSQL nyata.
 *
 * <p><b>Regresi #198.</b> Query JPQL berpola {@code :x IS NULL OR ...} membuat
 * Hibernate mengirim parameter {@code null} <i>tanpa tipe</i>, sehingga PostgreSQL
 * menolak: {@code could not determine data type of parameter $4} → endpoint
 * selalu <b>HTTP 500</b> (filter kosong maupun terisi).
 *
 * <p>Bug ini <b>tidak tertangkap</b> uji controller ber-mock karena SQL-nya tak
 * pernah dieksekusi (lihat #199). Uji ini menutup semua kombinasi filter dan
 * gagal pada kode lama, hijau setelah perbaikan.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class RiwayatStokIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;
    private static final long MENU_A = 10L;
    private static final long MENU_B = 11L;

    private static final OffsetDateTime W1 = OffsetDateTime.parse("2026-10-01T08:00:00+07:00");
    private static final OffsetDateTime W2 = OffsetDateTime.parse("2026-10-02T09:00:00+07:00");
    private static final OffsetDateTime W3 = OffsetDateTime.parse("2026-10-03T10:00:00+07:00");

    @Autowired
    private StokOperasiService operasi;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void siapkan() {
        TenantContext.clear();
        jdbc.execute("TRUNCATE TABLE mutasi_stok, stok_cache CASCADE");
        jdbc.execute("TRUNCATE TABLE menu CASCADE");

        menu(MENU_A, SEKOLAH, "Nasi Goreng");
        menu(MENU_B, SEKOLAH, "Es Teh");

        // id=1: menu A, BARANG_MASUK (10-01)
        mutasi(1L, SEKOLAH, MENU_A, "MASUK", "BARANG_MASUK", 10, 10, W1);
        // id=2: menu B, PENJUALAN (10-02)
        mutasi(2L, SEKOLAH, MENU_B, "KELUAR", "PENJUALAN", 2, 8, W2);
        // id=3: menu A, PENJUALAN (10-03)
        mutasi(3L, SEKOLAH, MENU_A, "KELUAR", "PENJUALAN", 1, 9, W3);
        // id=4: SEKOLAH LAIN — tidak boleh bocor ke tenant SEKOLAH
        mutasi(4L, SEKOLAH_LAIN, MENU_A, "MASUK", "BARANG_MASUK", 5, 5, W1);
    }

    // ────────────────────────────────────────────────────────────────
    // Semua kombinasi filter — inti regresi #198
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("tanpa filter sama sekali → 200, semua baris tenant, urut id DESC")
    void tanpaFilter() {
        HalamanResponse<RiwayatStokItem> hasil = operasi.riwayat(SEKOLAH, null, null, null, null, 0, 20);

        assertThat(hasil.getItems()).extracting(RiwayatStokItem::getId)
                .containsExactly(3L, 2L, 1L);
        assertThat(hasil.getTotal()).isEqualTo(3L);
        assertThat(hasil.getItems()).extracting(RiwayatStokItem::getMenuNama)
                .containsExactly("Nasi Goreng", "Es Teh", "Nasi Goreng");
    }

    @Test
    @DisplayName("filter menuId saja → 200")
    void filterMenuId() {
        HalamanResponse<RiwayatStokItem> hasil = operasi.riwayat(SEKOLAH, MENU_A, null, null, null, 0, 20);

        assertThat(hasil.getItems()).extracting(RiwayatStokItem::getId).containsExactly(3L, 1L);
        assertThat(hasil.getTotal()).isEqualTo(2L);
    }

    @Test
    @DisplayName("filter jenis saja → 200")
    void filterJenis() {
        HalamanResponse<RiwayatStokItem> hasil =
                operasi.riwayat(SEKOLAH, null, JenisMutasiStok.PENJUALAN, null, null, 0, 20);

        assertThat(hasil.getItems()).extracting(RiwayatStokItem::getId).containsExactly(3L, 2L);
        assertThat(hasil.getTotal()).isEqualTo(2L);
    }

    @Test
    @DisplayName("filter rentang waktu saja → 200")
    void filterRentangWaktu() {
        HalamanResponse<RiwayatStokItem> hasil = operasi.riwayat(
                SEKOLAH, null, null, W2, W3, 0, 20);

        // >= 10-02 09:00 dan < 10-03 10:00 → hanya id=2
        assertThat(hasil.getItems()).extracting(RiwayatStokItem::getId).containsExactly(2L);
        assertThat(hasil.getTotal()).isEqualTo(1L);
    }

    @Test
    @DisplayName("filter kombinasi (menuId + jenis + rentang) → 200")
    void filterKombinasi() {
        HalamanResponse<RiwayatStokItem> hasil = operasi.riwayat(
                SEKOLAH, MENU_A, JenisMutasiStok.PENJUALAN, W1, W3.plusDays(1), 0, 20);

        assertThat(hasil.getItems()).extracting(RiwayatStokItem::getId).containsExactly(3L);
        assertThat(hasil.getTotal()).isEqualTo(1L);
    }

    @Test
    @DisplayName("tenant-scoped: sekolah lain tidak bocor")
    void tenantScoped() {
        HalamanResponse<RiwayatStokItem> hasil = operasi.riwayat(SEKOLAH_LAIN, null, null, null, null, 0, 20);

        assertThat(hasil.getItems()).extracting(RiwayatStokItem::getId).containsExactly(4L);
        assertThat(hasil.getTotal()).isEqualTo(1L);
    }

    @Test
    @DisplayName("paginasi: halaman & ukuran dihormati, urut id DESC")
    void paginasi() {
        HalamanResponse<RiwayatStokItem> hal1 = operasi.riwayat(SEKOLAH, null, null, null, null, 0, 2);
        HalamanResponse<RiwayatStokItem> hal2 = operasi.riwayat(SEKOLAH, null, null, null, null, 1, 2);

        assertThat(hal1.getItems()).extracting(RiwayatStokItem::getId).containsExactly(3L, 2L);
        assertThat(hal2.getItems()).extracting(RiwayatStokItem::getId).containsExactly(1L);
        assertThat(hal1.getTotal()).isEqualTo(3L);
        assertThat(hal1.getTotalHalaman()).isEqualTo(2);
    }

    @Test
    @DisplayName("filter tanpa hasil → 200 dengan daftar kosong (bukan 500)")
    void filterTanpaHasil() {
        HalamanResponse<RiwayatStokItem> hasil =
                operasi.riwayat(SEKOLAH, MENU_B, JenisMutasiStok.BARANG_MASUK, null, null, 0, 20);

        assertThat(hasil.getItems()).isEmpty();
        assertThat(hasil.getTotal()).isZero();
    }

    // ────────────────────────────────────────────────────────────────
    // Helper seed
    // ────────────────────────────────────────────────────────────────

    private void menu(long id, long sekolah, String nama) {
        jdbc.update("""
                INSERT INTO menu (id, sekolah_id, nama, harga_jual)
                VALUES (?, ?, ?, 10000)
                """, id, sekolah, nama);
    }

    private void mutasi(long id, long sekolah, long menuId, String arah, String jenis,
                        int qty, int stokSetelah, OffsetDateTime waktu) {
        jdbc.update("""
                INSERT INTO mutasi_stok (id, sekolah_id, menu_id, arah, jenis, qty,
                                         stok_setelah, referensi_tipe, referensi_id, waktu)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'BARANG_MASUK', ?, ?)
                """, id, sekolah, menuId, arah, jenis, qty, stokSetelah, "REF-" + id, waktu);
    }
}
