package com.asqi.scholia_kantin_be.config.ratelimit;

import com.asqi.scholia_kantin_be.component.ratelimit.RateLimiterRedis;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AUDIT KEAMANAN — identitas rate limit untuk klien <b>tanpa token</b>.
 *
 * <p>Filter memakai entri <b>paling kiri</b> {@code X-Forwarded-For} sebagai
 * kunci. Header itu dikendalikan klien, jadi penyerang dapat memutar nilainya
 * tiap request untuk mendapat ember (bucket) baru → <b>rate limit terlewati</b>
 * pada endpoint publik (mis. {@code /api/webhook/**}).
 *
 * <p>Perilaku aman: pakai {@code request.getRemoteAddr()} (atau entri paling
 * kanan / hanya header dari proxy tepercaya) sebagai kunci.
 */
@DisplayName("AUDIT rate limit — X-Forwarded-For tidak boleh jadi kunci tunggal")
class AuditRateLimitSpoofTest {

    private RateLimiterRedis limiter;
    private RateLimitProperties props;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        limiter = mock(RateLimiterRedis.class);
        props = new RateLimitProperties();
        filter = new RateLimitFilter(limiter, props);
    }

    @AfterEach
    void tearDown() {
        com.asqi.scholia_kantin_be.security.TenantContext.clear();
    }

    @Test
    @DisplayName("X-Forwarded-For palsu tidak boleh mengubah kunci limit (tanpa proxy tepercaya)")
    void xffPalsuTidakMengubahKunci() throws Exception {
        when(limiter.periksa(anyString(), anyString(), anyInt()))
                .thenReturn(new RateLimiterRedis.Hasil(true, 1, 600));

        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/webhook/kartu");
        r.setRemoteAddr("10.0.0.9");                 // IP asli (yang dilihat server)
        r.addHeader("X-Forwarded-For", "1.2.3.4");   // dikendalikan klien

        filter.doFilter(r, new MockHttpServletResponse(), mock(FilterChain.class));

        ArgumentCaptor<String> identitas = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(limiter).periksa(anyString(), identitas.capture(), anyInt());

        assertThat(identitas.getValue())
                .as("kunci limit harus berbasis IP asli server, bukan XFF yang bisa dipalsukan")
                .isEqualTo("ip:10.0.0.9");
    }
}
