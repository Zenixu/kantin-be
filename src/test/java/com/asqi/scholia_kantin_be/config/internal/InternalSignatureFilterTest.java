package com.asqi.scholia_kantin_be.config.internal;

import com.asqi.scholia_kantin_be.config.webhook.TandaTanganWebhook;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Uji filter endpoint internal (issue #29) — menolak request tak sah,
 * meneruskan request ber-HMAC sah. Pola sama dengan webhook (B34).
 */
class InternalSignatureFilterTest {

    private static final String RAHASIA = "rahasia-internal-uji";
    private static final byte[] BADAN = new byte[0]; // GET tanpa body

    private InternalApiProperties props;

    @BeforeEach
    void setUp() {
        props = new InternalApiProperties();
        props.setSecret(RAHASIA);
        props.setEnabled(true);
        props.setToleranceSeconds(300);
    }

    private InternalSignatureFilter filter() {
        return new InternalSignatureFilter(props);
    }

    private MockHttpServletRequest requestSah() {
        long ts = Instant.now().getEpochSecond();
        String ttd = TandaTanganWebhook.hitung(RAHASIA, String.valueOf(ts), BADAN);
        MockHttpServletRequest r = new MockHttpServletRequest(
                "GET", "/api/internal/kartu-tamu/cek-uid");
        r.setRemoteAddr("203.0.113.7");
        r.addHeader("X-Internal-Timestamp", String.valueOf(ts));
        r.addHeader("X-Internal-Signature", ttd);
        return r;
    }

    @Test
    @DisplayName("Signature sah ⇒ diteruskan")
    void sahDiteruskan() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter().doFilter(requestSah(), resp, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Signature salah ⇒ 401, tidak diteruskan")
    void signatureSalah401() throws Exception {
        MockHttpServletRequest r = requestSah();
        r.removeHeader("X-Internal-Signature");
        r.addHeader("X-Internal-Signature", "deadbeef".repeat(8));

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter().doFilter(r, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(401);
        verify(chain, times(0)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Timestamp kedaluwarsa (replay) ⇒ 401")
    void replay401() throws Exception {
        long tsLama = Instant.now().getEpochSecond() - 100_000;
        MockHttpServletRequest r = new MockHttpServletRequest(
                "GET", "/api/internal/kartu-tamu/cek-uid");
        r.addHeader("X-Internal-Timestamp", String.valueOf(tsLama));
        r.addHeader("X-Internal-Signature", TandaTanganWebhook.hitung(RAHASIA, String.valueOf(tsLama), BADAN));

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter().doFilter(r, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(401);
        verify(chain, times(0)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Rahasia kosong ⇒ 503 (fail-closed)")
    void tanpaRahasia503() throws Exception {
        props.setSecret("");

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter().doFilter(requestSah(), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(503);
        verify(chain, times(0)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Endpoint dinonaktifkan ⇒ 503")
    void dinonaktifkan503() throws Exception {
        props.setEnabled(false);

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter().doFilter(requestSah(), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(503);
    }

    @Test
    @DisplayName("IP di luar allowlist ⇒ 403 walau signature sah")
    void ipLuar403() throws Exception {
        props.setAllowedIps(List.of("10.0.0.0/8"));
        MockHttpServletRequest r = requestSah();
        r.setRemoteAddr("203.0.113.7");

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter().doFilter(r, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Filter hanya berlaku untuk /api/internal/**")
    void pathLainDilewati() {
        assertThat(filter().shouldNotFilter(
                new MockHttpServletRequest("GET", "/api/kartu-tamu"))).isTrue();
        assertThat(filter().shouldNotFilter(
                new MockHttpServletRequest("GET", "/api/internal/x"))).isFalse();
    }
}
