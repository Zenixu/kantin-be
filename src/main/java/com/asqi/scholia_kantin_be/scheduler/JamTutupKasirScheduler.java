package com.asqi.scholia_kantin_be.scheduler;

import com.asqi.scholia_kantin_be.service.kasir.SesiKasirService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Penjadwal auto-tutup sesi kasir <b>sesuai jam tutup per sekolah</b>
 * (PRD §6.4, §9.1, issue #42).
 *
 * <p>Berbeda dari {@link SesiKasirScheduler} (jaring pengaman harian pukul 23:59
 * yang menutup sesi tertinggal hari-hari sebelumnya), penjadwal ini berjalan
 * <b>berkala</b> dan menutup sesi <b>hari ini</b> begitu jam tutup sekolah
 * terlewati — memakai pengaturan {@code jam_tutup_otomatis} per sekolah
 * ({@code SekolahKantinConfig}).
 *
 * <p>Idempoten: hanya menyentuh sesi yang masih {@code TERBUKA}. Bisa dimatikan
 * lewat {@code kantin.scheduler.jam-tutup.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "kantin.scheduler.jam-tutup.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class JamTutupKasirScheduler {

    private final SesiKasirService sesiKasirService;

    /**
     * Default: setiap 5 menit (zona kantin). Cukup rapat agar sesi tertutup tak
     * lama setelah jam tutup sekolah terlewati, tanpa membebani DB.
     */
    @Scheduled(cron = "${kantin.scheduler.jam-tutup.cron:0 */5 * * * *}",
            zone = "${kantin.zona-waktu:Asia/Jakarta}")
    public void autoTutupSesuaiJamSekolah() {
        log.debug("Penjadwal auto-tutup (jam tutup sekolah) dijalankan");
        int ditutup = sesiKasirService.tutupSesiLewatJamTutup();
        if (ditutup == 0) {
            log.debug("Auto-tutup (jam tutup sekolah): tidak ada sesi yang perlu ditutup");
        }
    }
}
