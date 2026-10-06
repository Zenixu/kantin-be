package com.asqi.scholia_kantin_be.config.webhook;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * AUDIT KEAMANAN (B34) — filter webhook menolak request tak sah.
 *
 * <p>Menjaga janji SECURITY.md §5:
 * <ul>
 *   <li>signature salah ⇒ <b>401</b></li>
 *   <li>timestamp kedaluwarsa (replay) ⇒ <b>401</b></li>
 *   <li>rahasia belum dikonfigurasi ⇒ <b>503</b> (fail-closed)</li>
 *   <li>IP di luar allowlist ⇒ <b>403</b></li>
 * </ul>
 * Request sah diteruskan ke controller.
 */
@DisplayName("AUDIT webhook — filter menolak signature salah/replay")
class WebhookSignatureFilterTest {

    private static final String RAHASIA = "rahasia-webhook-uji";
    private static final byte[] BADAN = "{\"eventType\":\"TOPUP\"}".getBytes(StandardCharsets.UTF_8);

    private WebhookProperties props;

    @BeforeEach
    void setUp() {
        props = new WebhookProperties();
        props.setSecret(RAHASIA);
        props.setToleranceSeconds(300);
    }

    private WebhookSignatureFilter filter() {
        return new WebhookSignatureFilter(props, new WebhookSignatureVerifier(props));
    }

    private MockHttpServletRequest requestSah() {
        long ts = Instant.now().getEpochSecond();
        String tandaTangan = TandaTanganWebhook.hitung(RAHASIA, String.valueOf(ts), BADAN);
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/webhook/skoolia");
        r.setContent(BADAN);
        r.setRemoteAddr("203.0.113.7");
        r.addHeader("X-Webhook-Timestamp", String.valueOf(ts));
        r.addHeader("X-Webhook-Signature", tandaTangan);
        return r;
    }

    @Test
    @DisplayName("Signature sah ⇒ diteruskan ke controller (200-hilir)")
    void signatureSahDiteruskan() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter().doFilter(requestSah(), resp, chain);

        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Signature salah ⇒ 401, tidak diteruskan")
    void signatureSalahDitolak401() throws Exception {
        MockHttpServletRequest r = requestSah();
        r.removeHeader("X-Webhook-Signature");
        r.addHeader("X-Webhook-Signature", "deadbeef".repeat(8));

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter().doFilter(r, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(401);
        verify(chain, times(0)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Timestamp kedaluwarsa (replay) ⇒ 401 walau signature dihitung benar")
    void timestampKedaluwarsaDitolak401() throws Exception {
        long tsLama = Instant.now().getEpochSecond() - 100_000;
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/webhook/skoolia");
        r.setContent(BADAN);
        r.addHeader("X-Webhook-Timestamp", String.valueOf(tsLama));
        r.addHeader("X-Webhook-Signature", TandaTanganWebhook.hitung(RAHASIA, String.valueOf(tsLama), BADAN));

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter().doFilter(r, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(401);
        verify(chain, times(0)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Header tanda tangan kurang ⇒ 401")
    void headerKurangDitolak401() throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/webhook/skoolia");
        r.setContent(BADAN);

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter().doFilter(r, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Rahasia belum dikonfigurasi ⇒ 503 (fail-closed, endpoint tidak terbuka)")
    void tanpaRahasiaDitolak503() throws Exception {
        props.setSecret("");

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter().doFilter(requestSah(), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(503);
        verify(chain, times(0)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("IP di luar allowlist ⇒ 403 walau signature sah")
    void ipLuarAllowlistDitolak403() throws Exception {
        props.setAllowedIps(List.of("10.0.0.0/8"));

        MockHttpServletRequest r = requestSah();
        r.setRemoteAddr("203.0.113.7"); // di luar 10.0.0.0/8

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter().doFilter(r, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(403);
        verify(chain, times(0)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("IP di dalam allowlist & signature sah ⇒ diteruskan")
    void ipDalamAllowlistDiteruskan() throws Exception {
        props.setAllowedIps(List.of("203.0.113.0/24"));

        MockHttpServletRequest r = requestSah();
        r.setRemoteAddr("203.0.113.7");

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter().doFilter(r, resp, chain);

        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Badan melebihi batas ⇒ 413")
    void badanTerlaluBesarDitolak413() throws Exception {
        props.setMaxBodyBytes(4);

        MockHttpServletRequest r = requestSah(); // badannya lebih dari 4 byte

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter().doFilter(r, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(413);
        verify(chain, times(0)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Filter hanya berlaku untuk /api/webhook/** — path lain dilewati")
    void pathLainDilewati() {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/saldo/topup");
        assertThat(filter().shouldNotFilter(r)).isTrue();
        MockHttpServletRequest w = new MockHttpServletRequest("POST", "/api/webhook/x");
        assertThat(filter().shouldNotFilter(w)).isFalse();
    }
}
