package com.asqi.scholia_kantin_be.config.ratelimit;

import com.asqi.scholia_kantin_be.component.ratelimit.RateLimiterRedis;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Uji filter rate limit: kategori path, 429, header, identitas klien. */
@DisplayName("RateLimitFilter — penerapan limit per kategori")
class RateLimitFilterTest {

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
        TenantContext.clear();
    }

    private MockHttpServletRequest req(String uri) {
        return new MockHttpServletRequest("POST", uri);
    }

    @Test
    @DisplayName("endpoint auth memakai batas auth")
    void kategoriAuth() throws Exception {
        when(limiter.periksa(eq("auth"), anyString(), eq(20)))
                .thenReturn(new RateLimiterRedis.Hasil(true, 1, 20));

        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req("/api/auth/refresh"), res, mock(FilterChain.class));

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getHeader("X-RateLimit-Limit")).isEqualTo("20");
    }

    @Test
    @DisplayName("mutasi kasir memakai batas sensitif")
    void kategoriSensitif() throws Exception {
        when(limiter.periksa(eq("sensitif"), anyString(), eq(60)))
                .thenReturn(new RateLimiterRedis.Hasil(true, 1, 60));

        filter.doFilter(req("/api/kasir/tap"), new MockHttpServletResponse(),
                mock(FilterChain.class));

        verify(limiter).periksa(eq("sensitif"), anyString(), eq(60));
    }

    @Test
    @DisplayName("melebihi batas → 429 + Retry-After, chain dihentikan")
    void ditolak429() throws Exception {
        when(limiter.periksa(anyString(), anyString(), anyInt()))
                .thenReturn(new RateLimiterRedis.Hasil(false, 99, 20));

        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req("/api/kasir/tap"), res, chain);

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("60");
        assertThat(res.getContentAsString()).contains("Terlalu banyak");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("rate limit nonaktif → chain langsung lanjut")
    void nonaktifDilewati() throws Exception {
        props.setEnabled(false);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req("/api/kasir/tap"), res, chain);

        verify(limiter, never()).periksa(anyString(), anyString(), anyInt());
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("identitas klien memakai sekolahId:userId bila login")
    void identitasDariTenant() throws Exception {
        TenantContext.set(IdentitasKantin.builder().userId("42").sekolahId(7L).build());
        when(limiter.periksa(anyString(), anyString(), anyInt()))
                .thenReturn(new RateLimiterRedis.Hasil(true, 1, 60));

        filter.doFilter(req("/api/kasir/tap"), new MockHttpServletResponse(),
                mock(FilterChain.class));

        verify(limiter).periksa(eq("sensitif"), eq("7:42"), eq(60));
    }

    @Test
    @DisplayName("tanpa login → identitas memakai IP")
    void identitasDariIp() throws Exception {
        when(limiter.periksa(anyString(), anyString(), anyInt()))
                .thenReturn(new RateLimiterRedis.Hasil(true, 1, 20));

        MockHttpServletRequest r = req("/api/auth/refresh");
        r.setRemoteAddr("10.1.2.3");
        filter.doFilter(r, new MockHttpServletResponse(), mock(FilterChain.class));

        verify(limiter).periksa(eq("auth"), eq("ip:10.1.2.3"), eq(20));
    }
}
