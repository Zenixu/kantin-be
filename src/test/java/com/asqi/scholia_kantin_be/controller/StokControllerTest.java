package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.BarangMasukRequest;
import com.asqi.scholia_kantin_be.model.StokCache;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.stok.HppService;
import com.asqi.scholia_kantin_be.service.stok.HasilMutasiStok;
import com.asqi.scholia_kantin_be.service.stok.StokOperasiService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uji wiring {@link StokController}: tenant dari konteks, proyeksi respons benar. */
@DisplayName("StokController — wiring barang-masuk/opname/lihat")
class StokControllerTest {

    private StokOperasiService operasi;
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
        operasi = mock(StokOperasiService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new StokController(operasi, new HppService()))
                .setCustomArgumentResolvers(new PrincipalResolver())
                .build();
        TenantContext.set(IdentitasKantin.builder().userId("42").sekolahId(7L).build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void barangMasukMeneruskanTenantDanAktor() throws Exception {
        BarangMasukRequest req = new BarangMasukRequest();
        req.setMenuId(10L);
        req.setQty(20);
        req.setHargaBeliPerUnit(5_000);
        req.setReferensiId("BM-2026-0001");

        when(operasi.masukBarang(eq(7L), eq(10L), eq(20), eq(5_000L), eq("BM-2026-0001"), eq(42L)))
                .thenReturn(HasilMutasiStok.baru(null, 20, 5_000L));

        mockMvc.perform(post("/api/stok/barang-masuk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void barangMasukQtyNolDitolakValidasi() throws Exception {
        BarangMasukRequest req = new BarangMasukRequest();
        req.setMenuId(10L);
        req.setQty(0); // invalid
        req.setHargaBeliPerUnit(5_000);
        req.setReferensiId("BM-1");

        mockMvc.perform(post("/api/stok/barang-masuk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lihatStokMenghitungNilaiPersediaan() throws Exception {
        StokCache cache = StokCache.builder()
                .menuId(10L)
                .sekolahId(7L)
                .stok(20)
                .hpp(5_000L)
                .stokMinimum(5)
                .build();

        when(operasi.lihat(7L, 10L)).thenReturn(cache);

        mockMvc.perform(get("/api/stok/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stok").value(20))
                .andExpect(jsonPath("$.data.nilaiPersediaan").value(100000))
                .andExpect(jsonPath("$.data.menipis").value(false));
    }
}
