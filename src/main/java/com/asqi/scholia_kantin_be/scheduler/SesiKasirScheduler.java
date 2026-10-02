package com.asqi.scholia_kantin_be.scheduler;

import com.asqi.scholia_kantin_be.service.kasir.SesiKasirService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Penjadwal auto-tutup sesi kasir (PRD §6.4).
 *
 * <p>Menutup sesi kasir yang masih {@code TERBUKA} padahal harinya sudah lewat,
 * agar total bersih tiap hari terkunci &amp; siap diposting ke Buku Kas. Berjalan
 * pada zona sekolah (default {@code Asia/Jakarta}) via {@code cron} yang bisa
 * dikonfigurasi.
 *
 * <p><b>Bisa dimatikan</b> lewat {@code kantin.scheduler.sesi.enabled=false}
 * (mis. pada test/dev tanpa perlu menjadwalkan tugas).
 *
 * <p><b>Idempoten:</b> hanya menutup sesi bertanggal &lt; hari ini, sehingga aman
 * dijalankan berulang / terlambat beberapa menit.
 */
@Component
@ConditionalOnProperty(name = "kantin.scheduler.sesi.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SesiKasirScheduler {

    private final SesiKasirService sesiKasirService;

    /**
     * Default: setiap hari pukul 23:59 zona kantin.
     *
     * <p>Bila penjadwal gagal (mis. aplikasi sedang restart tepat jam itu),
     * pemanggilan berikutnya tetap menutup sesi yang tertinggal karena filter
     * berbasis tanggal, bukan berbasis "hari ini saja".
     */
    @Scheduled(cron = "${kantin.scheduler.sesi.cron:0 59 23 * * *}",
            zone = "${kantin.zona-waktu:Asia/Jakarta}")
    public void autoTutupSesi() {
        log.debug("Penjadwal auto-tutup sesi kasir dijalankan");
        int ditutup = sesiKasirService.tutupOtomatisLintasTenant();
        if (ditutup == 0) {
            log.debug("Auto-tutup sesi: tidak ada sesi tertinggal");
        }
    }
}