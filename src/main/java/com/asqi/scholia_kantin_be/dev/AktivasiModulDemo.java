package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.service.integrasi.AktivasiModulInfo;
import com.asqi.scholia_kantin_be.service.integrasi.AktivasiModulPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Adapter <b>demo</b> untuk {@link AktivasiModulPort} (PRD §10, OPEN-QUESTIONS Q6).
 *
 * <p>Meniru internal-be yang melaporkan modul kantin <b>aktif</b>
 * ({@code diketahui=true}) untuk semua sekolah, sehingga alur pengecekan
 * aktivasi modul &amp; fee dapat diuji end-to-end tanpa API internal-be.
 *
 * <p><b>Gerbang keamanan:</b> hanya hidup bila {@code kantin.demo.enabled=true}
 * (default <b>false</b>). {@code @Primary} agar menang atas
 * {@link com.asqi.scholia_kantin_be.service.integrasi.AktivasiModulFallback}.
 *
 * <p><b>JANGAN aktifkan di staging/production.</b> Jangan aktifkan bersamaan
 * dengan adapter REST nyata ({@code kantin.integrasi.aktivasi.enabled=true}) —
 * dua {@code @Primary} → ambigu.
 */
@Service
@Primary
@Slf4j
@ConditionalOnProperty(prefix = "kantin.demo", name = "enabled", havingValue = "true")
public class AktivasiModulDemo implements AktivasiModulPort {

    @Override
    public AktivasiModulInfo status(Long sekolahId) {
        log.debug("[DEMO] Aktivasi modul (Q6) — sekolah={} dianggap AKTIF.", sekolahId);
        return AktivasiModulInfo.builder()
                .aktif(true)
                .diketahui(true)
                .catatan("Demo: modul kantin dianggap aktif (internal-be disimulasikan)")
                .build();
    }
}
