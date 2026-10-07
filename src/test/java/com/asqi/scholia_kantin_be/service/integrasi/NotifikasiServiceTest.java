package com.asqi.scholia_kantin_be.service.integrasi;

import com.asqi.scholia_kantin_be.config.notifikasi.NotifikasiProperties;
import com.asqi.scholia_kantin_be.enums.JenisNotifikasi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Uji unit fasad {@link NotifikasiService} (PRD §8.4) — jaminan
 * <b>best-effort/fail-open</b> &amp; filter subjek SISWA.
 *
 * <p>Port di-mock: tidak menyentuh DB/integrasi nyata.
 */
class NotifikasiServiceTest {

    private NotifikasiPort port;
    private NotifikasiProperties props;
    private NotifikasiService service;

    @BeforeEach
    void siap() {
        port = mock(NotifikasiPort.class);
        props = new NotifikasiProperties();
        props.setEnabled(true);
        service = new NotifikasiService(port, props);
    }

    private PerintahNotifikasi perintah(SubjekTipe tipe) {
        return PerintahNotifikasi.builder()
                .sekolahId(1L)
                .jenis(JenisNotifikasi.BELANJA)
                .subjekTipe(tipe)
                .subjekId(7L)
                .nominal(8_000L)
                .saldoSetelah(42_000L)
                .referensiId("trx-1")
                .ringkasan("Budi belanja Rp8.000")
                .build();
    }

    @Test
    @DisplayName("siswa — notifikasi diteruskan ke port & hasil terkirim diteruskan")
    void siswaDiteruskan() {
        when(port.kirim(any())).thenReturn(HasilKirimNotifikasi.terkirim("ok"));

        HasilKirimNotifikasi hasil = service.kirim(perintah(SubjekTipe.SISWA));

        assertThat(hasil.terkirim()).isTrue();
        verify(port).kirim(any());
    }

    @Test
    @DisplayName("non-siswa — dilewati tanpa memanggil port (tanpa ortu)")
    void nonSiswaDilewati() {
        HasilKirimNotifikasi hasil = service.kirim(perintah(SubjekTipe.KARTU_TAMU));

        assertThat(hasil.dilewati()).isTrue();
        verify(port, never()).kirim(any());
    }

    @Test
    @DisplayName("nonaktif — dilewati tanpa memanggil port (kantin.notifikasi.enabled=false)")
    void nonaktifDilewati() {
        props.setEnabled(false);

        HasilKirimNotifikasi hasil = service.kirim(perintah(SubjekTipe.SISWA));

        assertThat(hasil.dilewati()).isTrue();
        verify(port, never()).kirim(any());
    }

    @Test
    @DisplayName("port melempar exception — DITANGKAP, jadi GAGAL (transaksi tetap sah)")
    void exceptionDitangkapJadiGagal() {
        when(port.kirim(any())).thenThrow(new RuntimeException("mobile-be timeout"));

        HasilKirimNotifikasi hasil = service.kirim(perintah(SubjekTipe.SISWA));

        assertThat(hasil.status()).isEqualTo(HasilKirimNotifikasi.Status.GAGAL);
        assertThat(hasil.pesan()).contains("timeout");
    }

    @Test
    @DisplayName("port mengembalikan GAGAL — diteruskan apa adanya (tidak dilempar)")
    void gagalDiteruskan() {
        when(port.kirim(any())).thenReturn(HasilKirimNotifikasi.gagal("ditolak"));

        HasilKirimNotifikasi hasil = service.kirim(perintah(SubjekTipe.SISWA));

        assertThat(hasil.status()).isEqualTo(HasilKirimNotifikasi.Status.GAGAL);
    }

    @Test
    @DisplayName("perintah tidak lengkap (null) — dilewati, tidak melempar")
    void perintahNullDilewati() {
        HasilKirimNotifikasi hasil = service.kirim(null);

        assertThat(hasil.dilewati()).isTrue();
        verify(port, never()).kirim(any());
    }
}
