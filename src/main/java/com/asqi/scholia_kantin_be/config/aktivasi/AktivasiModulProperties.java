package com.asqi.scholia_kantin_be.config.aktivasi;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfigurasi penegakan aktivasi modul kantin (PRD §10, OPEN-QUESTIONS <b>Q6</b>).
 *
 * <p>Toggle aktivasi modul kantin per sekolah <b>dimiliki internal-be</b>;
 * kantin-be hanya <i>menegakkan</i> status yang dilaporkan
 * {@code AktivasiModulPort}. Semua nilai punya default aman dan bisa
 * di-override lewat environment.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "kantin.aktivasi-modul")
public class AktivasiModulProperties {

    /**
     * Saklar penegakan. Default {@code true} — bila status sekolah diketahui
     * {@code aktif=false}, seluruh endpoint kantin ditolak (PRD §10).
     *
     * <p>Karena fallback Q6 mengembalikan {@code diketahui=false} (fail-open),
     * penegakan ini <b>tidak</b> memblokir apa pun sampai kontrak internal-be
     * terjawab — aman untuk dev/demo.
     */
    private boolean enabled = true;

    /**
     * TTL cache status aktivasi per sekolah (detik). Default 60 (sesuai
     * keputusan tim BE di issue #19). {@code <= 0} mematikan cache.
     *
     * <p>Cache <b>fail-open</b>: kegagalan Redis tidak pernah menolak request.
     */
    private int cacheSeconds = 60;
}
