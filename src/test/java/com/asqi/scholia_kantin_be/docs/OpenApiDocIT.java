package com.asqi.scholia_kantin_be.docs;

import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji integrasi dokumentasi OpenAPI (springdoc) — issue #149.
 *
 * <p>Memastikan dokumen API <b>tergenerasi dari kode</b> dan dapat diakses:
 * {@code GET /v3/api-docs} (spesifikasi) & {@code GET /swagger-ui/index.html}
 * (UI). Ini menggantikan ketergantungan pada dokumen manual yang mudah basi.
 *
 * <p>Memakai konteks Spring penuh + web server acak (Testcontainers untuk DB),
 * karena springdoc baru membangun dokumen saat seluruh controller terdaftar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class OpenApiDocIT {

    @LocalServerPort
    private int port;

    private HttpResponse<String> get(String path) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("#149: /v3/api-docs tersedia & memuat info + endpoint kasir")
    void apiDocsTersedia() throws Exception {
        HttpResponse<String> res = get("/v3/api-docs");

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body())
                .contains("\"openapi\"")
                .contains("kantin-be API")
                .contains("/api/kasir/tap");
    }

    @Test
    @DisplayName("#149: Swagger UI dapat diakses")
    void swaggerUiTersedia() throws Exception {
        HttpResponse<String> res = get("/swagger-ui/index.html");
        assertThat(res.statusCode()).isBetween(200, 299);
    }
}
