package com.asqi.scholia_kantin_be.service.konfigurasi;

import com.asqi.scholia_kantin_be.service.integrasi.AktivasiModulFallback;
import com.asqi.scholia_kantin_be.service.integrasi.AktivasiModulInfo;
import com.asqi.scholia_kantin_be.service.integrasi.AktivasiModulPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji unit fasad aktivasi modul (PRD §10, issue #42) — <b>fail-open</b>:
 * ketiadaan/kegagalan integrasi internal-be (Q6) tidak boleh mematikan kantin.
 */
class AktivasiModulServiceTest {

    @Test
    @DisplayName("fallback (Q6 belum terjawab) → modul dianggap AKTIF, diketahui=false")
    void fallbackFailOpen() {
        var service = new AktivasiModulService(new AktivasiModulFallback());

        var hasil = service.status(1L);

        assertThat(hasil.isAktif()).isTrue();
        assertThat(hasil.isDiketahui()).isFalse();
        assertThat(hasil.getCatatan()).contains("Q6");
    }

    @Test
    @DisplayName("port melempar exception → fail-open, modul tetap dianggap aktif")
    void exceptionFailOpen() {
        AktivasiModulPort meledak = sekolahId -> {
            throw new IllegalStateException("internal-be timeout");
        };
        var service = new AktivasiModulService(meledak);

        var hasil = service.status(1L);

        assertThat(hasil.isAktif()).isTrue();
        assertThat(hasil.isDiketahui()).isFalse();
        assertThat(hasil.getCatatan()).contains("timeout");
    }

    @Test
    @DisplayName("port menjawab jelas → status diteruskan apa adanya")
    void statusDiteruskan() {
        AktivasiModulPort port = sekolahId -> AktivasiModulInfo.builder()
                .aktif(false).diketahui(true).feeTopup(2_500L).catatan("dari internal-be").build();
        var service = new AktivasiModulService(port);

        var hasil = service.status(1L);

        assertThat(hasil.isAktif()).isFalse();
        assertThat(hasil.isDiketahui()).isTrue();
        assertThat(hasil.getFeeTopup()).isEqualTo(2_500L);
    }
}
