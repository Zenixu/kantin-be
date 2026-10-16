package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uji pencarian siswa DEMO (issue #214) — memastikan layar TU (top-up tunai
 * §9.2, kontrol siswa §9.6, kandidat refund §9.3) dapat diuji <b>tanpa</b>
 * menunggu data/kontrak admin-be (Q7).
 *
 * <p>Fokus: filter kata kunci (nama/NIS), filter {@code hanyaTidakAktif},
 * pembatasan {@code batas}, dan pengambilan saldo dari ledger lokal.
 */
class CariSiswaDemoTest {

    private final LedgerSaldoService ledger = mock(LedgerSaldoService.class);
    private final CariSiswaDemo cari = new CariSiswaDemo(ledger);

    CariSiswaDemoTest() {
        // Saldo demo: semua subjek SISWA bernilai tetap agar mudah diperiksa.
        when(ledger.saldo(anyLong(), eq(SubjekTipe.SISWA), anyLong())).thenReturn(25_000L);
    }

    @Test
    @DisplayName("#214: tanpa kata kunci → daftar awal (semua siswa aktif)")
    void tanpaKataKunciDaftarAwal() {
        List<InfoSiswa> hasil = cari.cari(1L, null, false, 50);

        assertThat(hasil).hasSize(6);
        assertThat(hasil).allSatisfy(s -> assertThat(s.getSaldo()).isEqualTo(25_000L));
    }

    @Test
    @DisplayName("#214: cari berdasarkan potongan NAMA (case-insensitive)")
    void cariBerdasarkanNama() {
        List<InfoSiswa> hasil = cari.cari(1L, "budi", false, 50);

        assertThat(hasil).hasSize(1);
        assertThat(hasil.get(0).getNama()).isEqualTo("Budi Santoso");
        assertThat(hasil.get(0).getNis()).isEqualTo("2024001");
        assertThat(hasil.get(0).getKelas()).isEqualTo("7A");
    }

    @Test
    @DisplayName("#214: cari berdasarkan NIS")
    void cariBerdasarkanNis() {
        List<InfoSiswa> hasil = cari.cari(1L, "2024003", false, 50);

        assertThat(hasil).hasSize(1);
        assertThat(hasil.get(0).getNama()).isEqualTo("Andi Wijaya");
    }

    @Test
    @DisplayName("#214: hanyaTidakAktif=true → hanya siswa nonaktif (kandidat refund §9.3)")
    void hanyaTidakAktif() {
        List<InfoSiswa> hasil = cari.cari(1L, null, true, 50);

        assertThat(hasil).hasSize(1);
        assertThat(hasil.get(0).getNama()).isEqualTo("Maya Sari");
        assertThat(hasil.get(0).getTidakAktif()).isTrue();
    }

    @Test
    @DisplayName("#214: batas membatasi jumlah hasil")
    void batasMembatasiHasil() {
        assertThat(cari.cari(1L, null, false, 2)).hasSize(2);
    }

    @Test
    @DisplayName("#214: kata kunci tak ada hasil → daftar kosong (bukan error)")
    void kataKunciTakAdaHasil() {
        assertThat(cari.cari(1L, "zzz-tidak-ada", false, 50)).isEmpty();
    }

    @Test
    @DisplayName("#214: saldo diambil per siswa dari ledger lokal (bukan hard-code)")
    void saldoDariLedger() {
        when(ledger.saldo(eq(1L), eq(SubjekTipe.SISWA), eq(101L))).thenReturn(99_000L);

        List<InfoSiswa> hasil = cari.cari(1L, "Budi", false, 50);

        assertThat(hasil.get(0).getSaldo()).isEqualTo(99_000L);
    }

    @Test
    @DisplayName("#214: sekolah lain tetap dilayani port (tenant dari pemanggil)")
    void sekolahDiteruskanKeLedger() {
        cari.cari(7L, "Budi", false, 50);

        org.mockito.Mockito.verify(ledger).saldo(eq(7L), eq(SubjekTipe.SISWA), anyLong());
    }
}
