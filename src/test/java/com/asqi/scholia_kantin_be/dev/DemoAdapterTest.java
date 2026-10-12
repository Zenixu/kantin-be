package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.enums.JenisNotifikasi;
import com.asqi.scholia_kantin_be.enums.MetodeBukuKas;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.enums.TipeBukuKas;
import com.asqi.scholia_kantin_be.service.integrasi.HasilKirimNotifikasi;
import com.asqi.scholia_kantin_be.service.integrasi.HasilPostingBukuKas;
import com.asqi.scholia_kantin_be.service.integrasi.PerintahBukuKas;
import com.asqi.scholia_kantin_be.service.integrasi.PerintahNotifikasi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Uji adapter <b>demo</b> integrasi (PRD §10, Q3/Q5/Q6/Q7) — membuktikan mode
 * demo meniru sistem eksternal sehingga alur end-to-end berjalan tanpa API key
 * (kebijakan: integrasi yang butuh kredensial memakai demo). Tanpa Spring context.
 */
class DemoAdapterTest {

    @Test
    @DisplayName("BukuKasDemo: entri diterima (SUKSES) dengan referensi tiruan")
    void bukuKasDemo() {
        var perintah = PerintahBukuKas.builder()
                .sekolahId(1L)
                .tanggal(OffsetDateTime.now())
                .tipe(TipeBukuKas.MASUK)
                .kategori("Pendapatan Kantin")
                .jumlah(BigDecimal.valueOf(100000))
                .metode(MetodeBukuKas.NON_TUNAI)
                .refId("KANTIN-SESI-1")
                .refModul("KANTIN")
                .build();

        HasilPostingBukuKas hasil = new BukuKasDemo().catat(perintah);

        assertThat(hasil.sukses()).isTrue();
        assertThat(hasil.referensi()).isEqualTo("DEMO-BK-KANTIN-SESI-1");
    }

    @Test
    @DisplayName("NotifikasiDemo: notifikasi terkirim (bukan dilewati)")
    void notifikasiDemo() {
        var perintah = PerintahNotifikasi.builder()
                .sekolahId(1L)
                .jenis(JenisNotifikasi.BELANJA)
                .subjekTipe(SubjekTipe.SISWA)
                .subjekId(10L)
                .nominal(8000L)
                .ringkasan("Budi belanja Rp8.000 di Kantin")
                .build();

        HasilKirimNotifikasi hasil = new NotifikasiDemo().kirim(perintah);

        assertThat(hasil.terkirim()).isTrue();
        assertThat(hasil.status()).isEqualTo(HasilKirimNotifikasi.Status.TERKIRIM);
    }

    @Test
    @DisplayName("StatusSiswaDemo: siswa dianggap aktif; blokir tidak melempar")
    void statusSiswaDemo() {
        var demo = new StatusSiswaDemo();

        assertThat(demo.tidakAktif(1L, 10L)).isFalse();
        assertThatCode(() -> demo.blokirKartu(1L, 10L, "siswa keluar")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UidSiswaDemo: UID dianggap tidak dipakai siswa (registrasi tak diblokir keliru)")
    void uidSiswaDemo() {
        assertThat(new UidSiswaDemo().dipakaiSiswa(1L, "DE000001")).isFalse();
    }

    @Test
    @DisplayName("AktivasiModulDemo: modul aktif & diketahui (internal-be disimulasikan)")
    void aktivasiModulDemo() {
        var info = new AktivasiModulDemo().status(1L);

        assertThat(info.isAktif()).isTrue();
        assertThat(info.isDiketahui()).isTrue();
    }
}
