package com.asqi.scholia_kantin_be.config;

import com.asqi.scholia_kantin_be.component.ratelimit.RateLimiterRedis;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * AUDIT B33 (#13) — filter keamanan <b>tidak boleh</b> terdaftar/berjalan dua kali.
 *
 * <p>{@code RateLimitFilter} &amp; {@code JwtAuthTokenFilter} adalah {@code @Component}
 * yang juga didaftarkan manual lewat {@code http.addFilterBefore(...)} di
 * {@code WebSecurityConfig}. Bila auto-registrasi servlet container tidak
 * dimatikan, keduanya terdaftar <b>dua kali</b>; untuk rate limit ini memotong
 * kuota (tiap request dihitung 2×).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
@DisplayName("AUDIT B33 — filter keamanan tidak terdaftar/berjalan dua kali")
class FilterGandaIT {

    @Autowired
    private ServletContext servletContext;

    @LocalServerPort
    private int port;

    @MockitoSpyBean
    private RateLimiterRedis rateLimiter;

    @Test
    @DisplayName("hanya springSecurityFilterChain terdaftar di servlet container")
    void filterTidakTerdaftarDiServletContainer() {
        Set<String> terdaftar = servletContext.getFilterRegistrations().keySet();

        System.out.println("FILTER-REGISTRATIONS=" + terdaftar);

        assertThat(terdaftar)
                .as("RateLimitFilter/JwtAuthTokenFilter didaftarkan manual di WebSecurityConfig; "
                        + "auto-registrasi servlet container harus dimatikan")
                .doesNotContain("rateLimitFilter", "jwtAuthTokenFilter");
    }

    @Test
    @DisplayName("satu request hanya menghabiskan SATU kuota rate-limit")
    void satuRequestSatuKuota() throws Exception {
        reset(rateLimiter);

        // Endpoint permitAll -> melewati SecurityFilterChain sampai tuntas,
        // sehingga filter yang (mungkin) terdaftar ganda ikut dieksekusi.
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/webhook/probe-filter-ganda"))
                .GET().build();
        client.send(req, HttpResponse.BodyHandlers.discarding());

        long dipanggil = mockingDetails(rateLimiter).getInvocations().stream()
                .filter(i -> "periksa".equals(i.getMethod().getName()))
                .count();
        System.out.println("PERIKSA-DIPANGGIL=" + dipanggil + " kali untuk 1 request");

        verify(rateLimiter, times(1)).periksa(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("memperbaiki registrasi TIDAK melumpuhkan filter (endpoint terlindungi tetap 401)")
    void filterMasihAktif() throws Exception {
        // Tanpa token: JwtAuthTokenFilter harus tetap menolak -> 401.
        // Menjaga agar perbaikan registrasi tidak diam-diam mematikan filter.
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/me"))
                .GET().build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

        System.out.println("AUTH-ME-TANPA-TOKEN=" + res.statusCode());
        assertThat(res.statusCode())
                .as("endpoint terlindungi tanpa token harus 401 (filter JWT tetap aktif)")
                .isEqualTo(401);
    }
}
