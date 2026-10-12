package com.asqi.scholia_kantin_be.config.integrasi;

import com.asqi.scholia_kantin_be.service.integrasi.UidSiswaPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Adapter REST nyata {@link UidSiswaPort} → admin-be (INTEGRATIONS.md §4.2,
 * docs/integrasi-anti-tabrakan-uid.md §2.2, issue <b>#160</b>).
 *
 * <p><b>Kontrak (Q7):</b>
 * {@code GET /api/internal/siswa/cek-uid?rfidUid=<UID>&sekolahId=<ID>}
 * → {@code { "dipakai": true|false }}. Diamankan HMAC-SHA256 + anti-replay
 * memakai {@code kantin.integrasi.uid-siswa.secret} (pola endpoint internal,
 * issue #29).
 *
 * <p><b>Fail-open.</b> Bila integrasi belum siap / gagal → {@code null}
 * (&ldquo;tidak diketahui&rdquo;) sehingga registrasi Kartu Tamu <b>tidak</b>
 * ditolak keliru; validasi lokal (UID unik antar Kartu Tamu) tetap berjalan.
 *
 * <p><b>Kapan hidup?</b> Hanya bila
 * {@code kantin.integrasi.uid-siswa.enabled=true} (default <b>false</b>).
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "kantin.integrasi.uid-siswa", name = "enabled", havingValue = "true")
@Slf4j
public class UidSiswaRestClient extends IntegrasiRestSupport implements UidSiswaPort {

    /** Path default cek UID siswa (Q7). */
    public static final String PATH_DEFAULT = "/api/internal/siswa/cek-uid";

    public UidSiswaRestClient(IntegrasiRestProperties properties) {
        super("UidSiswa", properties.getUidSiswa());
    }

    @Override
    public Boolean dipakaiSiswa(Long sekolahId, String rfidUid) {
        if (rfidUid == null || rfidUid.isBlank()) {
            return null;
        }
        try {
            String uid = URLEncoder.encode(rfidUid, StandardCharsets.UTF_8);
            StringBuilder url = new StringBuilder(path(PATH_DEFAULT))
                    .append("?rfidUid=").append(uid);
            if (sekolahId != null) {
                url.append("&sekolahId=").append(sekolahId);
            }
            String json = coba("cek UID " + rfidUid, () -> {
                var spec = klien().get().uri(url.toString());
                return beriHeader(spec, null).retrieve().body(String.class);
            });
            return parse(json);
        } catch (RestClientResponseException e) {
            log.warn("[UidSiswa] admin-be menolak cek UID {} (HTTP {}).", rfidUid,
                    e.getStatusCode().value());
            return null;
        } catch (RuntimeException e) {
            log.warn("[UidSiswa] Gagal cek UID {} (tidak diketahui, fail-open): {}", rfidUid,
                    e.getMessage());
            return null;
        }
    }

    private Boolean parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode d = data(JSON.readTree(json));
        if (d == null || d.isNull() || !d.isObject() || d.get("dipakai") == null
                || d.get("dipakai").isNull()) {
            return null;
        }
        return d.get("dipakai").asBoolean();
    }
}
