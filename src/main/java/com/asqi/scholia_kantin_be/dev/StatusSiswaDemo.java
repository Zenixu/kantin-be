package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.service.integrasi.StatusSiswaPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Adapter <b>demo</b> untuk {@link StatusSiswaPort} (PRD §9.3, OPEN-QUESTIONS Q7).
 *
 * <p>Meniru admin-be untuk status keaktifan siswa &amp; blokir kartu siswa,
 * sehingga alur <i>refund/pindah saldo saat siswa keluar</i> dapat diuji
 * end-to-end di lokal tanpa API admin-be. Pada demo, semua siswa dianggap
 * <b>aktif</b> ({@code tidakAktif=false}) dan pemblokiran kartu hanya dicatat
 * ke log (best-effort, PRD §11.11).
 *
 * <p><b>Gerbang keamanan:</b> hanya hidup bila {@code kantin.demo.enabled=true}
 * (default <b>false</b>). {@code @Primary} agar menang atas
 * {@link com.asqi.scholia_kantin_be.service.integrasi.StatusSiswaFallback}.
 *
 * <p><b>JANGAN aktifkan di staging/production.</b> Jangan aktifkan bersamaan
 * dengan adapter REST nyata ({@code kantin.integrasi.status-siswa.enabled=true}) —
 * dua {@code @Primary} → ambigu.
 */
@Service
@Primary
@Slf4j
@ConditionalOnProperty(prefix = "kantin.demo", name = "enabled", havingValue = "true")
public class StatusSiswaDemo implements StatusSiswaPort {

    @Override
    public Boolean tidakAktif(Long sekolahId, Long siswaId) {
        log.debug("[DEMO] Status siswa (Q7) — siswaId={} dianggap AKTIF.", siswaId);
        return Boolean.FALSE;
    }

    @Override
    public void blokirKartu(Long sekolahId, Long siswaId, String alasan) {
        log.info("[DEMO] Blokir kartu siswa (Q7) — siswaId={} disimulasikan diblokir. alasan={}",
                siswaId, alasan);
    }
}
