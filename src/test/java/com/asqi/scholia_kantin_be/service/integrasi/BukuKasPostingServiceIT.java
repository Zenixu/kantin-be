package com.asqi.scholia_kantin_be.service.integrasi;

import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.dto.TapRequest;
import com.asqi.scholia_kantin_be.dto.TapResponse;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.MetodeBukuKas;
import com.asqi.scholia_kantin_be.enums.StatusSesiKasir;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.enums.TipeBukuKas;
import com.asqi.scholia_kantin_be.model.SesiKasir;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import com.asqi.scholia_kantin_be.service.kasir.SesiKasirService;
import com.asqi.scholia_kantin_be.service.kasir.TapService;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi posting Buku Kas &amp; idempotency-nya (INTEGRATIONS.md §3).
 *
 * <p>Memakai fake {@link BukuKasPort} (bukan jaringan) untuk memverifikasi:
 * kontrak perintah yang dikirim, penandaan {@code posting_buku_kas}, retry, dan
 * bahwa kegagalan integrasi <b>tidak</b> membatalkan penutupan sesi.
 */
@SpringBootTest
@Import({TestcontainersConfig.class,
        BukuKasPostingServiceIT.FakeBukuKasConfig.class,
        BukuKasPostingServiceIT.FakeLookupConfig.class})
@EnabledIfDockerAvailable
class BukuKasPostingServiceIT {

    private static final long SEKOLAH = 1L;
    private static final long SISWA = 7L;
    private static final long MENU_NASI = 10L;
    private static final long TITIK = 1L;
    private static final String UID = "A1B2C3D4";

    @Autowired
    private TapService tapService;

    @Autowired
    private SesiKasirService sesiKasir;

    @Autowired
    private BukuKasPostingService posting;

    @Autowired
    private FakeBukuKas fake;

    @Autowired
    private LedgerStokService ledgerStok;

    @Autowired
    private LedgerSaldoService ledgerSaldo;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    private final IdentitasKantin petugas = IdentitasKantin.builder()
            .userId("555").sekolahId(SEKOLAH).build();

    @BeforeEach
    void bersihkan() {
        TenantContext.set(IdentitasKantin.builder().userId("555").sekolahId(SEKOLAH).build());
        fake.reset();
        jdbc.execute("TRUNCATE TABLE transaksi_item, transaksi, sesi_kasir, titik_kasir, "
                + "saldo_ledger, saldo_cache, mutasi_stok, stok_cache, audit_log CASCADE");
        jdbc.update("INSERT INTO titik_kasir (id, sekolah_id, nama, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'Kasir 1', true, now(), now())", TITIK, SEKOLAH);
        tx.executeWithoutResult(s -> ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).subjekId(SISWA)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI).nominal(100_000).idempotencyKey("seed").build()));
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_NASI, 50, 4_000, "BARANG_MASUK", "BM", 1L));
    }

    @AfterEach
    void bersihkanKonteks() {
        TenantContext.clear();
    }

    private Long tapDanAmbilSesi(String key, int qty) {
        TapRequest r = new TapRequest();
        r.setRfidUid(UID);
        r.setTitikKasirId(TITIK);
        r.setIdempotencyKey(key);
        var item = new TapRequest.ItemTap();
        item.setMenuId(MENU_NASI);
        item.setQty(qty);
        r.setItems(List.of(item));
        TapResponse resp = tapService.tap(SEKOLAH, petugas, r);
        return jdbc.queryForObject(
                "SELECT sesi_kasir_id FROM transaksi WHERE id = ?", Long.class, resp.getTransaksiId());
    }

    @Test
    @DisplayName("tutup sesi → posting Buku Kas: MASUK/NON_TUNAI, jumlah 2 desimal, refId deterministik, refModul null")
    void postingSaatTutupSesi() {
        Long sesiId = tapDanAmbilSesi("s1", 1); // 8.000

        sesiKasir.tutupSesi(SEKOLAH, sesiId, 555L, false);

        assertThat(fake.dipanggil).hasSize(1);
        PerintahBukuKas p = fake.dipanggil.get(0);
        assertThat(p.getSekolahId()).isEqualTo(SEKOLAH);
        assertThat(p.getTipe()).isEqualTo(TipeBukuKas.MASUK);
        assertThat(p.getMetode()).isEqualTo(MetodeBukuKas.NON_TUNAI);
        assertThat(p.getKategori()).isEqualTo("Pendapatan Kantin");
        assertThat(p.getJumlah()).isEqualByComparingTo(new BigDecimal("8000"));
        assertThat(p.getRefId()).isEqualTo("KANTIN-SESI-" + sesiId);
        // Mitigasi Q3: refModul = null sampai admin-be menambah case kantin.
        assertThat(p.getRefModul()).isNull();

        // Flag idempotency tersimpan + audit tercatat.
        assertThat(jdbc.queryForObject(
                "SELECT posting_buku_kas FROM sesi_kasir WHERE id = ?", Boolean.class, sesiId)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT referensi_buku_kas FROM sesi_kasir WHERE id = ?", String.class, sesiId))
                .isEqualTo("KANTIN-SESI-" + sesiId);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE aksi = 'POSTING_BUKU_KAS'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("idempoten — posting ulang sesi yang sudah terposting tidak memanggil port lagi")
    void postingUlangTidakGanda() {
        Long sesiId = tapDanAmbilSesi("s1", 1);
        sesiKasir.tutupSesi(SEKOLAH, sesiId, 555L, false);
        assertThat(fake.dipanggil).hasSize(1);

        HasilPostingBukuKas hasil = posting.postingUlang(SEKOLAH, sesiId, 555L);

        assertThat(hasil.sukses()).isTrue();
        assertThat(fake.dipanggil).hasSize(1); // TIDAK ada entri ganda
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE aksi = 'POSTING_BUKU_KAS'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("kegagalan integrasi TIDAK membatalkan penutupan sesi; bisa di-retry")
    void gagalPostingTidakMembatalkanTutup() {
        Long sesiId = tapDanAmbilSesi("s1", 1);
        fake.melempar = true;

        SesiKasir sesi = sesiKasir.tutupSesi(SEKOLAH, sesiId, 555L, false);

        // Sesi tetap DITUTUP, hanya flag posting yang belum terisi.
        assertThat(sesi.getStatus()).isEqualTo(StatusSesiKasir.DITUTUP);
        assertThat(jdbc.queryForObject(
                "SELECT posting_buku_kas FROM sesi_kasir WHERE id = ?", Boolean.class, sesiId)).isFalse();

        // Integrasi pulih → retry berhasil tanpa membuat sesi/transaksi baru.
        fake.melempar = false;
        HasilPostingBukuKas hasil = posting.postingUlang(SEKOLAH, sesiId, 555L);

        assertThat(hasil.sukses()).isTrue();
        assertThat(fake.dipanggil).hasSize(2);
        assertThat(jdbc.queryForObject(
                "SELECT posting_buku_kas FROM sesi_kasir WHERE id = ?", Boolean.class, sesiId)).isTrue();
    }

    @Test
    @DisplayName("total bersih 0 → tidak ada yang diposting ke Buku Kas")
    void bersihNolDilewati() {
        SesiKasir sesi = sesiKasir.bukaSesi(SEKOLAH, TITIK); // tanpa transaksi

        sesiKasir.tutupSesi(SEKOLAH, sesi.getId(), 555L, false);
        HasilPostingBukuKas hasil = posting.postingUlang(SEKOLAH, sesi.getId(), 555L);

        assertThat(fake.dipanggil).isEmpty();
        assertThat(hasil.dilewati()).isTrue();
    }

    @Test
    @DisplayName("posting ulang sesi sekolah lain → 404 (bukan 403)")
    void postingUlangTenantLainNotFound() {
        Long sesiId = tapDanAmbilSesi("s1", 1);
        sesiKasir.tutupSesi(SEKOLAH, sesiId, 555L, false);
        fake.dipanggil.clear();

        assertThatThrownBy(() -> posting.postingUlang(2L, sesiId, 555L))
                .isInstanceOf(NotFoundEntity.class);

        assertThat(fake.dipanggil).isEmpty();
    }

    /** Fake port yang merekam perintah & bisa disetel sukses/gagal. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FakeBukuKasConfig {

        @Bean
        @Primary
        FakeBukuKas bukuKasFake() {
            return new FakeBukuKas();
        }
    }

    /** Fake kartu &amp; katalog (Q7 belum terjawab) agar tap bisa diuji tanpa admin-be. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FakeLookupConfig {

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

    static class FakeBukuKas implements BukuKasPort {

        final List<PerintahBukuKas> dipanggil = Collections.synchronizedList(new ArrayList<>());

        volatile boolean melempar = false;

        @Override
        public HasilPostingBukuKas catat(PerintahBukuKas perintah) {
            dipanggil.add(perintah);
            if (melempar) {
                throw new IllegalStateException("admin-be tidak dapat dihubungi");
            }
            return HasilPostingBukuKas.sukses(perintah.getRefId(), "ok");
        }

        void reset() {
            dipanggil.clear();
            melempar = false;
        }
    }
}
