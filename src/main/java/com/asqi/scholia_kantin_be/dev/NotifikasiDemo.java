package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.service.integrasi.HasilKirimNotifikasi;
import com.asqi.scholia_kantin_be.service.integrasi.NotifikasiPort;
import com.asqi.scholia_kantin_be.service.integrasi.PerintahNotifikasi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Adapter <b>demo</b> untuk {@link NotifikasiPort} (PRD §8.4, OPEN-QUESTIONS Q5).
 *
 * <p>Meniru mobile-be yang menerima push notification, sehingga alur
 * <i>belanja/void/top-up/refund → notifikasi ortu</i> dapat diuji end-to-end di
 * lokal tanpa endpoint mobile-be. Mengembalikan
 * {@link HasilKirimNotifikasi#terkirim} dan mencatat ringkasan ke log
 * (pengganti push yang tak bisa diverifikasi otomatis).
 *
 * <p><b>Gerbang keamanan:</b> hanya hidup bila {@code kantin.demo.enabled=true}
 * (default <b>false</b>). {@code @Primary} agar menang atas
 * {@link com.asqi.scholia_kantin_be.service.integrasi.NotifikasiFallback}.
 *
 * <p><b>JANGAN aktifkan di staging/production.</b> Jangan aktifkan bersamaan
 * dengan adapter REST nyata ({@code kantin.integrasi.notifikasi.enabled=true}) —
 * dua {@code @Primary} → ambigu.
 */
@Service
@Primary
@Slf4j
@ConditionalOnProperty(prefix = "kantin.demo", name = "enabled", havingValue = "true")
public class NotifikasiDemo implements NotifikasiPort {

    @Override
    public HasilKirimNotifikasi kirim(PerintahNotifikasi perintah) {
        log.info("[DEMO] Notifikasi ortu (Q5) — jenis={} subjek={}:{} nominal={} : {}",
                perintah.getJenis(), perintah.getSubjekTipe(), perintah.getSubjekId(),
                perintah.getNominal(), perintah.getRingkasan());
        return HasilKirimNotifikasi.terkirim(
                "Demo: notifikasi disimulasikan terkirim ke mobile-be");
    }
}
