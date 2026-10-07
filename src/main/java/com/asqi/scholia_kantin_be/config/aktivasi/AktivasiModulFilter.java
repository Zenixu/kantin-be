package com.asqi.scholia_kantin_be.config.aktivasi;

import com.asqi.scholia_kantin_be.dto.AktivasiModulResponse;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.konfigurasi.AktivasiModulService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;

/**
 * Penegakan <b>aktivasi modul kantin per sekolah</b> (PRD §10, issue <b>#19</b>/Q6).
 *
 * <p>PRD §10: <i>"Bila nonaktif, seluruh menu dan endpoint kantin untuk sekolah
 * tersebut tidak tersedia."</i> Status aktivasi <b>dimiliki internal-be</b>; filter
 * ini hanya <i>menegakkan</i> apa yang dilaporkan {@link AktivasiModulService}
 * (lewat {@code AktivasiModulPort}).
 *
 * <p><b>Fail-open (pola solusi demo repo):</b> bila status <b>tidak diketahui</b>
 * (kontrak Q6 belum final, integrasi gagal, Redis down) → request <b>dibiarkan
 * lewat</b>. Memblokir seluruh kantin karena integrasi belum siap jauh lebih
 * merugikan. Penolakan hanya terjadi bila internal-be secara eksplisit menjawab
 * {@code aktif=false} ({@code diketahui=true}).
 *
 * <p><b>Scope:</b> hanya {@code /api/**} yang terautentikasi &amp; ber-tenant.
 * Dikecualikan: webhook (publik, bukan tenant), preflight CORS, health/actuator,
 * dan endpoint diagnostik {@code /api/pengaturan-kantin/aktivasi-modul} agar
 * status tetap bisa dibaca walau modul nonaktif.
 *
 * <p>Dijalankan <b>setelah</b> {@code JwtAuthTokenFilter} agar {@link TenantContext}
 * sudah terisi.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AktivasiModulFilter extends OncePerRequestFilter {

    private static final String PREFIX = "akt:modul:";

    private final AktivasiModulService aktivasiModulService;
    private final AktivasiModulProperties properties;
    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        IdentitasKantin identitas = TenantContext.get();
        // Belum terautentikasi / tanpa konteks sekolah → bukan urusan filter ini
        // (JwtAuthTokenFilter / entry point yang menangani 401).
        if (identitas == null || identitas.getSekolahId() == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Boolean aktif = cekAktif(identitas.getSekolahId());
        if (Boolean.FALSE.equals(aktif)) {
            tolak(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Hanya periksa endpoint kantin yang terautentikasi. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return true;
        }
        // Webhook publik (HMAC, bukan tenant) & login dev.
        if (path.startsWith("/api/webhook") || path.startsWith("/api/v1/auth/login")) {
            return true;
        }
        // Diagnostik: tetap boleh dibaca walau modul nonaktif.
        return path.startsWith("/api/pengaturan-kantin/aktivasi-modul");
    }

    /**
     * Status aktif sekolah, dengan cache Redis TTL pendek (fail-open).
     *
     * @return {@code TRUE} aktif, {@code FALSE} nonaktif (diketahui),
     *         {@code null} tidak diketahui → dibiarkan lewat
     */
    private Boolean cekAktif(Long sekolahId) {
        String kunci = PREFIX + sekolahId;
        boolean pakaiCache = properties.getCacheSeconds() > 0;

        if (pakaiCache) {
            try {
                String cached = redis.opsForValue().get(kunci);
                if ("1".equals(cached)) {
                    return Boolean.TRUE;
                }
                if ("0".equals(cached)) {
                    return Boolean.FALSE;
                }
            } catch (RuntimeException e) {
                log.warn("Cache aktivasi modul tak tersedia (fail-open): {}", e.getMessage());
            }
        }

        // AktivasiModulService sudah fail-open: exception/ketiadaan → diketahui=false.
        AktivasiModulResponse status = aktivasiModulService.status(sekolahId);
        if (!status.isDiketahui()) {
            // Tidak diketahui → jangan cache (biar cepat membaik saat kontrak siap), izinkan.
            return null;
        }

        if (pakaiCache) {
            try {
                redis.opsForValue().set(kunci, status.isAktif() ? "1" : "0",
                        Duration.ofSeconds(properties.getCacheSeconds()));
            } catch (RuntimeException e) {
                log.warn("Gagal meng-cache aktivasi modul (fail-open): {}", e.getMessage());
            }
        }
        return status.isAktif();
    }

    private void tolak(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.CONFLICT.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Response<?> body = CommonResponse.conflict(
                "Modul kantin tidak aktif untuk sekolah ini (PRD §10). "
                        + "Hubungi SKOOLIA untuk mengaktifkan modul.").getBody();
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
