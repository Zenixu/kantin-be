package com.asqi.scholia_kantin_be.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

/**
 * Penjaga topologi database kantin (ADR-0010, OPEN-QUESTIONS <b>Q11</b>).
 *
 * <p>Mewujudkan jaminan <b>DB kantin terpisah dari admin-be</b> (ADR-0001) dengan
 * memeriksa datasource saat aplikasi siap:
 * <ol>
 *   <li><b>Pelanggaran</b> — nama DB kantin ada di {@code kantin.topologi.db-dilarang}
 *       (mis. {@code admin_db}) ⇒ menulis ke DB modul lain. Bila
 *       {@code kantin.topologi.enforce=true}, start <b>digagalkan</b>; selain itu
 *       dicatat sebagai ERROR.</li>
 *   <li><b>Peringatan</b> — host DB kantin <b>sama</b> dengan
 *       {@code kantin.topologi.host-db-admin-be} (bila diisi) ⇒ produksi sebaiknya
 *       server terpisah (ADR-0010 §Keputusan-2).</li>
 * </ol>
 *
 * <p>Memakai {@link Environment} (bukan mem-parsing JDBC langsung) agar tetap
 * sederhana &amp; andal terhadap variasi URL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PemeriksaTopologiDatabase {

    private final TopologiProperties properties;
    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void periksa() {
        String url = environment.getProperty("spring.datasource.url");
        String namaDb = namaDatabase(url);
        String host = host(url);

        // 1) Pelanggaran: menunjuk DB modul lain.
        List<String> dilarang = properties.getDbDilarang();
        if (namaDb != null && dilarang != null && dilarang.stream()
                .anyMatch(d -> d != null && d.equalsIgnoreCase(namaDb))) {
            String pesan = "Topologi DB MELANGGAR ADR-0010/Q11: kantin-be menunjuk database '"
                    + namaDb + "' milik modul lain. Kantin-be WAJIB memakai DB terpisah.";
            if (properties.isEnforce()) {
                throw new IllegalStateException(pesan);
            }
            log.error("{}. (enforce=false → hanya peringatan; aktifkan kantin.topologi.enforce=true di produksi)",
                    pesan);
        }

        // 2) Peringatan: host sama dengan admin-be.
        String hostAdmin = bersih(properties.getHostDbAdminBe());
        if (hostAdmin != null && host != null && host.equalsIgnoreCase(hostAdmin)) {
            log.warn("Topologi DB: host kantin == host admin-be ('{}'). Produksi sebaiknya "
                    + "server PostgreSQL terpisah (ADR-0010 §Keputusan-2).", host);
        }

        log.info("Topologi DB kantin: database='{}' host='{}' (ADR-0010/Q11).", namaDb, host);
    }

    /** Ekstrak nama database dari URL JDBC Postgres; {@code null} bila tak terbaca. */
    static String namaDatabase(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        // Buang query string, ambil segmen path terakhir: jdbc:postgresql://host:port/NAMA
        String tanpaQuery = url;
        int q = tanpaQuery.indexOf('?');
        if (q >= 0) {
            tanpaQuery = tanpaQuery.substring(0, q);
        }
        int slash = tanpaQuery.lastIndexOf('/');
        if (slash < 0 || slash == tanpaQuery.length() - 1) {
            return null;
        }
        String nama = tanpaQuery.substring(slash + 1).trim();
        return nama.isEmpty() ? null : nama;
    }

    /** Ekstrak host dari URL JDBC Postgres; {@code null} bila tak terbaca. */
    static String host(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String sisa = url;
        int sep = sisa.indexOf("//");
        if (sep >= 0) {
            sisa = sisa.substring(sep + 2);
        } else {
            return null;
        }
        int slash = sisa.indexOf('/');
        if (slash >= 0) {
            sisa = sisa.substring(0, slash);
        }
        int at = sisa.indexOf('@'); // buang kredensial user:pass@
        if (at >= 0) {
            sisa = sisa.substring(at + 1);
        }
        int colon = sisa.indexOf(':'); // buang port
        if (colon >= 0) {
            sisa = sisa.substring(0, colon);
        }
        sisa = sisa.trim();
        return sisa.isEmpty() ? null : sisa;
    }

    private static String bersih(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
