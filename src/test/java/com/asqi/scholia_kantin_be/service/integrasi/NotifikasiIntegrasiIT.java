package com.asqi.scholia_kantin_be.service.integrasi;

import com.asqi.scholia_kantin_be.dto.TapRequest;
import com.asqi.scholia_kantin_be.dto.TapResponse;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.JenisNotifikasi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import com.asqi.scholia_kantin_be.service.kasir.TapService;
import com.asqi.scholia_kantin_be.service.kasir.VoidService;
import com.asqi.scholia_kantin_be.service.saldo.RefundSaldoService;
import com.asqi.scholia_kantin_be.service.saldo.SaldoTopUpService;
import com.asqi.scholia_kantin_be.service.stok.LedgerStokService;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji integrasi notifikasi ortu (PRD §8.4, INTEGRATIONS.md §6) — issue #18/#36.
 *
 * <p>Memakai <b>fake</b> {@link NotifikasiPort} yang merekam setiap perintah
 * (menyembunyikan ketergantungan Q5), tetapi seluruh alur transaksi/top-up/
 * refund berjalan nyata. Menegakkan bahwa tiap peristiwa memicu notifikasi
 * dengan jenis &amp; data yang benar, dan bahwa kegagalan pengiriman
 * (<b>fail-open</b>) tidak membatalkan operasi.
 */
@SpringBootTest
@Import({TestcontainersConfig.class, NotifikasiIntegrasiIT.FakePortConfig.class,
        NotifikasiIntegrasiIT.FakeNotifikasiConfig.class})
@EnabledIfDockerAvailable
class NotifikasiIntegrasiIT {

    private static final long SEKOLAH = 1L;
    private static final long SISWA = 7L;
    private static final long MENU_NASI = 10L;
    private static final long TITIK = 1L;
    private static final String UID = "A1B2C3D4";

    @Autowired
    private TapService tapService;
    @Autowired
    private VoidService voidService;
    @Autowired
    private SaldoTopUpService topUpService;
    @Autowired
    private RefundSaldoService refundService;
    @Autowired
    private LedgerSaldoService ledgerSaldo;
    @Autowired
    private LedgerStokService ledgerStok;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private FakeNotifikasi fakeNotifikasi;

    private final IdentitasKantin petugas = IdentitasKantin.builder()
            .userId("555").nama("Petugas A").sekolahId(SEKOLAH).build();

    @BeforeEach
    void bersihkan() {
        TenantContext.set(petugas);
        fakeNotifikasi.reset();
        jdbc.execute("TRUNCATE TABLE transaksi_item, transaksi, sesi_kasir, titik_kasir, "
                + "saldo_ledger, saldo_cache, mutasi_stok, stok_cache CASCADE");
        jdbc.update("INSERT INTO titik_kasir (id, sekolah_id, nama, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'Kasir 1', true, now(), now())", TITIK, SEKOLAH);
        tx.executeWithoutResult(s -> ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).subjekId(SISWA)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI).nominal(50_000)
                .idempotencyKey("seed-" + System.nanoTime()).build()));
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_NASI, 20, 4_000,
                "BARANG_MASUK", "BM-" + System.nanoTime(), 1L));
    }

    @AfterEach
    void bersihkanKonteks() {
        TenantContext.clear();
    }

    private TapResponse tap(String key, int qty) {
        TapRequest r = new TapRequest();
        r.setRfidUid(UID);
        r.setTitikKasirId(TITIK);
        r.setIdempotencyKey(key);
        var item = new TapRequest.ItemTap();
        item.setMenuId(MENU_NASI);
        item.setQty(qty);
        r.setItems(List.of(item));
        return tapService.tap(SEKOLAH, petugas, r);
    }

    @Test
    @DisplayName("tap → notifikasi BELANJA ke ortu dengan nominal & saldo setelah")
    void tapMemicuNotifikasiBelanja() {
        tap("tap-1", 2); // 16.000

        PerintahNotifikasi p = fakeNotifikasi.satu(JenisNotifikasi.BELANJA);
        assertThat(p.getSekolahId()).isEqualTo(SEKOLAH);
        assertThat(p.getSubjekTipe()).isEqualTo(SubjekTipe.SISWA);
        assertThat(p.getSubjekId()).isEqualTo(SISWA);
        assertThat(p.getNominal()).isEqualTo(16_000L);
        assertThat(p.getSaldoSetelah()).isEqualTo(34_000L);
        assertThat(p.getReferensiId()).isNotBlank();
        assertThat(p.getRingkasan()).contains("belanja");
    }

    @Test
    @DisplayName("void → notifikasi VOID")
    void voidMemicuNotifikasi() {
        TapResponse tap = tap("tap-2", 1);
        voidService.voidTransaksi(SEKOLAH, tap.getTransaksiId(), 555L, "salah input");

        PerintahNotifikasi p = fakeNotifikasi.satu(JenisNotifikasi.VOID);
        assertThat(p.getSubjekId()).isEqualTo(SISWA);
        assertThat(p.getNominal()).isEqualTo(8_000L);
        assertThat(p.getRingkasan()).contains("dibatalkan");
    }

    @Test
    @DisplayName("top-up tunai → notifikasi TOPUP_TUNAI")
    void topUpTunaiMemicuNotifikasi() {
        topUpService.topUpTunai(SEKOLAH, SubjekTipe.SISWA, SISWA, 25_000, "Ibu Budi",
                "TU-1", 9L);

        PerintahNotifikasi p = fakeNotifikasi.satu(JenisNotifikasi.TOPUP_TUNAI);
        assertThat(p.getNominal()).isEqualTo(25_000L);
        assertThat(p.getSaldoSetelah()).isEqualTo(75_000L);
    }

    @Test
    @DisplayName("top-up online → notifikasi TOPUP_ONLINE")
    void topUpOnlineMemicuNotifikasi() {
        topUpService.topUpOnline(SEKOLAH, SubjekTipe.SISWA, SISWA, 30_000, "PG-REF-1",
                "Ibu Budi", "QRIS", null);

        PerintahNotifikasi p = fakeNotifikasi.satu(JenisNotifikasi.TOPUP_ONLINE);
        assertThat(p.getNominal()).isEqualTo(30_000L);
        assertThat(p.getReferensiId()).isEqualTo("PG-REF-1");
    }

    @Test
    @DisplayName("refund → notifikasi REFUND")
    void refundMemicuNotifikasi() {
        refundService.refund(SEKOLAH, SISWA, "REF-1", "siswa lulus", 9L);

        PerintahNotifikasi p = fakeNotifikasi.satu(JenisNotifikasi.REFUND);
        assertThat(p.getNominal()).isEqualTo(50_000L);
        assertThat(p.getSaldoSetelah()).isZero();
    }

    @Test
    @DisplayName("fail-open: port melempar → transaksi tetap sukses (notifikasi tidak membatalkan)")
    void gagalKirimTidakMembatalkanTransaksi() {
        fakeNotifikasi.gagal = true;

        TapResponse resp = tap("tap-3", 1); // tidak boleh melempar

        assertThat(resp.getSaldoSisa()).isEqualTo(42_000L);
        assertThat(ledgerSaldo.saldo(SEKOLAH, SubjekTipe.SISWA, SISWA)).isEqualTo(42_000L);
        assertThat(fakeNotifikasi.terkirim).isEmpty();
    }

    /** Fake port notifikasi yang merekam perintah; bisa dibuat selalu gagal. */
    static class FakeNotifikasi implements NotifikasiPort {
        final List<PerintahNotifikasi> terkirim = new CopyOnWriteArrayList<>();
        volatile boolean gagal = false;

        @Override
        public HasilKirimNotifikasi kirim(PerintahNotifikasi perintah) {
            if (gagal) {
                throw new RuntimeException("mobile-be tidak tersedia (simulasi)");
            }
            terkirim.add(perintah);
            return HasilKirimNotifikasi.terkirim("ok");
        }

        PerintahNotifikasi satu(JenisNotifikasi jenis) {
            List<PerintahNotifikasi> cocok = terkirim.stream()
                    .filter(p -> p.getJenis() == jenis).toList();
            assertThat(cocok).as("notifikasi " + jenis).hasSize(1);
            return cocok.get(0);
        }

        void reset() {
            terkirim.clear();
            gagal = false;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeNotifikasiConfig {
        @Bean
        @Primary
        FakeNotifikasi fakeNotifikasi() {
            return new FakeNotifikasi();
        }
    }

    /** Fake port kartu & menu (menyembunyikan Q7 & Fase 5) — mirip TapServiceIT. */
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
