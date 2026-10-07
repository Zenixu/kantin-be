package com.asqi.scholia_kantin_be.config.internal;

import com.asqi.scholia_kantin_be.config.webhook.BadanTertampung;
import com.asqi.scholia_kantin_be.config.webhook.TandaTanganWebhook;
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
import java.nio.charset.StandardCharsets;

/**
 * Filter verifikasi endpoint internal {@code /api/internal/**} (issue <b>#29</b>).
 *
 * <p>Pola sama dengan {@code WebhookSignatureFilter} (SECURITY.md §5, B34), tetapi
 * memakai <b>rahasia &amp; header terpisah</b> ({@code kantin.internal.*}) agar
 * endpoint internal tidak bergantung pada rahasia webhook masuk. Urutan
 * pemeriksaan (murah → mahal, <b>fail-closed</b>):
 * <ol>
 *   <li>Endpoint internal diaktifkan &amp; rahasia ada? Tidak ⇒ <b>503</b>.</li>
 *   <li>IP klien di allowlist (bila diisi)? Tidak ⇒ <b>403</b>.</li>
 *   <li>Header timestamp &amp; signature ada? Tidak ⇒ <b>401</b>.</li>
 *   <li>Badan ≤ batas? Lewat ⇒ <b>413</b>.</li>
 *   <li>Timestamp dalam jendela toleransi? Tidak ⇒ <b>401</b> (anti-replay).</li>
 *   <li>HMAC-SHA256 cocok? Tidak ⇒ <b>401</b>.</li>
 * </ol>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 25)
@RequiredArgsConstructor
@Slf4j
public class InternalSignatureFilter extends OncePerRequestFilter {

    private static final String PREFIX_INTERNAL = "/api/internal";

    private final InternalApiProperties properties;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(PREFIX_INTERNAL);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!properties.isEnabled()) {
            tolak(response, HttpStatus.SERVICE_UNAVAILABLE, "Endpoint internal dinonaktifkan");
            return;
        }
        if (properties.getSecret() == null || properties.getSecret().isBlank()) {
            log.error("Endpoint internal ditolak: rahasia belum dikonfigurasi (KANTIN_INTERNAL_SECRET kosong).");
            tolak(response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Endpoint internal belum dikonfigurasi di server");
            return;
        }

        IpAllowlist allowlist = IpAllowlist.dari(properties.getAllowedIps());
        if (!allowlist.kosong()) {
            String ip = AlamatKlien.ip(request, properties.getTrustedProxies());
            if (!allowlist.mengizinkan(ip)) {
                log.warn("Endpoint internal ditolak: IP {} tidak ada di allowlist.", ip);
                tolak(response, HttpStatus.FORBIDDEN, "Alamat pengirim tidak diizinkan");
                return;
            }
        }

        byte[] badan = bacaBadan(request);
        if (badan == null) {
            tolak(response, HttpStatus.PAYLOAD_TOO_LARGE, "Badan permintaan terlalu besar");
            return;
        }

        String timestamp = request.getHeader(properties.getHeaderTimestamp());
        String signature = request.getHeader(properties.getHeaderSignature());
        if (timestamp == null || timestamp.isBlank() || signature == null || signature.isBlank()) {
            tolak(response, HttpStatus.UNAUTHORIZED, "Header tanda tangan internal tidak lengkap");
            return;
        }

        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            tolak(response, HttpStatus.UNAUTHORIZED, "Timestamp internal tidak valid");
            return;
        }
        long sekarang = java.time.Instant.now().getEpochSecond();
        long toleransi = properties.getToleranceSeconds();
        if (ts < sekarang - toleransi || ts > sekarang + toleransi) {
            log.warn("Endpoint internal ditolak: timestamp kedaluwarsa (anti-replay), ts={}", timestamp);
            tolak(response, HttpStatus.UNAUTHORIZED, "Timestamp internal kedaluwarsa");
            return;
        }

        String diharapkan = TandaTanganWebhook.hitung(properties.getSecret(), timestamp.trim(), badan);
        if (!TandaTanganWebhook.cocok(diharapkan, signature)) {
            log.warn("Endpoint internal ditolak: tanda tangan tidak cocok dari IP {}.",
                    AlamatKlien.ip(request, properties.getTrustedProxies()));
            tolak(response, HttpStatus.UNAUTHORIZED, "Tanda tangan internal tidak valid");
            return;
        }

        filterChain.doFilter(new BadanTertampung(request, badan), response);
    }

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
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Response<?> body = switch (status) {
            case UNAUTHORIZED -> CommonResponse.unauthenticated(pesan).getBody();
            case FORBIDDEN -> CommonResponse.forbidden(pesan).getBody();
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
