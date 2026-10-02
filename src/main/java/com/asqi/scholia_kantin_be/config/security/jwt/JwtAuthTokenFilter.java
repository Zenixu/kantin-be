package com.asqi.scholia_kantin_be.config.security.jwt;

import com.asqi.scholia_kantin_be.config.security.KantinJwtDecoder;
import com.asqi.scholia_kantin_be.enums.SumberToken;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.KlaimResolver;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.security.blacklist.TokenBlacklistPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Filter autentikasi JWT kantin-be.
 *
 * <p>Menerima token dari:
 * <ol>
 *   <li>Header {@code Authorization: Bearer &lt;token&gt;} — jalur utama (klien API).</li>
 *   <li>Cookie (nama dari {@code jwt.cookieName}) — kompatibilitas FE SKOOLIA.</li>
 * </ol>
 *
 * <p>kantin-be tidak tahu issuer dari token mentah, jadi filter mencoba
 * <b>admin</b> dulu lalu <b>mobile</b>. Aman karena public key berbeda —
 * token hanya lolos bila signature cocok.
 *
 * <p><b>Perbaikan dari admin-be:</b>
 * <ul>
 *   <li>Jackson 3 ({@code tools.jackson}), bukan {@code com.fasterxml}.</li>
 *   <li>{@link TenantContext} selalu dibersihkan di {@code finally}.</li>
 *   <li>Token {@code typ=refresh} ditolak tegas (401).</li>
 *   <li>Tidak ada state bersama antar thread (hasil verifikasi di-record lokal).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthTokenFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final KantinJwtDecoder decoder;
    private final KlaimResolver klaimResolver;
    private final JwtConfigValues configValues;
    private final TokenBlacklistPort blacklist;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /** Hasil verifikasi: klaim + issuer yang cocok. */
    private record HasilVerifikasi(Claims claims, SumberToken sumber) {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = ambilToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            HasilVerifikasi hasil = verifikasiDuaIssuer(token);

            // Tolak refresh token sebagai access token.
            Object typ = hasil.claims().get("typ");
            if (typ != null && "refresh".equalsIgnoreCase(String.valueOf(typ))) {
                tolak(response, 401, "Refresh token tidak boleh dipakai sebagai access token");
                return;
            }

            IdentitasKantin identitas = klaimResolver.bangun(hasil.claims(), hasil.sumber());

            // Token yang dicabut platform ditolak — dicek setelah signature valid,
            // agar token palsu/rusak tidak menghabiskan query Redis.
            if (blacklist.tercabut(token)) {
                tolak(response, 401, "Token telah dicabut");
                return;
            }

            if (identitas.getUserId() == null || identitas.getUserId().isBlank()) {
                tolak(response, 401, "Token tidak memuat identitas pengguna");
                return;
            }

            List<GrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + identitas.getPeran().name()));

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    identitas, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

            // Konteks tenant untuk request ini (dibersihkan di finally).
            TenantContext.set(identitas);

            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException e) {
            tolak(response, 401, "Token kedaluwarsa");
        } catch (JwtException | IllegalArgumentException e) {
            tolak(response, 401, "Token tidak valid");
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Coba verifikasi sebagai token admin-be, lalu mobile-be.
     *
     * @throws JwtException bila kedua issuer menolak token
     */
    private HasilVerifikasi verifikasiDuaIssuer(String token) {
        JwtException kegagalanAdmin = null;
        try {
            return new HasilVerifikasi(decoder.verifikasi(token, SumberToken.ADMIN), SumberToken.ADMIN);
        } catch (JwtException e) {
            kegagalanAdmin = e;
        }

        try {
            return new HasilVerifikasi(decoder.verifikasi(token, SumberToken.MOBILE), SumberToken.MOBILE);
        } catch (JwtException e) {
            // Laporkan kegagalan admin (issuer pertama) — lebih relevan untuk mayoritas kasus.
            throw kegagalanAdmin != null ? kegagalanAdmin : e;
        }
    }

    private String ambilToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length()).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (Objects.equals(c.getName(), configValues.getCookieName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    private void tolak(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Response<?> body = CommonResponse.unauthenticated(message).getBody();
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
