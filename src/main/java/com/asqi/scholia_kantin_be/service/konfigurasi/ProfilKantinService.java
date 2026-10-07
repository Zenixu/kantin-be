package com.asqi.scholia_kantin_be.service.konfigurasi;

import com.asqi.scholia_kantin_be.config.KantinProfilProperties;
import com.asqi.scholia_kantin_be.dto.ProfilKantinResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Profil perangkat &amp; postur kepatuhan kantin — solusi DEMO (#22/Q17, #24/Q15).
 *
 * <p>Membaca {@link KantinProfilProperties} dan menyajikannya sebagai satu
 * respons agar FE/tim luar (RFID, legal) bisa melihat asumsi yang dipakai
 * sekarang. Nilai diubah lewat konfigurasi, bukan kode.
 */
@Service
@RequiredArgsConstructor
public class ProfilKantinService {

    private final KantinProfilProperties profil;

    /** Rangkum profil perangkat (#22) &amp; postur regulasi (#24). */
    public ProfilKantinResponse ambil() {
        return ProfilKantinResponse.builder()
                .readerSamaDenganKiosk(profil.isReaderSamaDenganKiosk())
                .readerMode(profil.getReaderMode())
                .readerPanjangUid(profil.getReaderPanjangUid())
                .readerBacaMs(profil.getReaderBacaMs())
                .readerCatatan(profil.getReaderCatatan())
                .modelDana(profil.getModelDana())
                .uangElektronik(profil.isUangElektronik())
                .tarikTunai(profil.isTarikTunai())
                .transferBebas(profil.isTransferBebas())
                .perluKonfirmasiLegal(profil.isPerluKonfirmasiLegal())
                .legalCatatan(profil.getLegalCatatan())
                .build();
    }
}
