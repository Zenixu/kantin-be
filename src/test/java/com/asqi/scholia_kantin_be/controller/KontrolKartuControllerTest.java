package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.BlokirKartuRequest;
import com.asqi.scholia_kantin_be.dto.KontrolSubjekResponse;
import com.asqi.scholia_kantin_be.dto.LimitHarianRequest;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.kartu.KontrolKartuService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uji wiring {@link KontrolKartuController}: tenant dari konteks, aktor dari token. */
@DisplayName("KontrolKartuController — wiring blokir/limit/item")
class KontrolKartuControllerTest {

    private KontrolKartuService service;
    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    private static class PrincipalResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter p, ModelAndViewContainer m,
                                      NativeWebRequest r, WebDataBinderFactory b) {
            return TenantContext.get();
        }
    }

    @BeforeEach
    void setUp() {
        service = mock(KontrolKartuService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new KontrolKartuController(service))
                .setCustomArgumentResolvers(new PrincipalResolver())
                .build();
        TenantContext.set(IdentitasKantin.builder().userId("42").sekolahId(7L).build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private KontrolSubjekResponse contoh() {
        return KontrolSubjekResponse.builder()
                .subjekTipe("SISWA").subjekId(7L).diblokir(true)
                .menuDiblokir(List.of()).kategoriDiblokir(List.of()).build();
    }

    @Test
    void blokirMeneruskanTenantDanAktor() throws Exception {
        BlokirKartuRequest req = new BlokirKartuRequest();
        req.setSubjekTipe(SubjekTipe.SISWA);
        req.setSubjekId(7L);
        req.setAlasan("hilang");

        when(service.ubahBlokir(eq(7L), eq(SubjekTipe.SISWA), eq(7L), eq(true), eq("hilang"), eq(42L)))
                .thenReturn(contoh());

        mockMvc.perform(post("/api/kontrol-kartu/blokir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diblokir").value(true));

        verify(service).ubahBlokir(eq(7L), eq(SubjekTipe.SISWA), eq(7L), eq(true), eq("hilang"), eq(42L));
    }

    @Test
    void setLimitMeneruskanNominal() throws Exception {
        LimitHarianRequest req = new LimitHarianRequest();
        req.setSubjekTipe(SubjekTipe.SISWA);
        req.setSubjekId(7L);
        req.setNominal(25_000L);

        when(service.setLimit(eq(7L), eq(SubjekTipe.SISWA), eq(7L), eq(25_000L), eq(42L)))
                .thenReturn(contoh());

        mockMvc.perform(put("/api/kontrol-kartu/limit-harian")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(service).setLimit(eq(7L), eq(SubjekTipe.SISWA), eq(7L), eq(25_000L), eq(42L));
    }

    @Test
    void kontrolMeneruskanPathParam() throws Exception {
        when(service.kontrol(eq(7L), eq(SubjekTipe.SISWA), eq(7L))).thenReturn(contoh());

        mockMvc.perform(get("/api/kontrol-kartu/SISWA/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjekTipe").value("SISWA"));

        verify(service).kontrol(eq(7L), eq(SubjekTipe.SISWA), eq(7L));
    }
}
