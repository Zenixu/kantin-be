package com.asqi.scholia_kantin_be.scheduler;

import com.asqi.scholia_kantin_be.service.integrasi.RetryPostingBukuKasService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Penjadwal retry posting Buku Kas (issue #33).
 *
 * <p>Posting saat tutup kasir bersifat <b>fail-open</b> (INTEGRATIONS.md §3.4):
 * bila admin-be sempat gangguan, sesi tetap DITUTUP namun entri Buku Kas
 * tertunggak. Penjadwal ini menyapu sesi tertunggak secara berkala dan mencoba
 * memposting ulang — idempoten, jadi aman dijalankan berulang.
 *
 * <p>Berjalan pada zona sekolah (default {@code Asia/Jakarta}) via {@code cron}
 * yang bisa dikonfigurasi. Default tiap 15 menit.
 *
 * <p><b>Bisa dimatikan</b> lewat {@code kantin.scheduler.retry-posting.enabled=false}
 * (mis. pada test/dev tanpa perlu menjadwalkan tugas).
 */
@Component
@ConditionalOnProperty(name = "kantin.scheduler.retry-posting.enabled",
        havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RetryPostingBukuKasScheduler {

    private final RetryPostingBukuKasService retryService;

    /**
     * Default: setiap 15 menit zona kantin.
     *
     * <p>Idempoten — bila penjadwal gagal/tergeser (mis. aplikasi restart),
     * pemanggilan berikutnya tetap menyapu semua sesi tertunggak karena filter
     * berbasis status &amp; flag {@code posting_buku_kas}, bukan waktu.
     */
    @Scheduled(cron = "${kantin.scheduler.retry-posting.cron:0 */15 * * * *}",
            zone = "${kantin.zona-waktu:Asia/Jakarta}")
    public void retryPostingTertunggak() {
        var hasil = retryService.postingTertunggak();
        if (hasil.tertunggak() > 0) {
            log.info("Retry posting Buku Kas: tertunggak={} sukses={} gagal={} dilewati={}",
                    hasil.tertunggak(), hasil.sukses(), hasil.gagal(), hasil.dilewati());
        }
    }
}
