package com.asqi.scholia_kantin_be.service.stok;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji unit HPP rata-rata tertimbang (PRD §7.4).
 *
 * <p>Perhitungan murni tanpa DB — cepat &amp; deterministik (CONVENTIONS.md §9).
 */
class HppServiceTest {

    private final HppService hpp = new HppService();

    @Test
    @DisplayName("stok 0 → HPP baru = harga beli barang masuk")
    void stokKosongPakaiHargaBeli() {
        assertThat(hpp.hitungRataRataTertimbang(0, 0, 10, 5000)).isEqualTo(5000);
        assertThat(hpp.hitungRataRataTertimbang(0, 9999, 3, 2500)).isEqualTo(2500);
    }

    @Test
    @DisplayName("rata-rata tertimbang dasar")
    void rataRataDasar() {
        // (10 × 5000 + 10 × 6000) / 20 = 5500
        assertThat(hpp.hitungRataRataTertimbang(10, 5000, 10, 6000)).isEqualTo(5500);
    }

    @Test
    @DisplayName("pembulatan HALF_UP ke rupiah terdekat")
    void pembulatanHalfUp() {
        // (1 × 1000 + 1 × 1001) / 2 = 1000.5 → 1001
        assertThat(hpp.hitungRataRataTertimbang(1, 1000, 1, 1001)).isEqualTo(1001);
        // (3 × 1000 + 1 × 1001) / 4 = 1000.25 → 1000
        assertThat(hpp.hitungRataRataTertimbang(3, 1000, 1, 1001)).isEqualTo(1000);
    }

    @Test
    @DisplayName("qty masuk ≤ 0 ditolak")
    void qtyMasukTidakValid() {
        assertThatThrownBy(() -> hpp.hitungRataRataTertimbang(5, 1000, 0, 2000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hpp.hitungRataRataTertimbang(5, 1000, -1, 2000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("nilai persediaan = stok × HPP")
    void nilaiPersediaan() {
        assertThat(hpp.nilaiPersediaan(12, 3500)).isEqualTo(42_000L);
    }

    // ────────────────────────────────────────────────────────────────
    // PEMBALIK BARANG MASUK (PRD §7.2)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pembalik membalik tepat rata-rata tertimbang sebelumnya")
    void pembalikMengembalikanHppSemula() {
        // Masuk 10@5000 → 10@6000 → HPP 5500 (stok 20). Balik 10@6000:
        // (20×5500 − 10×6000) / 10 = (110000 − 60000)/10 = 5000
        assertThat(hpp.hitungRataRataSetelahPembalik(20, 5500, 10, 6000)).isEqualTo(5000);
    }

    @Test
    @DisplayName("pembalik dengan stok bersisa satu batch → HPP = harga beli batch itu")
    void pembalikMenyisakanSatuBatch() {
        // stok 5 @ HPP 5000, balik 2 @ harga 5000 → sisa 3, HPP tetap 5000
        assertThat(hpp.hitungRataRataSetelahPembalik(5, 5000, 2, 5000)).isEqualTo(5000);
    }

    @Test
    @DisplayName("pembalik menghabiskan stok → HPP 0")
    void pembalikHabiskanStok() {
        assertThat(hpp.hitungRataRataSetelahPembalik(10, 5500, 10, 6000)).isEqualTo(0L);
    }

    @Test
    @DisplayName("pembalik menjaga nilai persediaan tak negatif")
    void pembalikNilaiTidakNegatif() {
        // harga beli asal lebih besar dari nilai tersisa → dijepit ke 0
        assertThat(hpp.hitungRataRataSetelahPembalik(3, 1000, 2, 5000)).isEqualTo(0L);
    }

    @Test
    @DisplayName("pembalik qty tidak valid ditolak")
    void pembalikQtyTidakValid() {
        assertThatThrownBy(() -> hpp.hitungRataRataSetelahPembalik(5, 1000, 0, 2000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hpp.hitungRataRataSetelahPembalik(5, 1000, 6, 2000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
