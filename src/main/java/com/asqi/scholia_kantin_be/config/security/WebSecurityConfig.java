package com.asqi.scholia_kantin_be.config.security;

import com.asqi.scholia_kantin_be.config.security.jwt.JwtAuthTokenFilter;
import com.asqi.scholia_kantin_be.config.security.jwt.JwtAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
        com.asqi.scholia_kantin_be.config.security.jwt.JwtConfigValues.class})
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final JwtAuthTokenFilter jwtAuthTokenFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    /** Endpoint tanpa autentikasi. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health/**",
            "/actuator/info",
            "/api/webhook/**",
            "/error"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .anyRequest().authenticated()
        );

        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.exceptionHandling(ex ->
                ex.authenticationEntryPoint(jwtAuthenticationEntryPoint));

        http.addFilterBefore(jwtAuthTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS — mengizinkan FE kasir & back office. Daftar origin akhir menunggu
     * konfigurasi deploy; untuk pengembangan longgar agar tidak menghambat tim.
     * ⚠️ Persempit sebelum production.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
