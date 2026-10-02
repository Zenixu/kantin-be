package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.dto.TapRequest;
import com.asqi.scholia_kantin_be.dto.TapResponse;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.StatusSesiKasir;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.stok.LedgerStokService;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji integrasi sesi kasir &amp; tutup kasir (PRD §6.4).
 */
@SpringBootTest
@Import({TestcontainersConfig.class, TapServiceIT.FakePortConfig.class})
@EnabledIfDockerAvailable
class SesiKasirServiceIT {

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
    private VoidService voidService;

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
        // SesiKasirService memakai SekolahGuard → butuh TenantContext.
        TenantContext.set(IdentitasKantin.builder().userId("555").sekolahId(SEKOLAH).build());
        jdbc.execute("TRUNCATE TABLE transaksi_item, transaksi, sesi_kasir, titik_kasir, "
                + "saldo_ledger, saldo_cache, mutasi_stok, stok_cache CASCADE");
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
    @DisplayName("tap pertama otomatis membuka sesi; rekap menghitung bruto/void/bersih")
    void rekapSesi() {
        TapResponse t1 = tap("s1", 1); // 8.000
        tap("s2", 2);                  // 16.000
        voidService.voidTransaksi(SEKOLAH, t1.getTransaksiId(), 555L, "salah");

        Long sesiId = jdbc.queryForObject(
                "SELECT sesi_kasir_id FROM transaksi WHERE id = ?", Long.class, t1.getTransaksiId());

        SesiKasirService.RekapSesi rekap = sesiKasir.rekap(SEKOLAH, sesiId);
        assertThat(rekap.getJumlahTransaksi()).isEqualTo(1);
        assertThat(rekap.getJumlahVoid()).isEqualTo(1);
        assertThat(rekap.getTotalBersih()).isEqualTo(16_000L);
        assertThat(rekap.getTotalVoid()).isEqualTo(8_000L);
        assertThat(rekap.getTotalBruto()).isEqualTo(24_000L);
    }

    @Test
    @DisplayName("tutup sesi mengunci & menyimpan rekap")
    void tutupSesi() {
        TapResponse t1 = tap("s1", 1);
        Long sesiId = jdbc.queryForObject(
                "SELECT sesi_kasir_id FROM transaksi WHERE id = ?", Long.class, t1.getTransaksiId());

        var sesi = sesiKasir.tutupSesi(SEKOLAH, sesiId, 555L, false);

        assertThat(sesi.getStatus()).isEqualTo(StatusSesiKasir.DITUTUP);
        assertThat(sesi.getTotalBersih()).isEqualTo(8_000L);
        assertThat(sesi.getDitutupOleh()).isEqualTo(555L);
        assertThat(sesi.getDitutupAt()).isNotNull();
    }

    @Test
    @DisplayName("auto-tutup menutup semua sesi terbuka")
    void autoTutup() {
        tap("s1", 1);
        int ditutup = sesiKasir.tutupOtomatis(SEKOLAH);
        assertThat(ditutup).isEqualTo(1);

        Integer masihTerbuka = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sesi_kasir WHERE status = 'TERBUKA'", Integer.class);
        assertThat(masihTerbuka).isZero();
    }

    /**
     * Penjadwal harian (PRD §6.4) hanya boleh menutup sesi yang tanggalnya
     * SUDAH LEWAT — sesi hari ini yang masih berjalan tidak ikut tertutup,
     * meski penjadwal jalan menjelang tengah malam.
     */
    @Test
    @DisplayName("auto-tutup lintas-tenant: sesi hari ini TIDAK ditutup, sesi kemarin ditutup")
    void autoTutupLintasTenantHanyaSesiTertinggal() {
        tap("s1", 1); // membuka sesi hari ini

        // Ciptakan sesi tertinggal (kemarin) untuk sekolah yang sama.
        Long titik2 = 2L;
        jdbc.update("INSERT INTO titik_kasir (id, sekolah_id, nama, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'Kasir 2', true, now(), now())", titik2, SEKOLAH);
        jdbc.update("INSERT INTO sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status, "
                + "total_bruto, total_void, total_bersih, dibuka_at, auto_tutup, posting_buku_kas, "
                + "created_at, updated_at) "
                + "VALUES (9001, ?, ?, CURRENT_DATE - 1, 'TERBUKA', 0, 0, 0, now(), false, false, now(), now())",
                SEKOLAH, titik2);

        int ditutup = sesiKasir.tutupOtomatisLintasTenant();

        // Hanya sesi kemarin (9001) yang ditutup; sesi hari ini tetap terbuka.
        assertThat(ditutup).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM sesi_kasir WHERE id = 9001", String.class)).isEqualTo("DITUTUP");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sesi_kasir WHERE status = 'TERBUKA'", Integer.class)).isEqualTo(1);
    }
}
