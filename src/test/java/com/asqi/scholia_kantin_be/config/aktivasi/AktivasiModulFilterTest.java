package com.asqi.scholia_kantin_be.config.aktivasi;

import com.asqi.scholia_kantin_be.dto.AktivasiModulResponse;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.konfigurasi.AktivasiModulService;
import com.asqi.scholia_kantin_be.service.integrasi.AktivasiModulInfo;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Uji penegakan aktivasi modul (PRD §10, issue #19/Q6).
 *
 * <p>Inti keputusan tim BE: <b>fail-open</b> — selama status tidak diketahui
 * (kontrak Q6 belum final) kantin <b>tidak</b> diblokir; hanya {@code aktif=false}
 * yang eksplisit dari internal-be yang ditolak (409).
 */
@DisplayName("AktivasiModulFilter — penegakan toggle modul per sekolah (fail-open)")
class AktivasiModulFilterTest {

    private AktivasiModulService service;
    private AktivasiModulProperties props;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private AktivasiModulFilter filter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        service = mock(AktivasiModulService.class);
        props = new AktivasiModulProperties();
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        filter = new AktivasiModulFilter(service, props, redis);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private MockHttpServletRequest req(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private AktivasiModulResponse status(boolean aktif, boolean diketahui) {
        return AktivasiModulResponse.builder()
                .sekolahId(7L).aktif(aktif).diketahui(diketahui).build();
    }

    @Test
    @DisplayName("status tidak diketahui (Q6 belum final) → fail-open, request dilewatkan")
    void tidakDiketahuiFailOpen() throws Exception {
        TenantContext.set(IdentitasKantin.builder().userId("42").sekolahId(7L).build());
        when(service.status(7L)).thenReturn(status(true, false));
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req("/api/kasir/tap"), res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("aktif=false eksplisit → 409, chain dihentikan (PRD §10)")
    void nonaktifDitolak() throws Exception {
        TenantContext.set(IdentitasKantin.builder().userId("42").sekolahId(7L).build());
        when(service.status(7L)).thenReturn(status(false, true));
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req("/api/kasir/tap"), res, chain);

        assertThat(res.getStatus()).isEqualTo(409);
        assertThat(res.getContentAsString()).contains("tidak aktif");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("aktif=true → request dilewatkan")
    void aktifDilewatkan() throws Exception {
        TenantContext.set(IdentitasKantin.builder().userId("42").sekolahId(7L).build());
        when(service.status(7L)).thenReturn(status(true, true));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req("/api/kasir/tap"), new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("tanpa konteks tenant (belum login) → dilewatkan, bukan tugas filter ini")
    void tanpaTenantDilewatkan() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req("/api/kasir/tap"), new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        verify(service, never()).status(any());
    }

    @Test
    @DisplayName("webhook & endpoint diagnostik aktivasi tidak difilter")
    void dikecualikan() {
        assertThat(filter.shouldNotFilter(req("/api/webhook/topup"))).isTrue();
        assertThat(filter.shouldNotFilter(req("/api/pengaturan-kantin/aktivasi-modul"))).isTrue();
        assertThat(filter.shouldNotFilter(req("/actuator/health"))).isTrue();
        assertThat(filter.shouldNotFilter(req("/api/kasir/tap"))).isFalse();
    }

    @Test
    @DisplayName("penegakan nonaktif via config → tidak pernah memanggil service")
    void saklarNonaktif() throws Exception {
        props.setEnabled(false);
        TenantContext.set(IdentitasKantin.builder().userId("42").sekolahId(7L).build());
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req("/api/kasir/tap"), new MockHttpServletResponse(), chain);

        verify(service, never()).status(any());
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("status diketahui di-cache; request berikut membaca cache")
    void cacheDipakai() throws Exception {
        TenantContext.set(IdentitasKantin.builder().userId("42").sekolahId(7L).build());
        when(service.status(7L)).thenReturn(status(false, true));
        when(valueOps.get(anyString())).thenReturn("0");
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req("/api/kasir/tap"), res, chain);

        assertThat(res.getStatus()).isEqualTo(409);
        verify(service, never()).status(any());
    }

    @Test
    @DisplayName("Redis error → fail-open (tidak pernah menolak karena cache rusak)")
    void redisErrorFailOpen() throws Exception {
        TenantContext.set(IdentitasKantin.builder().userId("42").sekolahId(7L).build());
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        when(service.status(7L)).thenReturn(status(true, false));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req("/api/kasir/tap"), new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
    }
}
