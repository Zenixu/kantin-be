package com.asqi.scholia_kantin_be.service.stok;

import com.asqi.scholia_kantin_be.dto.HalamanResponse;
import com.asqi.scholia_kantin_be.dto.RiwayatStokItem;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
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
 * Uji integrasi <b>riwayat mutasi stok</b> ({@code GET /api/stok/riwayat},
 * PRD §9.5) dengan PostgreSQL nyata (Testcontainers).
 *
 * <p>Regresi issue <b>#198</b>: endpoint ini dulu <b>selalu HTTP 500</b>
 * ({@code could not determine data type of parameter}) karena
 * {@code MutasiStokRepository.riwayat()} memakai pola filter opsional
 * {@code :x IS NULL OR ...} — Hibernate mengirim parameter null tanpa tipe
 * eksplisit sehingga PostgreSQL menolaknya. Uji ini menembus query ke DB
 * nyata (bukan mock controller seperti {@code StokControllerTest}) sehingga
 * <b>gagal sebelum perbaikan, hijau sesudah</b>.
 *
 * <p>Menutup seluruh kombinasi filter acceptance criteria #198: tanpa filter,
 * {@code menuId}, {@code jenis}, rentang waktu, dan kombinasinya — termasuk
 * data tenant-scoped, urutan {@code id DESC}, dan paginasi.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
@DisplayName("Stok riwayat (#198) — semua kombinasi filter ke DB nyata")
class StokRiwayatIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;
    private static final long MENU_A = 10L;
    private static final long MENU_B = 11L;

    @Autowired
    private StokOperasiService operasi;

    @Autowired
    private JdbcTemplate jdbc;

    private OffsetDateTime t0;

    @BeforeEach
    void siapkan() {
        jdbc.execute("TRUNCATE TABLE mutasi_stok, stok_cache, menu, kategori_menu CASCADE");

        jdbc.update("INSERT INTO menu (id, sekolah_id, nama, harga_jual, satuan, stok_minimum, "
                + "is_active, created_at, updated_at) VALUES (?, ?, 'Menu A', 5000, 'PCS', 0, true, now(), now())",
                MENU_A, SEKOLAH);
        jdbc.update("INSERT INTO menu (id, sekolah_id, nama, harga_jual, satuan, stok_minimum, "
                + "is_active, created_at, updated_at) VALUES (?, ?, 'Menu B', 5000, 'PCS', 0, true, now(), now())",
                MENU_B, SEKOLAH);

        t0 = OffsetDateTime.now().minusDays(1).withNano(0);
        // Ledger sekolah 1: menu A (BARANG_MASUK), menu A (PENJUALAN), menu B (BARANG_MASUK).
        mutasi(1001, SEKOLAH, MENU_A, "MASUK", "BARANG_MASUK", 10, 10, 5_000L, 5_000L,
                "BARANG_MASUK", "BM-A1", t0, "Bu Sri");
        mutasi(1002, SEKOLAH, MENU_A, "KELUAR", "PENJUALAN", 3, 7, 5_000L, null,
                "TRANSAKSI", "TRX-1", t0.plusHours(1), null);
        mutasi(1003, SEKOLAH, MENU_B, "MASUK", "BARANG_MASUK", 20, 20, 4_000L, 4_000L,
                "BARANG_MASUK", "BM-B1", t0.plusHours(2), "Pak Kasir");
        // Baris tenant lain — tak boleh bocor (menu_id beda karena PK stok_cache = menu_id).
        mutasi(1004, SEKOLAH_LAIN, 12L, "MASUK", "BARANG_MASUK", 7, 7, 1_000L, 1_000L,
                "BARANG_MASUK", "BM-LAIN", t0.plusHours(3), null);
    }

    private void mutasi(long id, long sekolahId, long menuId, String arah, String jenis, int qty,
                        int stokSetelah, Long hpp, Long hargaBeli, String refTipe, String refId,
                        OffsetDateTime waktu, String aktorNama) {
        jdbc.update("INSERT INTO mutasi_stok (id, sekolah_id, menu_id, arah, jenis, qty, "
                + "stok_setelah, hpp_snapshot, harga_beli_satuan, referensi_tipe, referensi_id, "
                + "aktor_id, aktor_nama, waktu, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)",
                id, sekolahId, menuId, arah, jenis, qty, stokSetelah, hpp, hargaBeli, refTipe,
                refId, aktorNama, waktu, waktu);
    }

    private HalamanResponse<RiwayatStokItem> riwayat(Long menuId, JenisMutasiStok jenis,
                                                     OffsetDateTime dari, OffsetDateTime sampai,
                                                     int halaman, int ukuran) {
        return operasi.riwayat(SEKOLAH, menuId, jenis, dari, sampai, halaman, ukuran);
    }

    @Test
    @DisplayName("#198: tanpa filter → 200 (dulu 500) & tenant-scoped, id DESC")
    void tanpaFilter() {
        HalamanResponse<RiwayatStokItem> hasil = riwayat(null, null, null, null, 0, 20);

        assertThat(hasil.getItems()).hasSize(3);
        assertThat(hasil.getTotal()).isEqualTo(3);
        assertThat(hasil.getItems()).extracting(RiwayatStokItem::getId)
                .isSortedAccordingTo((a, b) -> Long.compare(b, a));
        // Baris sekolah lain tak bocor.
        assertThat(hasil.getItems()).noneMatch(i -> i.getId() == 1004L);
    }

    @Test
    @DisplayName("#198: filter menuId saja → hanya mutasi menu itu")
    void filterMenuId() {
        HalamanResponse<RiwayatStokItem> hasil = riwayat(MENU_A, null, null, null, 0, 20);

        assertThat(hasil.getItems()).hasSize(2);
        assertThat(hasil.getItems()).allMatch(i -> i.getMenuId().equals(MENU_A));
    }

    @Test
    @DisplayName("#198: filter jenis saja → hanya jenis itu")
    void filterJenis() {
        HalamanResponse<RiwayatStokItem> hasil =
                riwayat(null, JenisMutasiStok.BARANG_MASUK, null, null, 0, 20);

        assertThat(hasil.getItems()).hasSize(2);
        assertThat(hasil.getItems()).allMatch(i -> i.getJenis() == JenisMutasiStok.BARANG_MASUK);
    }

    @Test
    @DisplayName("#198: filter rentang waktu saja")
    void filterRentangWaktu() {
        HalamanResponse<RiwayatStokItem> hasil = riwayat(null, null,
                t0.minusHours(1), t0.plusHours(4), 0, 20);
        assertThat(hasil.getItems()).hasSize(3);

        // Rentang di masa lalu → kosong, bukan error.
        HalamanResponse<RiwayatStokItem> kosong = riwayat(null, null,
                t0.minusDays(10), t0.minusDays(5), 0, 20);
        assertThat(kosong.getItems()).isEmpty();
    }

    @Test
    @DisplayName("#198: kombinasi menuId + jenis + rentang waktu")
    void filterKombinasi() {
        HalamanResponse<RiwayatStokItem> hasil = riwayat(MENU_A, JenisMutasiStok.BARANG_MASUK,
                t0.minusHours(1), t0.plusHours(4), 0, 20);

        assertThat(hasil.getItems()).hasSize(1);
        assertThat(hasil.getItems()).allMatch(i -> i.getMenuId().equals(MENU_A)
                && i.getJenis() == JenisMutasiStok.BARANG_MASUK);
    }

    @Test
    @DisplayName("#198: kombinasi jenis + rentang waktu (tanpa menuId)")
    void filterJenisDanRentang() {
        HalamanResponse<RiwayatStokItem> hasil = riwayat(null, JenisMutasiStok.BARANG_MASUK,
                t0.minusHours(1), t0.plusHours(4), 0, 20);

        assertThat(hasil.getItems()).hasSize(2); // BM-A1 + BM-B1
    }

    @Test
    @DisplayName("#198: filter menuId + rentang waktu (tanpa jenis)")
    void filterMenuDanRentang() {
        HalamanResponse<RiwayatStokItem> hasil = riwayat(MENU_B, null,
                t0.minusHours(1), t0.plusHours(4), 0, 20);

        assertThat(hasil.getItems()).hasSize(1);
        assertThat(hasil.getItems().get(0).getMenuId()).isEqualTo(MENU_B);
    }

    @Test
    @DisplayName("#198: paginasi benar (ukuran 1 → halaman 0 & 1 beda, total tetap)")
    void paginasi() {
        HalamanResponse<RiwayatStokItem> hal0 = riwayat(null, null, null, null, 0, 1);
        HalamanResponse<RiwayatStokItem> hal1 = riwayat(null, null, null, null, 1, 1);

        assertThat(hal0.getItems()).hasSize(1);
        assertThat(hal1.getItems()).hasSize(1);
        assertThat(hal0.getTotal()).isEqualTo(3);
        assertThat(hal0.getTotalHalaman()).isEqualTo(3);
        assertThat(hal0.getItems().get(0).getId()).isNotEqualTo(hal1.getItems().get(0).getId());
    }

    @Test
    @DisplayName("#198: BARANG_MASUK menyertakan sisaDapatDibalik (query batch ikut tereksekusi)")
    void barangMasukSisaDapatDibalik() {
        HalamanResponse<RiwayatStokItem> hasil =
                riwayat(MENU_A, JenisMutasiStok.BARANG_MASUK, null, null, 0, 20);

        assertThat(hasil.getItems()).hasSize(1);
        RiwayatStokItem bm = hasil.getItems().get(0);
        assertThat(bm.isDapatDibalik()).isTrue();
        assertThat(bm.getSisaDapatDibalik()).isEqualTo(10);
        assertThat(bm.getSudahDibalik()).isZero();
        assertThat(bm.getMenuNama()).isEqualTo("Menu A");
    }
}
