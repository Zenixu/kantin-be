package com.asqi.scholia_kantin_be.config.integrasi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji penjaga kesiapan integrasi produksi (issue <b>#164</b>) — memastikan
 * kantin-be menolak start bila masih ada {@code *Fallback} / public key RS256
 * belum terpasang, saat {@code kantin.integrasi.wajib-aktif=true}. Tanpa Spring
 * context (cepat).
 */
class PemeriksaIntegrasiProduksiTest {

    /** Properties dengan keenam adapter aktif + base-url terisi. */
    private IntegrasiRestProperties lengkap() {
        IntegrasiRestProperties p = new IntegrasiRestProperties();
        p.setWajibAktif(true);
        isi(p.getBukukas(), "http://admin-be:8081");
        isi(p.getNotifikasi(), "http://mobile-be:8083");
        isi(p.getKartuLookup(), "http://admin-be:8081");
        isi(p.getStatusSiswa(), "http://admin-be:8081");
        isi(p.getUidSiswa(), "http://admin-be:8081");
        isi(p.getAktivasi(), "http://internal-be:8084");
        return p;
    }

    private void isi(IntegrasiRestProperties.Endpoint e, String baseUrl) {
        e.setEnabled(true);
        e.setBaseUrl(baseUrl);
    }

    /** Environment dengan public key RS256 admin & mobile terpasang. */
    private MockEnvironment envKunciLengkap() {
        MockEnvironment e = new MockEnvironment();
        e.setProperty("jwt.admin.public-key", "MIIBIjANBgkq...admin");
        e.setProperty("jwt.mobile.public-key", "MIIBIjANBgkq...mobile");
        return e;
    }

    @Test
    @DisplayName("wajib-aktif=false → tidak pernah menggagalkan start (dev/demo/test)")
    void nonaktifAman() {
        IntegrasiRestProperties kosong = new IntegrasiRestProperties(); // semua fallback
        var pemeriksa = new PemeriksaIntegrasiProduksi(kosong, new MockEnvironment());

        assertThatCode(pemeriksa::periksa).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("wajib-aktif=true & lengkap (6 adapter + public key) → start lolos")
    void lengkapLolos() {
        var pemeriksa = new PemeriksaIntegrasiProduksi(lengkap(), envKunciLengkap());

        assertThatCode(pemeriksa::periksa).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("wajib-aktif=true & ada adapter belum aktif → start DIGAGALKAN, sebut nama adapter")
    void adapterBelumAktif() {
        IntegrasiRestProperties p = lengkap();
        p.getBukukas().setEnabled(false); // Buku Kas masih *Fallback
        var pemeriksa = new PemeriksaIntegrasiProduksi(p, envKunciLengkap());

        assertThatThrownBy(pemeriksa::periksa)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Buku Kas")
                .hasMessageContaining("bukukas");
    }

    @Test
    @DisplayName("wajib-aktif=true & adapter aktif tapi base-url kosong → DIGAGALKAN")
    void baseUrlKosong() {
        IntegrasiRestProperties p = lengkap();
        p.getNotifikasi().setBaseUrl("");
        var pemeriksa = new PemeriksaIntegrasiProduksi(p, envKunciLengkap());

        assertThatThrownBy(pemeriksa::periksa)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Notifikasi")
                .hasMessageContaining("base-url");
    }

    @Test
    @DisplayName("wajib-aktif=true & public key RS256 admin kosong → DIGAGALKAN")
    void kunciAdminKosong() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("jwt.mobile.public-key", "MIIBIjANBgkq...mobile"); // admin kosong
        var pemeriksa = new PemeriksaIntegrasiProduksi(lengkap(), env);

        assertThatThrownBy(pemeriksa::periksa)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin-be");
    }

    @Test
    @DisplayName("wajib-aktif=true & public key RS256 mobile kosong → DIGAGALKAN")
    void kunciMobileKosong() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("jwt.admin.public-key", "MIIBIjANBgkq...admin"); // mobile kosong
        var pemeriksa = new PemeriksaIntegrasiProduksi(lengkap(), env);

        assertThatThrownBy(pemeriksa::periksa)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mobile-be");
    }
}
