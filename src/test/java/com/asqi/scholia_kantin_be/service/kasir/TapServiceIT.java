package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.dto.TapRequest;
import com.asqi.scholia_kantin_be.dto.TapResponse;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import com.asqi.scholia_kantin_be.service.integrasi.InfoMenu;
import com.asqi.scholia_kantin_be.service.integrasi.KartuLookupPort;
import com.asqi.scholia_kantin_be.service.integrasi.MenuLookupPort;
import com.asqi.scholia_kantin_be.dto.PengaturanKantinRequest;
import com.asqi.scholia_kantin_be.service.konfigurasi.PengaturanKantinService;
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
 * Uji integrasi alur tap end-to-end (PRD §6.1–6.2) dengan PostgreSQL nyata.
 *
 * <p>Memakai <b>fake</b> {@link KartuLookupPort} &amp; {@link MenuLookupPort}
 * (menyembunyikan ketergantungan Q7 &amp; Fase 5), tetapi ledger, stok, sesi,
 * transaksi, idempotency, dan locking berjalan nyata.
 */
@SpringBootTest
@Import({TestcontainersConfig.class, TapServiceIT.FakePortConfig.class})
@EnabledIfDockerAvailable
class TapServiceIT {

    private static final long SEKOLAH = 1L;
    private static final long SISWA = 7L;
    private static final long MENU_NASI = 10L;
    private static final long MENU_ES = 20L;
    private static final long TITIK = 1L;
    private static final String UID = "A1B2C3D4";

    @Autowired
    private TapService tapService;

    @Autowired
    private LedgerSaldoService ledgerSaldo;

    @Autowired
    private LedgerStokService ledgerStok;

    @Autowired
    private PengaturanKantinService pengaturan;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    private final IdentitasKantin petugas = IdentitasKantin.builder()
            .userId("555").nama("Petugas A").sekolahId(SEKOLAH).build();

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE transaksi_menunggu_konfirmasi, transaksi_item, transaksi, "
                + "sesi_kasir, titik_kasir, saldo_ledger, saldo_cache, mutasi_stok, stok_cache, "
                + "sekolah_kantin_config CASCADE");
        jdbc.update("INSERT INTO titik_kasir (id, sekolah_id, nama, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'Kasir 1', true, now(), now())", TITIK, SEKOLAH);

        // Modal awal: saldo siswa 50.000 & stok menu.
        tx.executeWithoutResult(s -> ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).subjekId(SISWA)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI).nominal(50_000).idempotencyKey("seed-saldo").build()));
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
            var nasi = new TapRequest.ItemTap();
            nasi.setMenuId(MENU_NASI);
            nasi.setQty(qtyNasi);
            items.add(nasi);
        }
        if (qtyEs > 0) {
            var es = new TapRequest.ItemTap();
            es.setMenuId(MENU_ES);
            es.setQty(qtyEs);
            items.add(es);
        }
        r.setItems(items);
        return r;
    }

    @Test
    @DisplayName("tap sukses — potong saldo, kurangi stok, catat transaksi + snapshot HPP")
    void tapSukses() {
        TapResponse resp = tapService.tap(SEKOLAH, petugas, request("tap-1", 2, 1));

        // total = 2×8000 + 1×5000 = 21.000
        assertThat(resp.getTotal()).isEqualTo(21_000L);
        assertThat(resp.getSaldoSisa()).isEqualTo(29_000L);
        assertThat(resp.getNama()).isEqualTo("Budi");
        assertThat(resp.getKelas()).isEqualTo("5A");

        assertThat(ledgerSaldo.saldo(SEKOLAH, SubjekTipe.SISWA, SISWA)).isEqualTo(29_000L);
        assertThat(ledgerStok.stok(SEKOLAH, MENU_NASI)).isEqualTo(18);
        assertThat(ledgerStok.stok(SEKOLAH, MENU_ES)).isEqualTo(19);

        // Snapshot HPP & total_hpp benar.
        Long totalHpp = jdbc.queryForObject(
                "SELECT total_hpp FROM transaksi WHERE id = ?", Long.class, resp.getTransaksiId());
        assertThat(totalHpp).isEqualTo(2 * 4_000L + 1 * 2_000L); // 10.000
    }

    @Test
    @DisplayName("idempotency — tap ganda dengan key sama hanya memotong sekali")
    void tapGandaIdempotent() {
        TapResponse pertama = tapService.tap(SEKOLAH, petugas, request("tap-dup", 1, 0));
        TapResponse kedua = tapService.tap(SEKOLAH, petugas, request("tap-dup", 1, 0));

        assertThat(kedua.getTransaksiId()).isEqualTo(pertama.getTransaksiId());
        assertThat(ledgerSaldo.saldo(SEKOLAH, SubjekTipe.SISWA, SISWA)).isEqualTo(42_000L); // hanya sekali 8.000
        assertThat(ledgerStok.stok(SEKOLAH, MENU_NASI)).isEqualTo(19);

        Integer jumlahTrx = jdbc.queryForObject(
                "SELECT COUNT(*) FROM transaksi WHERE idempotency_key = 'tap-dup'", Integer.class);
        assertThat(jumlahTrx).isEqualTo(1);
    }

    @Test
    @DisplayName("saldo kurang — transaksi TIDAK dibuat, saldo & stok tidak berubah")
    void saldoKurangTidakMengubahApaPun() {
        // total 10×8000 = 80.000 > saldo 50.000
        assertThatThrownBy(() -> tapService.tap(SEKOLAH, petugas, request("tap-kurang", 10, 0)))
                .hasMessageContaining("Saldo kurang");

        assertThat(ledgerSaldo.saldo(SEKOLAH, SubjekTipe.SISWA, SISWA)).isEqualTo(50_000L);
        assertThat(ledgerStok.stok(SEKOLAH, MENU_NASI)).isEqualTo(20);
        Integer trx = jdbc.queryForObject("SELECT COUNT(*) FROM transaksi", Integer.class);
        assertThat(trx).isZero();
    }

    @Test
    @DisplayName("stok tidak cukup — transaksi TIDAK dibuat")
    void stokKurangTidakMengubahApaPun() {
        assertThatThrownBy(() -> tapService.tap(SEKOLAH, petugas, request("tap-stok", 100, 0)))
                .hasMessageContaining("Stok");

        assertThat(ledgerSaldo.saldo(SEKOLAH, SubjekTipe.SISWA, SISWA)).isEqualTo(50_000L);
        Integer trx = jdbc.queryForObject("SELECT COUNT(*) FROM transaksi", Integer.class);
        assertThat(trx).isZero();
    }

    // ────────────────────────────────────────────────────────────────
    // KONFIRMASI MANUAL (#111, PRD §6.1/§9.1)
    // ────────────────────────────────────────────────────────────────

    private void aktifkanKonfirmasiManual() {
        PengaturanKantinRequest req = new PengaturanKantinRequest();
        req.setKonfirmasiManual(true);
        pengaturan.ubah(SEKOLAH, req, 900L);
    }

    @Test
    @DisplayName("#111: konfirmasi manual aktif → tap TIDAK langsung memotong saldo/stok")
    void konfirmasiManualTidakLangsungPotong() {
        aktifkanKonfirmasiManual();

        TapResponse resp = tapService.tap(SEKOLAH, petugas, request("tap-konf", 2, 1));

        assertThat(resp.isMenungguKonfirmasi()).isTrue();
        assertThat(resp.getTransaksiId()).isNull();
        assertThat(resp.getPendingId()).isNotNull();
        assertThat(resp.getTotal()).isEqualTo(21_000L);

        // Belum ada perubahan apa pun di ledger.
        assertThat(ledgerSaldo.saldo(SEKOLAH, SubjekTipe.SISWA, SISWA)).isEqualTo(50_000L);
        assertThat(ledgerStok.stok(SEKOLAH, MENU_NASI)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaksi", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM transaksi_menunggu_konfirmasi WHERE status = 'MENUNGGU'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("#111: konfirmasi tap pending → barulah saldo/stok terpotong")
    void konfirmasiMemotongSaldoStok() {
        aktifkanKonfirmasiManual();
        TapResponse pending = tapService.tap(SEKOLAH, petugas, request("tap-konf2", 1, 0));

        TapResponse resp = tapService.konfirmasi(SEKOLAH, pending.getPendingId(), petugas);

        assertThat(resp.isMenungguKonfirmasi()).isFalse();
        assertThat(resp.getTransaksiId()).isNotNull();
        assertThat(resp.getSaldoSisa()).isEqualTo(42_000L);
        assertThat(ledgerSaldo.saldo(SEKOLAH, SubjekTipe.SISWA, SISWA)).isEqualTo(42_000L);
        assertThat(ledgerStok.stok(SEKOLAH, MENU_NASI)).isEqualTo(19);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM transaksi_menunggu_konfirmasi WHERE id = ?",
                String.class, pending.getPendingId())).isEqualTo("DIKONFIRMASI");
    }

    @Test
    @DisplayName("#111: konfirmasi ganda idempoten — hanya sekali potong")
    void konfirmasiGandaIdempoten() {
        aktifkanKonfirmasiManual();
        TapResponse pending = tapService.tap(SEKOLAH, petugas, request("tap-konf3", 1, 0));

        TapResponse a = tapService.konfirmasi(SEKOLAH, pending.getPendingId(), petugas);
        TapResponse b = tapService.konfirmasi(SEKOLAH, pending.getPendingId(), petugas);

        assertThat(b.getTransaksiId()).isEqualTo(a.getTransaksiId());
        assertThat(ledgerSaldo.saldo(SEKOLAH, SubjekTipe.SISWA, SISWA)).isEqualTo(42_000L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaksi", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("#111: tap ulang dengan key sama saat pending → tidak buat pending kedua")
    void tapUlangSaatPendingIdempoten() {
        aktifkanKonfirmasiManual();
        TapResponse a = tapService.tap(SEKOLAH, petugas, request("tap-konf4", 1, 0));
        TapResponse b = tapService.tap(SEKOLAH, petugas, request("tap-konf4", 1, 0));

        assertThat(b.getPendingId()).isEqualTo(a.getPendingId());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM transaksi_menunggu_konfirmasi", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("#111: batalkan tap pending → tidak jadi transaksi, saldo utuh")
    void batalPendingTidakJadiTransaksi() {
        aktifkanKonfirmasiManual();
        TapResponse pending = tapService.tap(SEKOLAH, petugas, request("tap-konf5", 1, 0));

        tapService.batal(SEKOLAH, pending.getPendingId(), petugas);

        assertThat(ledgerSaldo.saldo(SEKOLAH, SubjekTipe.SISWA, SISWA)).isEqualTo(50_000L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaksi", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM transaksi_menunggu_konfirmasi WHERE id = ?",
                String.class, pending.getPendingId())).isEqualTo("DIBATALKAN");
    }

    @Test
    @DisplayName("#111: default nonaktif → tap langsung tercatat (perilaku lama)")
    void defaultNonaktifLangsungTercatat() {
        TapResponse resp = tapService.tap(SEKOLAH, petugas, request("tap-default", 1, 0));

        assertThat(resp.isMenungguKonfirmasi()).isFalse();
        assertThat(resp.getTransaksiId()).isNotNull();
        assertThat(ledgerSaldo.saldo(SEKOLAH, SubjekTipe.SISWA, SISWA)).isEqualTo(42_000L);
    }

    /** Fake port: kartu siswa "Budi" & katalog dua menu. */
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
                            .hargaJual(5_000L).kategoriId(200L).aktif(true).build());
            return (sekolahId, menuId) -> peta.get(menuId);
        }
    }
}
