package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.MutasiSaldoItem;
import com.asqi.scholia_kantin_be.dto.SaldoResponse;
import com.asqi.scholia_kantin_be.dto.TopUpRequest;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.saldo.RefundSaldoService;
import com.asqi.scholia_kantin_be.service.saldo.SaldoOperasiService;
import com.asqi.scholia_kantin_be.service.saldo.SaldoTopUpService;
import com.asqi.scholia_kantin_be.service.saldo.SetoranTuService;
import com.asqi.scholia_kantin_be.service.kasir.HasilMutasiSaldo;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uji wiring {@link SaldoController}: tenant dari konteks, aktor dari token. */
@DisplayName("SaldoController — wiring topup/koreksi/lihat")
class SaldoControllerTest {

    private SaldoTopUpService topUpService;
    private SaldoOperasiService operasi;
    private RefundSaldoService refundService;
    private SetoranTuService setoranService;
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
        topUpService = mock(SaldoTopUpService.class);
        operasi = mock(SaldoOperasiService.class);
        refundService = mock(RefundSaldoService.class);
        setoranService = mock(SetoranTuService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SaldoController(topUpService, operasi, refundService, setoranService))
                .setCustomArgumentResolvers(new PrincipalResolver())
                .build();
        TenantContext.set(IdentitasKantin.builder().userId("42").sekolahId(7L).build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void topupMeneruskanTenantDanAktor() throws Exception {
        TopUpRequest req = new TopUpRequest();
        req.setSubjekTipe(SubjekTipe.SISWA);
        req.setSubjekId(100L);
        req.setNominal(50_000);
        req.setPenyetor("Ibu Ani");
        req.setReferensiId("TU-2026-0001");

        when(topUpService.topUpTunai(eq(7L), eq(SubjekTipe.SISWA), eq(100L),
                eq(50_000L), eq("Ibu Ani"), eq("TU-2026-0001"), eq(42L)))
                .thenReturn(HasilMutasiSaldo.baru(null, 50_000L));

        mockMvc.perform(post("/api/saldo/topup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(topUpService).topUpTunai(eq(7L), eq(SubjekTipe.SISWA), eq(100L),
                eq(50_000L), eq("Ibu Ani"), eq("TU-2026-0001"), eq(42L));
    }

    @Test
    void topupTanpaReferensiDitolakValidasi() throws Exception {
        TopUpRequest req = new TopUpRequest();
        req.setSubjekTipe(SubjekTipe.SISWA);
        req.setSubjekId(100L);
        req.setNominal(50_000);
        // referensiId kosong

        mockMvc.perform(post("/api/saldo/topup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lihatSaldoMengembalikanRingkasan() throws Exception {
        when(operasi.lihat(eq(7L), eq(SubjekTipe.SISWA), eq(100L), eq(20)))
                .thenReturn(SaldoResponse.builder()
                        .subjekTipe(SubjekTipe.SISWA).subjekId(100L)
                        .saldo(35_000L).belanjaHariIni(15_000L)
                        .mutasiTerbaru(List.<MutasiSaldoItem>of())
                        .build());

        mockMvc.perform(get("/api/saldo")
                        .param("subjekTipe", "SISWA")
                        .param("subjekId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saldo").value(35000))
                .andExpect(jsonPath("$.data.belanjaHariIni").value(15000));
    }
}
