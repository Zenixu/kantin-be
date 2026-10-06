package com.asqi.scholia_kantin_be.config.ratelimit;

import com.asqi.scholia_kantin_be.component.ratelimit.RateLimiterRedis;
import com.asqi.scholia_kantin_be.helper.AlamatKlien;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Filter rate limit HTTP (SECURITY.md §7).
 *
 * <p>Kategori limit ditentukan dari path: endpoint autentikasi &amp; mutasi
 * sensitif mendapat batas lebih ketat. Identitas klien: {@code sekolahId:userId}
 * bila sudah terautentikasi, jika tidak alamat IP.
 *
 * <p>Dijalankan <b>setelah</b> {@code JwtAuthTokenFilter} (order lebih rendah
 * prioritasnya) agar identitas sudah tersedia — namun tetap aman bila
 * belum login (memakai IP).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterRedis limiter;
    private final RateLimitProperties properties;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        Kategori kategori = tentukanKategori(path);
        if (kategori == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String identitas = identitasKlien(request);
        RateLimiterRedis.Hasil hasil = limiter.periksa(kategori.kode, identitas, kategori.batas(properties));

        // Header standar agar klien tahu sisa kuota.
        response.setHeader("X-RateLimit-Limit", String.valueOf(hasil.batas()));

        if (!hasil.diizinkan()) {
            tolak(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Lewati health/actuator & preflight CORS. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }

    private enum Kategori {
        AUTH("auth"),
        SENSITIF("sensitif"),
        UMUM("umum");

        final String kode;

        Kategori(String kode) {
            this.kode = kode;
        }

        int batas(RateLimitProperties p) {
            return switch (this) {
                case AUTH -> p.getAuthLimit();
                case SENSITIF -> p.getSensitiveLimit();
                case UMUM -> p.getDefaultLimit();
            };
        }
    }

    private Kategori tentukanKategori(String path) {
        if (path.startsWith("/api/auth")) {
            return Kategori.AUTH;
        }
        // Mutasi bernilai uang / stok / katalog → ketat.
        if (path.startsWith("/api/kasir")
                || path.startsWith("/api/saldo")
                || path.startsWith("/api/stok")
                || path.startsWith("/api/katalog")) {
            return Kategori.SENSITIF;
        }
        if (path.startsWith("/api")) {
            return Kategori.UMUM;
        }
        return null;
    }

    /** Identitas klien: preferensi tenant:user, fallback alamat IP. */
    private String identitasKlien(HttpServletRequest request) {
        IdentitasKantin id = TenantContext.get();
        if (id != null && id.getUserId() != null && !id.getUserId().isBlank()) {
            Long sekolahId = id.getSekolahId();
            return (sekolahId == null ? "?" : sekolahId) + ":" + id.getUserId();
        }
        return "ip:" + alamatIp(request);
    }

    /**
     * Alamat IP klien untuk kunci rate limit.
     *
     * <p><b>Anti-spoof (audit keamanan):</b> delegasi ke {@link AlamatKlien} —
     * {@code X-Forwarded-For} hanya dipercaya bila koneksi datang dari proxy
     * tepercaya ({@code kantin.rate-limit.trusted-proxies}). Tanpa itu, header
     * XFF dikendalikan klien dan bisa diputar untuk melewati limit — jadi kita
     * pakai {@code remoteAddr} apa adanya.
     */
    private String alamatIp(HttpServletRequest request) {
        return AlamatKlien.ip(request, properties.getTrustedProxies());
    }

    private void tolak(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(properties.getWindowSeconds()));
        Response<?> body = CommonResponse.tooManyRequests(
                "Terlalu banyak permintaan, coba lagi dalam "
                        + properties.getWindowSeconds() + " detik").getBody();
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
