package com.asqi.scholia_kantin_be.config.security;

import com.asqi.scholia_kantin_be.config.ratelimit.RateLimitFilter;
import com.asqi.scholia_kantin_be.config.ratelimit.RateLimitProperties;
import com.asqi.scholia_kantin_be.config.security.jwt.JwtAuthTokenFilter;
import com.asqi.scholia_kantin_be.config.security.jwt.JwtAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Konfigurasi keamanan kantin-be.
 *
 * <p><b>Berbeda dari admin-be:</b>
 * <ul>
 *   <li><b>Tanpa CSRF.</b> admin-be mengaktifkan CSRF cookie karena login
 *       berbasis cookie + browser. kantin-be murni API dengan Bearer token →
 *       CSRF di-disable (praktik standar API stateless). Bila kelak memakai
 *       cookie untuk FE, aktifkan kembali + sesuaikan.</li>
 *   <li><b>Tanpa AuthenticationProvider/PasswordEncoder/UserDetailsService.</b>
 *       kantin-be tidak punya login sendiri (ADR-0002) — identitas hanya dari
 *       verifikasi JWT.</li>
 *   <li><b>{@code @EnableMethodSecurity}</b> untuk RBAC berbasis anotasi
 *       ({@code @PreAuthorize}) di controller — dicek di backend (PRD §11.5).</li>
 * </ul>
 *
 * <p>Endpoint publik yang dikecualikan: health/actuator + webhook dari SKOOLIA
 * (callback top-up, sinkronisasi kartu). Webhook diamankan dengan
 * <b>signature/secara internal</b> di layer controller (bukan token user).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class,
        com.asqi.scholia_kantin_be.config.security.jwt.JwtConfigValues.class,
        RateLimitProperties.class})
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final JwtAuthTokenFilter jwtAuthTokenFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final RateLimitFilter rateLimitFilter;

    /** Endpoint tanpa autentikasi. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health/**",
            "/actuator/info",
            "/api/webhook/**",
            "/error"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           CorsConfigurationSource corsConfigurationSource) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.cors(cors -> cors.configurationSource(corsConfigurationSource));

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .anyRequest().authenticated()
        );

        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.exceptionHandling(ex ->
                ex.authenticationEntryPoint(jwtAuthenticationEntryPoint));

        // Urutan filter: JWT dulu agar identitas (sekolah:user) tersedia untuk
        // rate limit, lalu rate limit sebelum pemrosesan request.
        // Kedua filter didaftarkan manual di sini; auto-registrasi servlet
        // container dimatikan lewat FilterRegistrationBean di bawah (B33/#13).
        http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtAuthTokenFilter, RateLimitFilter.class);

        return http.build();
    }

    /**
     * Matikan auto-registrasi servlet container untuk filter yang didaftarkan
     * manual di {@link #filterChain(HttpSecurity, CorsConfigurationSource)}.
     *
     * <p><b>BUG B33 (#13):</b> sebagai {@code @Component}, Spring Boot
     * mendaftarkan {@code RateLimitFilter} &amp; {@code JwtAuthTokenFilter} ke
     * rantai filter servlet <b>dan</b> keduanya didaftarkan lagi lewat
     * {@code addFilterBefore}. Filter berjalan dua kali per request — untuk rate
     * limit ini memotong kuota (tiap request dihitung 2×). Bean ini
     * menonaktifkan registrasi otomatis; bean tetap ada untuk SecurityFilterChain.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthTokenFilter> jwtAuthTokenFilterRegistration(
            JwtAuthTokenFilter filter) {
        FilterRegistrationBean<JwtAuthTokenFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    /**
     * CORS — daftar origin diambil dari properti {@code kantin.cors.allowed-origins}
     * (dipisah koma). Default pengembangan: {@code http://localhost:5173}.
     *
     * <p><b>PERBAIKAN KEAMANAN:</b> versi lama memakai
     * {@code setAllowedOriginPatterns("*")} <b>bersamaan dengan</b>
     * {@code setAllowCredentials(true)}. Kombinasi itu membuat <b>setiap situs</b>
     * dapat mengirim request ber-kredensial (cookie) dan membaca respons —
     * praktis menonaktifkan isolasi origin. Kini origin eksplisit &amp; tidak
     * ada wildcard saat kredensial aktif.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${kantin.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
            List<String> allowedOrigins) {

        List<String> origins = allowedOrigins.stream()
                .map(String::trim)
                .filter(o -> !o.isBlank())
                .filter(o -> !"*".equals(o))
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
