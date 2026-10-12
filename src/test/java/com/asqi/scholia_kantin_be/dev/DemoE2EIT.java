package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.dto.TapRequest;
import com.asqi.scholia_kantin_be.dto.TapResponse;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.StatusSesiKasir;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.KartuTamu;
import com.asqi.scholia_kantin_be.model.SesiKasir;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.integrasi.BukuKasPort;
import com.asqi.scholia_kantin_be.service.integrasi.BukuKasPostingService;
import com.asqi.scholia_kantin_be.service.integrasi.HasilPostingBukuKas;
import com.asqi.scholia_kantin_be.service.kartu.KartuTamuService;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import com.asqi.scholia_kantin_be.service.kasir.SesiKasirService;
import com.asqi.scholia_kantin_be.service.kasir.TapService;
import com.asqi.scholia_kantin_be.service.stok.LedgerStokService;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
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
 * Uji integrasi <b>jalur DEMO end-to-end</b> (issue <b>#164</b>, kebijakan:
 * integrasi yang butuh API key memakai mode demo).
 *
 * <p>Membuktikan alur kasir lengkap benar-benar berjalan <b>tanpa kredensial tim
 * SKOOLIA</b>: <i>tap Kartu Tamu → potong saldo/stok → tutup sesi → posting Buku
 * Kas SUKSES → penanda idempoten</i>. Berbeda dari IT lain yang memakai fake
 * {@code @Primary}, IT ini mengaktifkan adapter <b>demo nyata</b>
 * ({@link KartuLookupDemo} + {@link BukuKasDemo}) lewat {@code kantin.demo.enabled=true},
 * sehingga yang diuji adalah jalur produksi-kode yang sama, hanya backend-nya disimulasikan.
 *
 * <p>Menegakkan acceptance criteria #164 "uji integrasi lintas-sistem hijau" untuk
 * jalur demo; jalur produksi nyata tetap menunggu kredensial (lihat
 * {@code PemeriksaIntegrasiProduksi}).
 */
@SpringBootTest(properties = {
        "kantin.demo.enabled=true",
        // Adapter REST nyata harus tetap mati agar tidak bentrok @Primary dgn demo.
        "kantin.integrasi.bukukas.enabled=false",
        "kantin.integrasi.kartu-lookup.enabled=false"
})
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
@DisplayName("Demo E2E (#164) — tap → tutup sesi → posting Buku Kas via adapter demo")
class DemoE2EIT {

    private static final long SEKOLAH = 1L;
    private static final long MENU_NASI = 10L;
    private static final long TITIK = 1L;
    private static final String UID = "DE000001";

    @Autowired
    private TapService tapService;

    @Autowired
    private SesiKasirService sesiKasir;

    @Autowired
    private BukuKasPostingService bukuKasPosting;

    @Autowired
    private KartuTamuService kartuTamu;

    @Autowired
    private LedgerSaldoService ledgerSaldo;

    @Autowired
    private LedgerStokService ledgerStok;

    @Autowired
    private BukuKasPort bukuKasPort;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    private final IdentitasKantin petugas = IdentitasKantin.builder()
            .userId("555").nama("Petugas Demo").sekolahId(SEKOLAH).build();

    private Long kartuId;

    @BeforeEach
    void siapkan() {
        TenantContext.set(IdentitasKantin.builder().userId("555").sekolahId(SEKOLAH).build());
        jdbc.execute("TRUNCATE TABLE transaksi_menunggu_konfirmasi, transaksi_item, transaksi, "
                + "sesi_kasir, titik_kasir, kartu_tamu, saldo_ledger, saldo_cache, "
                + "mutasi_stok, stok_cache, posting_buku_kas, sekolah_kantin_config CASCADE");
        jdbc.update("INSERT INTO titik_kasir (id, sekolah_id, nama, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'Kasir Demo', true, now(), now())", TITIK, SEKOLAH);
        jdbc.update("INSERT INTO kategori_menu (id, sekolah_id, nama, urutan, is_active, created_at, updated_at) "
                + "VALUES (100, ?, 'Makanan', 1, true, now(), now())", SEKOLAH);
        jdbc.update("INSERT INTO menu (id, sekolah_id, kategori_id, nama, harga_jual, satuan, "
                + "stok_minimum, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 100, 'Nasi Uduk', 8000, 'PORSI', 5, true, now(), now())",
                MENU_NASI, SEKOLAH);

        // Adapter demo harus terpasang (bukan *Fallback) — bukti gerbang demo bekerja.
        assertThat(bukuKasPort).isInstanceOf(BukuKasDemo.class);

        // Seed: 1 Kartu Tamu (jalur demo) + saldo Rp100.000 + stok menu.
        KartuTamu kartu = kartuTamu.buatKartu(SEKOLAH, "KT-DEMO-001", UID, null, "Budi (Guru)", 1L);
        kartuId = kartu.getId();
        tx.executeWithoutResult(s -> ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.KARTU_TAMU).subjekId(kartuId)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI)
                .nominal(100_000).idempotencyKey("demo-topup-" + System.nanoTime()).build()));
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(
                SEKOLAH, MENU_NASI, 20, 7_000, "BARANG_MASUK", "BM-DEMO-1", 1L));
    }

    private TapRequest request(String key, int qty) {
        TapRequest r = new TapRequest();
        r.setRfidUid(UID);
        r.setTitikKasirId(TITIK);
        r.setIdempotencyKey(key);
        var item = new TapRequest.ItemTap();
        item.setMenuId(MENU_NASI);
        item.setQty(qty);
        r.setItems(new java.util.ArrayList<>(List.of(item)));
        return r;
    }

    @Test
    @DisplayName("alur penuh: tap Kartu Tamu → tutup sesi → Buku Kas SUKSES (referensi demo)")
    void alurPenuhSampaiPostingBukuKas() {
        // 1) Tap: adapter demo mengenali Kartu Tamu → saldo/stok terpotong.
        TapResponse tap = tapService.tap(SEKOLAH, petugas, request("demo-e2e-1", 2));

        assertThat(tap.getTotal()).isEqualTo(16_000L);      // 2 × 8.000 (harga jual)
        assertThat(tap.getSaldoSisa()).isEqualTo(84_000L);  // 100.000 − 16.000
        assertThat(ledgerSaldo.saldo(SEKOLAH, SubjekTipe.KARTU_TAMU, kartuId)).isEqualTo(84_000L);
        assertThat(ledgerStok.stok(SEKOLAH, MENU_NASI)).isEqualTo(18);

        // 2) Tutup sesi → posting Buku Kas lewat BukuKasDemo (SUKSES, bukan dilewati).
        SesiKasir sesi = sesiKasir.sesiTerbukaAtauBuka(SEKOLAH, TITIK);
        SesiKasir ditutup = sesiKasir.tutupSesi(SEKOLAH, sesi.getId(), 555L, false);

        assertThat(ditutup.getStatus()).isEqualTo(StatusSesiKasir.DITUTUP);
        assertThat(ditutup.getTotalBersih()).isEqualTo(16_000L);
        assertThat(ditutup.isPostingBukuKas()).isTrue();                    // posting SUKSES via demo
        assertThat(ditutup.getReferensiBukuKas()).startsWith("DEMO-BK-");   // referensi dari BukuKasDemo
    }

    @Test
    @DisplayName("idempoten: posting ulang sesi yang sama tidak menggandakan entri Buku Kas")
    void postingUlangTidakMenggandakan() {
        tapService.tap(SEKOLAH, petugas, request("demo-e2e-2", 1));
        SesiKasir sesi = sesiKasir.sesiTerbukaAtauBuka(SEKOLAH, TITIK);
        SesiKasir ditutup = sesiKasir.tutupSesi(SEKOLAH, sesi.getId(), 555L, false);
        String referensiAwal = ditutup.getReferensiBukuKas();

        // Posting ulang (mis. retry scheduler) → dilewati karena flag sudah true.
        HasilPostingBukuKas hasil = bukuKasPosting.postingUlangSistem(SEKOLAH, sesi.getId());

        assertThat(hasil.sukses()).isTrue();
        assertThat(hasil.pesan()).contains("Sudah diposting");

        SesiKasir lagi = sesiKasir.ambil(SEKOLAH, sesi.getId());
        assertThat(lagi.isPostingBukuKas()).isTrue();
        assertThat(lagi.getReferensiBukuKas()).isEqualTo(referensiAwal);
    }
}
