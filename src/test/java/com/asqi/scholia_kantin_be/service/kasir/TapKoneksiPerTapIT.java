package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.dto.PengaturanKantinRequest;
import com.asqi.scholia_kantin_be.dto.TapRequest;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import com.asqi.scholia_kantin_be.service.integrasi.InfoMenu;
import com.asqi.scholia_kantin_be.service.integrasi.KartuLookupPort;
import com.asqi.scholia_kantin_be.service.integrasi.MenuLookupPort;
import com.asqi.scholia_kantin_be.service.konfigurasi.PengaturanKantinService;
import com.asqi.scholia_kantin_be.service.stok.LedgerStokService;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import io.micrometer.core.instrument.MeterRegistry;
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

/**
 * Uji regresi <b>jumlah pengambilan koneksi per tap</b> (issue #180).
 *
 * <p><b>Latar:</b> cold-start 100 tap/dtk melanggar SLO karena antrean kolam
 * koneksi Hikari — satu tap mengambil koneksi <b>3×</b> (dua pra-cek idempotency
 * di luar transaksi + satu transaksi utama). Perbaikan #180 menyatukan semuanya
 * dalam satu transaksi sehingga menjadi <b>1×</b>.
 *
 * <p>Uji ini mengukur metrik Micrometer {@code hikaricp.connections.acquire}
 * (dihitung Hikari) sebelum &amp; sesudah satu tap, lalu memastikan selisihnya
 * <b>1</b> — bukan 3. Ini menjadikan perbaikan terverifikasi dan mencegah
 * regresi (mis. pra-cek dipindah kembali ke luar transaksi).
 *
 * <p>Memakai PostgreSQL nyata (Testcontainers) karena metrik Hikari baru muncul
 * saat koneksi benar-benar diambil dari kolam.
 */
@SpringBootTest
@Import({TestcontainersConfig.class, TapKoneksiPerTapIT.FakePortConfig.class})
@EnabledIfDockerAvailable
@DisplayName("#180 — satu tap = satu pengambilan koneksi Hikari")
class TapKoneksiPerTapIT {

    private static final long SEKOLAH = 1L;
    private static final long SISWA = 7L;
    private static final long MENU_NASI = 10L;
    private static final long TITIK = 1L;
    private static final String UID = "A1B2C3D4";
    private static final String METER_ACQUIRE = "hikaricp.connections.acquire";

    @Autowired
    private TapService tapService;

    @Autowired
    private LedgerSaldoService ledgerSaldo;

    @Autowired
    private LedgerStokService ledgerStok;

    @Autowired
    private PengaturanKantinService pengaturan;

    @Autowired
    private MeterRegistry registry;

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

        tx.executeWithoutResult(s -> ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).subjekId(SISWA)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI).nominal(500_000).idempotencyKey("seed-saldo").build()));
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_NASI, 100, 4_000, "BARANG_MASUK", "BM-1", 1L));
    }

    /** Hitung total acquire koneksi Hikari (semua instance). */
    private double totalAcquire() {
        return registry.find(METER_ACQUIRE).timers().stream()
                .mapToDouble(t -> t.count())
                .sum();
    }

    private TapRequest request(String key) {
        TapRequest r = new TapRequest();
        r.setRfidUid(UID);
        r.setTitikKasirId(TITIK);
        r.setIdempotencyKey(key);
        var item = new TapRequest.ItemTap();
        item.setMenuId(MENU_NASI);
        item.setQty(1);
        r.setItems(List.of(item));
        return r;
    }

    @Test
    @DisplayName("tap sukses hanya mengambil koneksi Hikari SEKALI (bukan 3×)")
    void tapSuksesSatuKoneksi() {
        // Panaskan dulu (kompilasi/JIT) agar selisih metrik murni dari jalur tap.
        tapService.tap(SEKOLAH, petugas, request("warmup"));
        double sebelum = totalAcquire();

        tapService.tap(SEKOLAH, petugas, request("tap-1"));

        double selisih = totalAcquire() - sebelum;
        assertThat(selisih)
                .as("1 tap harus = 1 acquire koneksi (perbaikan #180)")
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("tap replay (idempotent) juga hanya mengambil koneksi SEKALI")
    void tapReplaySatuKoneksi() {
        tapService.tap(SEKOLAH, petugas, request("tap-dup")); // buat transaksi
        double sebelum = totalAcquire();

        tapService.tap(SEKOLAH, petugas, request("tap-dup")); // replay

        double selisih = totalAcquire() - sebelum;
        assertThat(selisih)
                .as("replay idempotent harus = 1 acquire koneksi")
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("konfirmasi manual: tap pending juga 1 acquire; konfirmasi 1 acquire")
    void pendingDanKonfirmasiSatuKoneksi() {
        PengaturanKantinRequest set = new PengaturanKantinRequest();
        set.setKonfirmasiManual(true);
        pengaturan.ubah(SEKOLAH, set, 900L);

        double sebelumTap = totalAcquire();
        var pending = tapService.tap(SEKOLAH, petugas, request("tap-konf"));
        assertThat(totalAcquire() - sebelumTap)
                .as("tap pending harus = 1 acquire koneksi")
                .isEqualTo(1.0);

        double sebelumKonfirmasi = totalAcquire();
        tapService.konfirmasi(SEKOLAH, pending.getPendingId(), petugas);
        assertThat(totalAcquire() - sebelumKonfirmasi)
                .as("konfirmasi harus = 1 acquire koneksi")
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("5 tap berturut-turut = 5 acquire (linier, tanpa acquire tersembunyi)")
    void limaTapLimaKoneksi() {
        tapService.tap(SEKOLAH, petugas, request("warmup-5"));
        double sebelum = totalAcquire();

        for (int i = 1; i <= 5; i++) {
            tapService.tap(SEKOLAH, petugas, request("tap-5-" + i));
        }

        assertThat(totalAcquire() - sebelum)
                .as("5 tap = 5 acquire (1 per tap)")
                .isEqualTo(5.0);
    }

    /** Fake port: kartu siswa "Budi" & satu menu. */
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
                            .hargaJual(8_000L).kategoriId(100L).aktif(true).build());
            return (sekolahId, menuId) -> peta.get(menuId);
        }
    }
}
