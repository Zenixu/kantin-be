package com.asqi.scholia_kantin_be.config.webhook;

import com.asqi.scholia_kantin_be.helper.AlamatKlien;
import com.asqi.scholia_kantin_be.helper.IpAllowlist;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;

/**
 * Filter verifikasi webhook masuk (SECURITY.md §5, BUGS-DITEMUKAN B34).
 *
 * <p>Endpoint {@code /api/webhook/**} dibuka {@code permitAll} di
 * {@code WebSecurityConfig} (tanpa token user). Filter ini menggantikan
 * autentikasi tersebut dengan <b>verifikasi HMAC</b> + <b>anti-replay</b> +
 * <b>allowlist IP</b>, lalu meneruskan request ke controller dengan badan yang
 * masih bisa dibaca.
 *
 * <p>Urutan pemeriksaan (murah → mahal, fail-closed):
 * <ol>
 *   <li>Rahasia webhook terkonfigurasi? Tidak ⇒ <b>503</b> (endpoint tertutup).</li>
 *   <li>IP klien ada di allowlist (bila diisi)? Tidak ⇒ <b>403</b>.</li>
 *   <li>Header timestamp &amp; signature ada? Tidak ⇒ <b>401</b>.</li>
 *   <li>Badan request ≤ batas? Lewat ⇒ <b>413</b>.</li>
 *   <li>Timestamp dalam jendela toleransi? Tidak ⇒ <b>401</b> (anti-replay).</li>
 *   <li>HMAC-SHA256 cocok? Tidak ⇒ <b>401</b>.</li>
 * </ol>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
@RequiredArgsConstructor
@Slf4j
public class WebhookSignatureFilter extends OncePerRequestFilter {

    private static final String PREFIX_WEBHOOK = "/api/webhook";

    private final WebhookProperties properties;
    private final WebhookSignatureVerifier verifier;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(PREFIX_WEBHOOK);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1) Rahasia wajib ada — tanpa itu kita tak bisa memverifikasi apa pun.
        if (properties.getSecret() == null || properties.getSecret().isBlank()) {
            log.error("Webhook ditolak: rahasia belum dikonfigurasi (KANTIN_WEBHOOK_SECRET kosong).");
            tolak(response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Webhook belum dikonfigurasi di server");
            return;
        }

        // 2) Allowlist IP (bila diisi).
        IpAllowlist allowlist = IpAllowlist.dari(properties.getAllowedIps());
        if (!allowlist.kosong()) {
            String ip = AlamatKlien.ip(request, properties.getTrustedProxies());
            if (!allowlist.mengizinkan(ip)) {
                log.warn("Webhook ditolak: IP {} tidak ada di allowlist.", ip);
                tolak(response, HttpStatus.FORBIDDEN, "Alamat pengirim tidak diizinkan");
                return;
            }
        }

        // 3) Baca badan mentah dengan batas ukuran.
        byte[] badan = bacaBadan(request);
        if (badan == null) {
            tolak(response, HttpStatus.PAYLOAD_TOO_LARGE, "Badan permintaan terlalu besar");
            return;
        }

        // 4) Verifikasi tanda tangan & anti-replay.
        String timestamp = request.getHeader(properties.getHeaderTimestamp());
        String signature = request.getHeader(properties.getHeaderSignature());
        WebhookSignatureVerifier.Hasil hasil = verifier.verifikasi(timestamp, signature, badan);

        switch (hasil) {
            case SAH -> filterChain.doFilter(new BadanTertampung(request, badan), response);
            case TANPA_KONFIGURASI -> {
                log.error("Webhook ditolak: rahasia belum dikonfigurasi.");
                tolak(response, HttpStatus.SERVICE_UNAVAILABLE,
                        "Webhook belum dikonfigurasi di server");
            }
            case HEADER_KURANG -> tolak(response, HttpStatus.UNAUTHORIZED,
                    "Header tanda tangan webhook tidak lengkap");
            case TIMESTAMP_TIDAK_VALID -> tolak(response, HttpStatus.UNAUTHORIZED,
                    "Timestamp webhook tidak valid");
            case TIMESTAMP_KEDALUWARSA -> {
                log.warn("Webhook ditolak: timestamp kedaluwarsa (anti-replay), ts={}", timestamp);
                tolak(response, HttpStatus.UNAUTHORIZED, "Timestamp webhook kedaluwarsa");
            }
            case SIGNATURE_SALAH -> {
                log.warn("Webhook ditolak: tanda tangan tidak cocok dari IP {}.",
                        AlamatKlien.ip(request, properties.getTrustedProxies()));
                tolak(response, HttpStatus.UNAUTHORIZED, "Tanda tangan webhook tidak valid");
            }
        }
    }

    /** Baca badan mentah; {@code null} bila melebihi batas ukuran. */
    private byte[] bacaBadan(HttpServletRequest request) throws IOException {
        int maks = properties.getMaxBodyBytes();
        try (InputStream in = request.getInputStream()) {
            byte[] buf = new byte[8192];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int n;
            while ((n = in.read(buf)) != -1) {
                if (out.size() + n > maks) {
                    return null;
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private void tolak(HttpServletResponse response, HttpStatus status, String pesan) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Response<?> body = switch (status) {
            case UNAUTHORIZED -> CommonResponse.unauthenticated(pesan).getBody();
            case FORBIDDEN -> CommonResponse.forbidden(pesan).getBody();
            case SERVICE_UNAVAILABLE -> {
                Response<Void> r = new Response<>();
                r.setCode(503);
                r.setMessage(pesan);
                yield r;
            }
            default -> {
                Response<Void> r = new Response<>();
                r.setCode(status.value());
                r.setMessage(pesan);
                yield r;
            }
        };
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
