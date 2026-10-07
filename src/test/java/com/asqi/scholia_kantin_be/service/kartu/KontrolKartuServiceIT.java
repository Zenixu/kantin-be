package com.asqi.scholia_kantin_be.service.kartu;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.dto.TapRequest;
import com.asqi.scholia_kantin_be.dto.TapResponse;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import com.asqi.scholia_kantin_be.service.kasir.TapService;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import com.asqi.scholia_kantin_be.service.integrasi.InfoMenu;
import com.asqi.scholia_kantin_be.service.integrasi.KartuLookupPort;
import com.asqi.scholia_kantin_be.service.integrasi.MenuLookupPort;
import com.asqi.scholia_kantin_be.service.stok.LedgerStokService;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi kontrol kartu: blokir, limit harian &amp; blokir item
 * (PRD §6.1, §8.3, issue #40) dengan PostgreSQL nyata (Testcontainers).
 *
 * <p>Menegakkan yang <b>tidak</b> terblokir Q7: kontrol adalah data kantin-be,
 * sehingga blokir berlaku <b>instan</b> di jalur tap (PRD §11.11), limit &amp;
 * blokir item ditegakkan, serta tenant scoping &amp; audit (PRD §11.4/§11.7).
 */
@SpringBootTest
@Import({TestcontainersConfig.class, KontrolKartuServiceIT.FakePortConfig.class})
@EnabledIfDockerAvailable
class KontrolKartuServiceIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;
    private static final long SISWA = 7L;
    private static final long MENU_NASI = 10L;
    private static final long MENU_ES = 20L;
    private static final long KATEGORI_ES = 200L;
    private static final long TITIK = 1L;
    private static final long ADMIN = 900L;
    private static final String UID = "A1B2C3D4";

    @Autowired
    private KontrolKartuService kontrol;

    @Autowired
    private TapService tapService;

    @Autowired
    private LedgerSaldoService ledgerSaldo;

    @Autowired
    private LedgerStokService ledgerStok;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    private final IdentitasKantin petugas = IdentitasKantin.builder()
            .userId("555").sekolahId(SEKOLAH).build();

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE blokir_kartu, limit_harian, blokir_item, transaksi_item, "
                + "transaksi, sesi_kasir, titik_kasir, saldo_ledger, saldo_cache, mutasi_stok, "
                + "stok_cache, audit_log CASCADE");
        jdbc.update("INSERT INTO titik_kasir (id, sekolah_id, nama, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'Kasir 1', true, now(), now())", TITIK, SEKOLAH);

        tx.executeWithoutResult(s -> ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).subjekId(SISWA)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI).nominal(100_000).idempotencyKey("seed").build()));
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_NASI, 20, 4_000, "BARANG_MASUK", "BM-1", 1L));
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_ES, 20, 2_000, "BARANG_MASUK", "BM-2", 1L));
    }

    private TapRequest request(String key, int qtyNasi, int qtyEs) {
        TapRequest r = new TapRequest();
        r.setRfidUid(UID);
        r.setTitikKasirId(TITIK);
        r.setIdempotencyKey(key);
        var items = new java.util.ArrayList<TapRequest.ItemTap>();
        if (qtyNasi > 0) {
            var i = new TapRequest.ItemTap();
            i.setMenuId(MENU_NASI);
            i.setQty(qtyNasi);
            items.add(i);
        }
        if (qtyEs > 0) {
            var i = new TapRequest.ItemTap();
            i.setMenuId(MENU_ES);
            i.setQty(qtyEs);
            items.add(i);
        }
        r.setItems(items);
        return r;
    }

    // ────────────────────────────────────────────────────────────────
    // BLOKIR KARTU — instan tanpa cache (PRD §11.11)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("#40: blokir kartu berlaku INSTAN — tap berikutnya ditolak")
    void blokirInstan() {
        // Tap pertama lolos (belum diblokir).
        TapResponse t1 = tapService.tap(SEKOLAH, petugas, request("t1", 1, 0));
        assertThat(t1.getTotal()).isEqualTo(8_000L);

        // Blokir — lalu tap berikutnya (detik yang sama) HARUS ditolak.
        kontrol.ubahBlokir(SEKOLAH, SubjekTipe.SISWA, SISWA, true, "Atas permintaan ortu", ADMIN);

        assertThatThrownBy(() -> tapService.tap(SEKOLAH, petugas, request("t2", 1, 0)))
                .hasMessageContaining("Kartu diblokir, hubungi orang tua");

        // Saldo tidak berkurang oleh tap yang ditolak.
        assertThat(ledgerSaldo.saldo(SEKOLAH, SubjekTipe.SISWA, SISWA)).isEqualTo(92_000L);
    }

    @Test
    @DisplayName("#40: buka blokir → tap lolos lagi")
    void bukaBlokir() {
        kontrol.ubahBlokir(SEKOLAH, SubjekTipe.SISWA, SISWA, true, "blokir", ADMIN);
        kontrol.ubahBlokir(SEKOLAH, SubjekTipe.SISWA, SISWA, false, "dibuka", ADMIN);

        TapResponse t = tapService.tap(SEKOLAH, petugas, request("t1", 1, 0));
        assertThat(t.getTotal()).isEqualTo(8_000L);
    }

    @Test
    @DisplayName("#40: blokir tercatat di audit (nilai lama → baru)")
    void blokirTercatatAudit() {
        kontrol.ubahBlokir(SEKOLAH, SubjekTipe.SISWA, SISWA, true, "hilang", ADMIN);

        Integer audit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE aksi = 'BLOKIR_KARTU'", Integer.class);
        assertThat(audit).isEqualTo(1);
    }

    // ────────────────────────────────────────────────────────────────
    // LIMIT HARIAN (PRD §6.1 tahap 5, §8.3)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("#40: limit harian ditegakkan — melebihi limit ditolak")
    void limitHarianDitegakkan() {
        kontrol.setLimit(SEKOLAH, SubjekTipe.SISWA, SISWA, 10_000L, ADMIN);

        // 2×8.000 = 16.000 > 10.000 → ditolak (sisa Rp 10000).
        assertThatThrownBy(() -> tapService.tap(SEKOLAH, petugas, request("t1", 2, 0)))
                .hasMessageContaining("Melebihi limit harian");

        // 1×8.000 = 8.000 ≤ 10.000 → lolos.
        TapResponse t = tapService.tap(SEKOLAH, petugas, request("t2", 1, 0));
        assertThat(t.getTotal()).isEqualTo(8_000L);
    }

    @Test
    @DisplayName("#40: nominal null = tanpa limit")
    void limitNullTanpaLimit() {
        kontrol.setLimit(SEKOLAH, SubjekTipe.SISWA, SISWA, null, ADMIN);
        TapResponse t = tapService.tap(SEKOLAH, petugas, request("t1", 5, 0)); // 40.000
        assertThat(t.getTotal()).isEqualTo(40_000L);
    }

    @Test
    @DisplayName("#40: limit harian hanya untuk siswa — Kartu Tamu ditolak")
    void limitKartuTamuDitolak() {
        assertThatThrownBy(() ->
                kontrol.setLimit(SEKOLAH, SubjekTipe.KARTU_TAMU, 99L, 10_000L, ADMIN))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("siswa");
    }

    // ────────────────────────────────────────────────────────────────
    // BLOKIR ITEM / KATEGORI (PRD §6.1 tahap 3, §8.3)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("#40: blokir item — tap item yang diblokir ditolak")
    void blokirItemDitegakkan() {
        kontrol.ubahBlokirItem(SEKOLAH, SubjekTipe.SISWA, SISWA, MENU_NASI, null, true, ADMIN);

        assertThatThrownBy(() -> tapService.tap(SEKOLAH, petugas, request("t1", 1, 0)))
                .hasMessageContaining("Nasi Uduk").hasMessageContaining("diblokir oleh orang tua");

        // Item lain tetap bisa dibeli.
        TapResponse t = tapService.tap(SEKOLAH, petugas, request("t2", 0, 1));
        assertThat(t.getTotal()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("#40: blokir kategori — tap item sekategori ditolak")
    void blokirKategoriDitegakkan() {
        kontrol.ubahBlokirItem(SEKOLAH, SubjekTipe.SISWA, SISWA, null, KATEGORI_ES, true, ADMIN);

        assertThatThrownBy(() -> tapService.tap(SEKOLAH, petugas, request("t1", 0, 1)))
                .hasMessageContaining("Es Jeruk").hasMessageContaining("diblokir oleh orang tua");
    }

    @Test
    @DisplayName("#40: blokir item harus tepat satu target (menu ATAU kategori)")
    void blokirItemDuaTargetDitolak() {
        assertThatThrownBy(() ->
                kontrol.ubahBlokirItem(SEKOLAH, SubjekTipe.SISWA, SISWA, MENU_NASI, KATEGORI_ES, true, ADMIN))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("tepat satu");
        assertThatThrownBy(() ->
                kontrol.ubahBlokirItem(SEKOLAH, SubjekTipe.SISWA, SISWA, null, null, true, ADMIN))
                .isInstanceOf(InvalidOperationException.class);
    }

    // ────────────────────────────────────────────────────────────────
    // BACA & TENANT SCOPING
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("#40: ringkasan kontrol mencerminkan blokir/limit/item")
    void ringkasanKontrol() {
        kontrol.ubahBlokir(SEKOLAH, SubjekTipe.SISWA, SISWA, true, "hilang", ADMIN);
        kontrol.setLimit(SEKOLAH, SubjekTipe.SISWA, SISWA, 25_000L, ADMIN);
        kontrol.ubahBlokirItem(SEKOLAH, SubjekTipe.SISWA, SISWA, MENU_NASI, null, true, ADMIN);

        var k = kontrol.kontrol(SEKOLAH, SubjekTipe.SISWA, SISWA);
        assertThat(k.isDiblokir()).isTrue();
        assertThat(k.getAlasanBlokir()).isEqualTo("hilang");
        assertThat(k.getLimitHarian()).isEqualTo(25_000L);
        assertThat(k.isAdaLimit()).isTrue();
        assertThat(k.getMenuDiblokir()).containsExactly(MENU_NASI);
        assertThat(k.getKategoriDiblokir()).isEmpty();
    }

    @Test
    @DisplayName("#40: tenant scoping — kontrol sekolah lain tidak bocor")
    void kontrolTenantScoped() {
        kontrol.ubahBlokir(SEKOLAH, SubjekTipe.SISWA, SISWA, true, "hilang", ADMIN);

        var lain = kontrol.kontrol(SEKOLAH_LAIN, SubjekTipe.SISWA, SISWA);
        assertThat(lain.isDiblokir()).isFalse();
        assertThat(lain.isAdaLimit()).isFalse();
        assertThat(lain.getMenuDiblokir()).isEmpty();
    }

    /** Fake port: kartu siswa "Budi" & katalog dua menu (menyembunyikan Q7/Fase 5). */
    @TestConfiguration(proxyBeanMethods = false)
    static class FakePortConfig {

        @Bean
        @Primary
        KartuLookupPort kartuLookupFake() {
            return (sekolahId, rfidUid) -> {
                if (UID.equals(rfidUid) && sekolahId == SEKOLAH) {
                    return InfoKartu.builder()
                            .dikenal(true).diblokir(false)
                            .subjekTipe(SubjekTipe.SISWA).subjekId(SISWA)
                            .nama("Budi").kelas("5A")
                            .limitHarian(1_000_000L)
                            .build();
                }
                return InfoKartu.tidakDikenal();
            };
        }

        @Bean
        @Primary
        MenuLookupPort menuLookupFake() {
            Map<Long, InfoMenu> peta = Map.of(
                    MENU_NASI, InfoMenu.builder().menuId(MENU_NASI).nama("Nasi Uduk")
                            .hargaJual(8_000L).kategoriId(100L).aktif(true).build(),
                    MENU_ES, InfoMenu.builder().menuId(MENU_ES).nama("Es Jeruk")
                            .hargaJual(5_000L).kategoriId(KATEGORI_ES).aktif(true).build());
            return (sekolahId, menuId) -> peta.get(menuId);
        }
    }
}
