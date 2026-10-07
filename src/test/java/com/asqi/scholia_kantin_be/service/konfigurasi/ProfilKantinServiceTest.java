package com.asqi.scholia_kantin_be.service.konfigurasi;

import com.asqi.scholia_kantin_be.config.KantinProfilProperties;
import com.asqi.scholia_kantin_be.dto.ProfilKantinResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji unit profil kantin — asumsi demo reader RFID (#22/Q17) &amp; postur
 * regulasi dana titipan (#24/Q15).
 */
class ProfilKantinServiceTest {

    @Test
    @DisplayName("#22/#24: default aman — reader = Kiosk, dana closed-loop bukan uang elektronik")
    void defaultAman() {
        KantinProfilProperties props = new KantinProfilProperties();
        ProfilKantinResponse r = new ProfilKantinService(props).ambil();

        // #22 / Q17 — asumsi reader
        assertThat(r.isReaderSamaDenganKiosk()).isTrue();
        assertThat(r.getReaderMode()).isEqualTo("KEYBOARD_WEDGE");
        assertThat(r.getReaderBacaMs()).isLessThanOrEqualTo(1000); // selaras SLO tap ≤1 dtk

        // #24 / Q15 — postur regulasi
        assertThat(r.getModelDana()).isEqualTo("DANA_TITIPAN_CLOSED_LOOP");
        assertThat(r.isUangElektronik()).isFalse();
        assertThat(r.isTarikTunai()).isFalse();
        assertThat(r.isTransferBebas()).isFalse();
        assertThat(r.isPerluKonfirmasiLegal()).isTrue();
    }

    @Test
    @DisplayName("#22/#24: nilai mengikuti konfigurasi (bisa diubah tanpa ubah kode)")
    void mengikutiKonfigurasi() {
        KantinProfilProperties props = new KantinProfilProperties();
        props.setReaderSamaDenganKiosk(false);
        props.setReaderMode("SERIAL");
        props.setModelDana("UANG_ELEKTRONIK");

        ProfilKantinResponse r = new ProfilKantinService(props).ambil();

        assertThat(r.isReaderSamaDenganKiosk()).isFalse();
        assertThat(r.getReaderMode()).isEqualTo("SERIAL");
        assertThat(r.getModelDana()).isEqualTo("UANG_ELEKTRONIK");
    }
}
