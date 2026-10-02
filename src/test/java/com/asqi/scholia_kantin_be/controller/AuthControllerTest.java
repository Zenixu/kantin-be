package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.config.security.KantinJwtDecoder;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.enums.SumberToken;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.security.blacklist.TokenBlacklistPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uji endpoint {@link AuthController} (me + cabut token). */
@DisplayName("AuthController — me & cabut token")
class AuthControllerTest {

    private TokenBlacklistPort blacklist;
    private KantinJwtDecoder decoder;
    private MockMvc mockMvc;

    private static class PrincipalResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return TenantContext.get();
        }
    }

    @BeforeEach
    void setUp() {
        blacklist = mock(TokenBlacklistPort.class);
        decoder = mock(KantinJwtDecoder.class);

        AuthController controller = new AuthController(blacklist, decoder);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PrincipalResolver())
                .build();

        TenantContext.set(IdentitasKantin.builder()
                .userId("42")
                .sekolahId(7L)
                .peran(AktorKantin.ADMIN_SEKOLAH)
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /me mengembalikan konteks dari principal")
    void meMengembalikanKonteks() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("42"))
                .andExpect(jsonPath("$.data.sekolahId").value(7));
    }

    @Test
    @DisplayName("POST /cabut mencabut token dengan TTL = sisa umur")
    void cabutDenganTtlSisaUmur() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getExpiration())
                .thenReturn(Date.from(Instant.now().plusSeconds(600)));
        when(decoder.verifikasi(eq("tok-1"), any(SumberToken.class))).thenReturn(claims);

        mockMvc.perform(post("/api/auth/cabut")
                        .header("Authorization", "Bearer tok-1"))
                .andExpect(status().isOk());

        // TTL di kisaran 600 detik (toleransi waktu eksekusi 1 detik).
        var captor = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(blacklist).cabut(eq("tok-1"), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue())
                .isBetween(598L, 600L);
    }

    @Test
    @DisplayName("POST /cabut tanpa Authorization header → 400")
    void cabutTanpaToken() throws Exception {
        mockMvc.perform(post("/api/auth/cabut"))
                .andExpect(status().isBadRequest());
        verify(blacklist, never()).cabut(anyString(), eq(0L));
    }

    @Test
    @DisplayName("POST /cabut dengan token tak valid → tetap 200, TTL 0")
    void cabutTokenTakValid() throws Exception {
        when(decoder.verifikasi(anyString(), any(SumberToken.class)))
                .thenThrow(new JwtException("rusak") {});

        mockMvc.perform(post("/api/auth/cabut")
                        .header("Authorization", "Bearer rusak"))
                .andExpect(status().isOk());

        verify(blacklist).cabut(eq("rusak"), eq(0L));
    }
}
