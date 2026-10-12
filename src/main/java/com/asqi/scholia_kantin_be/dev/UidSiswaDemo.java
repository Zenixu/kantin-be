package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.service.integrasi.UidSiswaPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Adapter <b>demo</b> untuk {@link UidSiswaPort} (PRD §4.3, §9.4, OPEN-QUESTIONS Q7).
 *
 * <p>Meniru admin-be untuk cek anti-tabrakan UID (apakah {@code rfidUid} sudah
 * dipakai kartu siswa). Pada demo, jawabannya selalu {@code false}
 * (&ldquo;tidak dipakai siswa&rdquo;) sehingga registrasi Kartu Tamu dapat diuji
 * end-to-end tanpa data siswa. Validasi lokal (UID unik antar Kartu Tamu) tetap
 * berjalan lewat {@code KartuTamuRepository}.
 *
 * <p><b>Gerbang keamanan:</b> hanya hidup bila {@code kantin.demo.enabled=true}
 * (default <b>false</b>). {@code @Primary} agar menang atas
 * {@link com.asqi.scholia_kantin_be.service.integrasi.UidSiswaFallback}.
 *
 * <p><b>JANGAN aktifkan di staging/production.</b> Jangan aktifkan bersamaan
 * dengan adapter REST nyata ({@code kantin.integrasi.uid-siswa.enabled=true}) —
 * dua {@code @Primary} → ambigu.
 */
@Service
@Primary
@Slf4j
@ConditionalOnProperty(prefix = "kantin.demo", name = "enabled", havingValue = "true")
public class UidSiswaDemo implements UidSiswaPort {

    @Override
    public Boolean dipakaiSiswa(Long sekolahId, String rfidUid) {
        log.debug("[DEMO] Cek UID siswa (Q7) — UID={} dianggap TIDAK dipakai siswa.", rfidUid);
        return Boolean.FALSE;
    }
}
