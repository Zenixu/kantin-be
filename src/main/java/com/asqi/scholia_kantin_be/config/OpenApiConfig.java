package com.asqi.scholia_kantin_be.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Konfigurasi dokumentasi API (OpenAPI 3 + Swagger UI) — issue #149.
 *
 * <p>Menutup temuan audit #149: "Tidak ada springdoc/OpenAPI (dokumen API manual
 * → mudah basi)". Dokumen kini <b>tergenerasi dari kode</b> (anotasi controller),
 * sehingga {@code architecture/API-ENDPOINTS.md} tak lagi menjadi satu-satunya
 * sumber yang bisa basi.
 *
 * <p>Endpoint:
 * <ul>
 *   <li>{@code GET /v3/api-docs} — spesifikasi OpenAPI (JSON).</li>
 *   <li>{@code GET /swagger-ui.html} — UI interaktif.</li>
 * </ul>
 * Akses publiknya diatur {@code kantin.openapi.public} (lihat {@code WebSecurityConfig}).
 *
 * <p><b>Catatan keamanan:</b> skema keamanan {@code bearerAuth} dideklarasikan
 * agar Swagger UI bisa mengirim JWT saat mencoba endpoint terproteksi
 * (ADR-0002: kantin-be hanya menerima Bearer token dari SKOOLIA).
 */
@Configuration
public class OpenApiConfig {

    private static final String SKEMA_BEARER = "bearerAuth";

    @Bean
    public OpenAPI kantinOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("kantin-be API")
                        .version("1.0.0")
                        .description("""
                                API layanan kantin sekolah SKOOLIA (kantin-be).

                                Autentikasi: Bearer JWT (RS256) yang diterbitkan admin-be
                                (staf) & mobile-be (orang tua) — kantin-be tidak punya login
                                sendiri (ADR-0002). Klik "Authorize" dan tempel token untuk
                                mencoba endpoint terproteksi.

                                Catatan: sebagian endpoint masih menunggu kontrak tim lain
                                (OPEN-QUESTIONS Q1–Q8) dan berjalan dengan implementasi
                                *fallback* (fail-open/fail-closed) — lihat architecture/INTEGRATIONS.md.
                                """))
                .components(new Components().addSecuritySchemes(SKEMA_BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token RS256 dari admin-be/mobile-be")))
                .addSecurityItem(new SecurityRequirement().addList(SKEMA_BEARER));
    }
}
