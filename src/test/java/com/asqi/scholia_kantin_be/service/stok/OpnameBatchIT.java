package com.asqi.scholia_kantin_be.service.stok;

import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.dto.OpnameBatchItemRequest;
import com.asqi.scholia_kantin_be.dto.OpnameBatchResponse;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi <b>opname batch</b> (PRD §7.3) dengan PostgreSQL nyata.
 *
 * <p>Menegakkan: (1) beberapa menu disesuaikan dalam satu batch, (2) idempotent
 * lewat nomor berita acara, (3) all-or-nothing (satu item gagal ⇒ tak ada yang
 * tersimpan), (4) flag {@code rusak} → jenis {@code BARANG_RUSAK}, (5) guard
 * menu lintas-tenant.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class OpnameBatchIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;
    private static final long MENU_A = 10L;
    private static final long MENU_B = 11L;
    private static final long MENU_C = 12L;

    @Autowired
    private LedgerStokService ledger;

    @Autowired
    private StokOperasiService operasi;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE mutasi_stok, stok_cache CASCADE");
        jdbc.execute("TRUNCATE TABLE menu CASCADE");
        menu(MENU_A, SEKOLAH, "Nasi Uduk");
        menu(MENU_B, SEKOLAH, "Es Jeruk");
        menu(MENU_C, SEKOLAH, "Ayam Goreng");
    }

    private void menu(long id, long sekolahId, String nama) {
        jdbc.update("INSERT INTO menu (id, sekolah_id, nama, harga_jual) VALUES (?, ?, ?, ?)",
                id, sekolahId, nama, 8_000L);
    }

    private OpnameBatchItemRequest item(long menuId, int qtyFisik, String alasan, Boolean rusak) {
        OpnameBatchItemRequest i = new OpnameBatchItemRequest();
        i.setMenuId(menuId);
        i.setQtyFisik(qtyFisik);
        i.setAlasan(alasan);
        i.setRusak(rusak);
        return i;
    }

    /** Seed stok awal via barang masuk (stok = qty). */
    private void isiStok(long menuId, int qty) {
        operasi.masukBarang(SEKOLAH, menuId, qty, 5_000, "BM-" + menuId + "-" + qty, 1L);
    }

    @Test
    @DisplayName("batch menyesuaikan banyak menu dalam satu request")
    void batchBanyakMenu() {
        isiStok(MENU_A, 20);
        isiStok(MENU_B, 30);
        isiStok(MENU_C, 15);

        OpnameBatchResponse hasil = operasi.opnameBatch(SEKOLAH, "OPN-20261006-001", List.of(
                item(MENU_A, 15, "Selisih hitung", false),
                item(MENU_B, 40, "Selisih hitung", false),
                item(MENU_C, 15, "Cocok", false)   // tanpa selisih
        ), 42L);

        assertThat(hasil.getJumlahBerubah()).isEqualTo(2);
        assertThat(hasil.getJumlahTanpaSelisih()).isEqualTo(1);
        assertThat(ledger.stok(SEKOLAH, MENU_A)).isEqualTo(15);
        assertThat(ledger.stok(SEKOLAH, MENU_B)).isEqualTo(40);
        assertThat(ledger.stok(SEKOLAH, MENU_C)).isEqualTo(15);

        // MENU_C tanpa selisih → tak ada baris ledger untuknya.
        Integer barisC = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_stok WHERE menu_id = ? AND referensi_tipe = 'OPNAME_BATCH'",
                Integer.class, MENU_C);
        assertThat(barisC).isZero();
    }

    @Test
    @DisplayName("batch idempoten: nomor berita acara sama tidak menerapkan dua kali")
    void batchIdempoten() {
        isiStok(MENU_A, 20);
        isiStok(MENU_B, 30);

        var items = List.of(
                item(MENU_A, 15, "Selisih", false),
                item(MENU_B, 25, "Selisih", false));

        var pertama = operasi.opnameBatch(SEKOLAH, "OPN-DUP-1", items, 42L);
        var kedua = operasi.opnameBatch(SEKOLAH, "OPN-DUP-1", items, 42L);

        assertThat(pertama.getJumlahBerubah()).isEqualTo(2);
        assertThat(kedua.getJumlahBerubah()).isEqualTo(2);
        // Stok TIDAK berubah pada replay.
        assertThat(ledger.stok(SEKOLAH, MENU_A)).isEqualTo(15);
        assertThat(ledger.stok(SEKOLAH, MENU_B)).isEqualTo(25);

        Integer baris = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_stok WHERE referensi_tipe = 'OPNAME_BATCH' AND referensi_id = 'OPN-DUP-1'",
                Integer.class);
        assertThat(baris).isEqualTo(2); // tetap 2, bukan 4
    }

    @Test
    @DisplayName("batch all-or-nothing: item ke-N gagal ⇒ item sebelumnya di-rollback")
    void batchAtomikAllOrNothing() {
        isiStok(MENU_A, 20);
        isiStok(MENU_B, 30);

        // Item 1 sah (MENU_A 20→15). Item 2 gagal DI TENGAH loop (alasan kosong,
        // dijaga di service) → seluruh transaksi harus di-rollback.
        assertThatThrownBy(() -> operasi.opnameBatch(SEKOLAH, "OPN-GAGAL-1", List.of(
                item(MENU_A, 15, "Selisih", false),
                item(MENU_B, 25, "   ", false)   // alasan kosong → InvalidOperationException
        ), 42L)).hasMessageContaining("Alasan");

        // Rollback penuh: MENU_A tetap 20 (bukan 15).
        assertThat(ledger.stok(SEKOLAH, MENU_A)).isEqualTo(20);
        assertThat(ledger.stok(SEKOLAH, MENU_B)).isEqualTo(30);
        Integer baris = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_stok WHERE referensi_tipe = 'OPNAME_BATCH'", Integer.class);
        assertThat(baris).isZero();
    }

    @Test
    @DisplayName("flag rusak → jenis BARANG_RUSAK (bukan OPNAME_KELUAR)")
    void flagRusakJenisBarangRusak() {
        isiStok(MENU_A, 20);
        isiStok(MENU_B, 20);

        var hasil = operasi.opnameBatch(SEKOLAH, "OPN-RUSAK-1", List.of(
                item(MENU_A, 18, "Basi", true),     // rusak → BARANG_RUSAK
                item(MENU_B, 19, "Selisih hitung", false) // audit → OPNAME_KELUAR
        ), 42L);

        assertThat(hasil.getItems().get(0).getJenis()).isEqualTo(JenisMutasiStok.BARANG_RUSAK);
        assertThat(hasil.getItems().get(1).getJenis()).isEqualTo(JenisMutasiStok.OPNAME_KELUAR);

        String jenisA = jdbc.queryForObject(
                "SELECT jenis FROM mutasi_stok WHERE menu_id = ? AND referensi_tipe = 'OPNAME_BATCH'",
                String.class, MENU_A);
        assertThat(jenisA).isEqualTo("BARANG_RUSAK");

        // Riwayat barang rusak bisa difilter terpisah.
        Integer rusak = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_stok WHERE jenis = 'BARANG_RUSAK'", Integer.class);
        assertThat(rusak).isEqualTo(1);
    }

    @Test
    @DisplayName("menu ganda dalam satu batch ditolak")
    void menuGandaDitolak() {
        isiStok(MENU_A, 20);

        assertThatThrownBy(() -> operasi.opnameBatch(SEKOLAH, "OPN-DUP-MENU", List.of(
                item(MENU_A, 15, "a", false),
                item(MENU_A, 10, "b", false)
        ), 42L)).hasMessageContaining("lebih dari sekali");
    }

    @Test
    @DisplayName("menu sekolah lain ditolak (404), batch tak tersimpan")
    void menuSekolahLainDitolak() {
        menu(900L, SEKOLAH_LAIN, "Milik Sekolah Lain");
        isiStok(MENU_A, 20);

        assertThatThrownBy(() -> operasi.opnameBatch(SEKOLAH, "OPN-TENANT", List.of(
                item(MENU_A, 15, "a", false),
                item(900L, 5, "b", false)
        ), 42L)).isInstanceOf(NotFoundEntity.class);

        assertThat(ledger.stok(SEKOLAH, MENU_A)).isEqualTo(20);
    }
}
