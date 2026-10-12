package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.repository.KartuTamuRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Jalur lookup kartu untuk mode <b>demo / dev</b> (issue #163).
 *
 * <p>Tujuan: membuka alur kasir <b>end-to-end</b> (tap → potong saldo/stok →
 * tutup sesi → cek laporan) di lokal <b>tanpa</b> menunggu data/keys tim SKOOLIA
 * (OPEN-QUESTIONS <b>Q7</b>). Cukup kenali <b>Kartu Tamu</b> yang di-seed oleh
 * {@code load/seed/seed-demo.sql} (data milik kantin-be sendiri, PRD §9.4).
 *
 * <p>Logika lookup identik dengan {@link KartuLookupLoadTest} karena keduanya
 * mewarisi {@link KartuLookupKartuTamu}; kelas ini hanya menyediakan <i>gerbang
 * property</i> yang berbeda agar profil {@code demo} terpisah dari {@code loadtest}.
 *
 * <p><b>Gerbang keamanan:</b> hanya hidup bila {@code kantin.demo.enabled=true}
 * (default <b>false</b>). {@code @Primary} agar menang atas
 * {@link com.asqi.scholia_kantin_be.service.integrasi.KartuLookupFallback}.
 *
 * <p><b>JANGAN aktifkan di staging/production.</b> Jangan aktifkan bersamaan
 * dengan {@code kantin.loadtest.enabled} (dua bean {@code @Primary} → ambigu).
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "kantin.demo", name = "enabled", havingValue = "true")
public class KartuLookupDemo extends KartuLookupKartuTamu {

    public KartuLookupDemo(KartuTamuRepository kartuTamuRepo) {
        super(kartuTamuRepo);
    }
}
