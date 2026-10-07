package com.asqi.scholia_kantin_be.service.konfigurasi;

import com.asqi.scholia_kantin_be.dto.AktivasiModulResponse;
import com.asqi.scholia_kantin_be.service.integrasi.AktivasiModulInfo;
import com.asqi.scholia_kantin_be.service.integrasi.AktivasiModulPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Fasad status aktivasi modul kantin &amp; fee platform (PRD §10, issue <b>#42</b>).
 *
 * <p>Toggle aktivasi modul &amp; fee platform <b>dimiliki internal-be</b>
 * (OPEN-QUESTIONS <b>Q6</b>) — kantin-be tidak menduplikasi data itu, hanya
 * menanyakannya lewat {@link AktivasiModulPort}. Fasad ini menjamin
 * <b>fail-open</b>: kegagalan/ketiadaan integrasi membuat modul dianggap
 * <i>aktif</i> agar kantin tidak mati (pola solusi demo repo).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AktivasiModulService {

    private final AktivasiModulPort port;

    /** Status aktivasi modul &amp; fee untuk sekolah (fail-open). */
    public AktivasiModulResponse status(Long sekolahId) {
        AktivasiModulInfo info;
        try {
            info = port.status(sekolahId);
            if (info == null) {
                info = AktivasiModulInfo.tidakDiketahui("Port aktivasi modul mengembalikan null");
            }
        } catch (RuntimeException e) {
            // Fail-open: integrasi gagal ⇒ modul dianggap aktif (kantin tetap jalan).
            log.warn("Aktivasi modul gagal (fail-open, modul dianggap aktif): {}", e.getMessage());
            info = AktivasiModulInfo.tidakDiketahui("Gagal memanggil integrasi: " + e.getMessage());
        }
        return AktivasiModulResponse.builder()
                .sekolahId(sekolahId)
                .aktif(info.isAktif())
                .diketahui(info.isDiketahui())
                .catatan(info.getCatatan())
                .feeTopup(info.getFeeTopup())
                .build();
    }
}
