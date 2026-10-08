package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.dto.TapRequest;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import com.asqi.scholia_kantin_be.service.integrasi.InfoMenu;
import com.asqi.scholia_kantin_be.service.stok.LedgerStokService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uji unit validasi 6 tahap tap (PRD §6.1) — urutan &amp; pesan wajib benar.
 *
 * <p>Stok di-stub lewat mock {@link LedgerStokService}; menu lewat fungsi lookup.
 */
class TapValidatorTest {

    private static final long SEKOLAH = 1L;

    private LedgerStokService ledgerStok;
    private TapValidator validator;

    private final InfoMenu nasi = InfoMenu.builder()
            .menuId(10L).nama("Nasi Uduk").hargaJual(8000L).kategoriId(100L).aktif(true).build();
    private final InfoMenu esJeruk = InfoMenu.builder()
            .menuId(20L).nama("Es Jeruk").hargaJual(5000L).kategoriId(200L).aktif(true).build();

    @BeforeEach
    void siap() {
        ledgerStok = mock(LedgerStokService.class);
        when(ledgerStok.stok(anyLong(), anyLong())).thenReturn(50);
        validator = new TapValidator(ledgerStok);
    }

    private TapRequest request(int qtyNasi, int qtyJeruk) {
        TapRequest r = new TapRequest();
        r.setRfidUid("A1B2C3D4");
        r.setTitikKasirId(1L);
        r.setIdempotencyKey("key-1");
        // Kasir hanya mengirim item dengan qty > 0 (item 0-qty dianggap tak dipilih).
        List<TapRequest.ItemTap> items = new java.util.ArrayList<>();
        if (qtyNasi > 0) {
            items.add(item(10L, qtyNasi));
        }
        if (qtyJeruk > 0) {
            items.add(item(20L, qtyJeruk));
        }
        r.setItems(items);
        return r;
    }

    private TapRequest.ItemTap item(Long menuId, int qty) {
        TapRequest.ItemTap i = new TapRequest.ItemTap();
        i.setMenuId(menuId);
        i.setQty(qty);
        return i;
    }

    private InfoKartu kartuSiswa(long limit, Set<Long> menuDiblokir, Set<Long> kategoriDiblokir) {
        return InfoKartu.builder()
                .dikenal(true).diblokir(false).subjekTipe(SubjekTipe.SISWA).subjekId(7L)
                .nama("Budi").kelas("5A").limitHarian(limit)
                .menuDiblokir(menuDiblokir).kategoriDiblokir(kategoriDiblokir)
                .build();
    }

    private java.util.function.Function<Long, InfoMenu> lookup() {
        Map<Long, InfoMenu> peta = Map.of(10L, nasi, 20L, esJeruk);
        return peta::get;
    }

    @Test
    @DisplayName("tahap 1 — kartu tidak dikenal")
    void kartuTidakDikenal() {
        var hasil = validator.validasi(SEKOLAH, request(1, 1), InfoKartu.tidakDikenal(), 999_999, 0, lookup());
        assertThat(hasil.valid()).isFalse();
        assertThat(hasil.getValidasi().getTahapGagal()).isEqualTo(1);
        assertThat(hasil.getValidasi().getPesan()).isEqualTo("Kartu tidak dikenal");
    }

    @Test
    @DisplayName("tahap 2 — kartu siswa diblokir → arahkan ke orang tua")
    void kartuSiswaDiblokir() {
        InfoKartu kartu = InfoKartu.builder()
                .dikenal(true).diblokir(true).subjekTipe(SubjekTipe.SISWA).subjekId(7L).build();
        var hasil = validator.validasi(SEKOLAH, request(1, 1), kartu, 999_999, 0, lookup());
        assertThat(hasil.getValidasi().getTahapGagal()).isEqualTo(2);
        assertThat(hasil.getValidasi().getPesan()).isEqualTo("Kartu diblokir, hubungi orang tua");
    }

    @Test
    @DisplayName("tahap 3 — item diblokir orang tua")
    void itemDiblokirOrtu() {
        InfoKartu kartu = kartuSiswa(1_000_000, Set.of(10L), Set.of());
        var hasil = validator.validasi(SEKOLAH, request(1, 1), kartu, 999_999, 0, lookup());
        assertThat(hasil.getValidasi().getTahapGagal()).isEqualTo(3);
        assertThat(hasil.getValidasi().getPesan()).contains("Nasi Uduk").contains("diblokir oleh orang tua");
    }

    @Test
    @DisplayName("tahap 3 — kategori diblokir orang tua")
    void kategoriDiblokirOrtu() {
        InfoKartu kartu = kartuSiswa(1_000_000, Set.of(), Set.of(200L));
        var hasil = validator.validasi(SEKOLAH, request(0, 1), kartu, 999_999, 0, lookup());
        assertThat(hasil.getValidasi().getTahapGagal()).isEqualTo(3);
        assertThat(hasil.getValidasi().getPesan()).contains("Es Jeruk");
    }

    @Test
    @DisplayName("tahap 4 — stok tidak cukup")
    void stokTidakCukup() {
        when(ledgerStok.stok(anyLong(), anyLong())).thenReturn(1);
        InfoKartu kartu = kartuSiswa(1_000_000, Set.of(), Set.of());
        var hasil = validator.validasi(SEKOLAH, request(2, 0), kartu, 999_999, 0, lookup());
        assertThat(hasil.getValidasi().getTahapGagal()).isEqualTo(4);
        assertThat(hasil.getValidasi().getPesan()).contains("Stok Nasi Uduk tidak cukup").contains("sisa 1");
    }

    @Test
    @DisplayName("#124: tahap 4 — stok 0 → item ditolak sebagai 'habis'")
    void stokHabisDitolak() {
        when(ledgerStok.stok(anyLong(), anyLong())).thenReturn(0);
        InfoKartu kartu = kartuSiswa(1_000_000, Set.of(), Set.of());
        var hasil = validator.validasi(SEKOLAH, request(1, 0), kartu, 999_999, 0, lookup());
        assertThat(hasil.getValidasi().getTahapGagal()).isEqualTo(4);
        assertThat(hasil.getValidasi().getPesan()).contains("Nasi Uduk habis");
    }

    @Test
    @DisplayName("tahap 5 — melebihi limit harian (sisa ditampilkan)")
    void melebihiLimitHarian() {
        // total = 8000 + 5000 = 13000; limit 10000; sudah belanja 5000 → sisa 5000
        InfoKartu kartu = kartuSiswa(10_000, Set.of(), Set.of());
        var hasil = validator.validasi(SEKOLAH, request(1, 1), kartu, 999_999, 5_000, lookup());
        assertThat(hasil.getValidasi().getTahapGagal()).isEqualTo(5);
        assertThat(hasil.getValidasi().getPesan()).isEqualTo("Melebihi limit harian (sisa Rp 5000)");
    }

    @Test
    @DisplayName("tahap 6 — saldo kurang (nominal kekurangan tepat)")
    void saldoKurang() {
        InfoKartu kartu = kartuSiswa(1_000_000, Set.of(), Set.of());
        // total 13000, saldo 8000 → kurang 5000
        var hasil = validator.validasi(SEKOLAH, request(1, 1), kartu, 8_000, 0, lookup());
        assertThat(hasil.getValidasi().getTahapGagal()).isEqualTo(6);
        assertThat(hasil.getValidasi().getPesan()).isEqualTo("Saldo kurang Rp 5000");
    }

    @Test
    @DisplayName("lolos semua tahap — total & baris benar")
    void lolos() {
        InfoKartu kartu = kartuSiswa(1_000_000, Set.of(), Set.of());
        var hasil = validator.validasi(SEKOLAH, request(2, 1), kartu, 100_000, 0, lookup());
        assertThat(hasil.valid()).isTrue();
        assertThat(hasil.getTotal()).isEqualTo(2 * 8000L + 5000L);
        assertThat(hasil.getBaris()).hasSize(2);
        assertThat(hasil.getBaris().get(0).getSubtotal()).isEqualTo(16_000L);
    }

    @Test
    @DisplayName("kartu tamu — tanpa limit harian & tanpa blokir item")
    void kartuTamuTanpaLimit() {
        InfoKartu kartu = InfoKartu.builder()
                .dikenal(true).diblokir(false).subjekTipe(SubjekTipe.KARTU_TAMU).subjekId(99L)
                .nama("KT-012").limitHarian(null).build();
        // belanja besar tetap lolos karena tak ada limit
        var hasil = validator.validasi(SEKOLAH, request(3, 3), kartu, 999_999_999, 500_000, lookup());
        assertThat(hasil.valid()).isTrue();
    }
}
