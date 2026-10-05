package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.MenuRequest;
import com.asqi.scholia_kantin_be.enums.SatuanMenu;
import com.asqi.scholia_kantin_be.model.Menu;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.katalog.KatalogService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uji wiring {@link KatalogController}: tenant dari konteks, aktor dari token. */
@DisplayName("KatalogController — wiring kategori/menu")
class KatalogControllerTest {

    private KatalogService katalog;
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
        katalog = mock(KatalogService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new KatalogController(katalog))
                .setCustomArgumentResolvers(new PrincipalResolver())
                .build();
        TenantContext.set(IdentitasKantin.builder().userId("42").sekolahId(7L).build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void buatMenuMeneruskanTenantDanAktor() throws Exception {
        MenuRequest req = new MenuRequest();
        req.setNama("Nasi Goreng");
        req.setHargaJual(15_000L);
        req.setSatuan(SatuanMenu.PORSI);
        req.setStokMinimum(5);

        Menu menu = Menu.builder().id(99L).sekolahId(7L).nama("Nasi Goreng")
                .hargaJual(15_000L).satuan(SatuanMenu.PORSI).stokMinimum(5).isActive(true).build();
        when(katalog.buatMenu(eq(7L), eq(null), eq("Nasi Goreng"), eq(15_000L),
                eq(SatuanMenu.PORSI), eq(null), eq(5), eq(42L))).thenReturn(menu);

        mockMvc.perform(post("/api/katalog/menu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(99))
                .andExpect(jsonPath("$.data.hargaJual").value(15000))
                .andExpect(jsonPath("$.data.aktif").value(true));

        verify(katalog).buatMenu(eq(7L), eq(null), eq("Nasi Goreng"), eq(15_000L),
                eq(SatuanMenu.PORSI), eq(null), eq(5), eq(42L));
    }

    @Test
    void buatMenuTanpaHargaDitolakValidasi() throws Exception {
        MenuRequest req = new MenuRequest();
        req.setNama("Tanpa Harga");
        // hargaJual null

        mockMvc.perform(post("/api/katalog/menu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void daftarMenuMeneruskanFilter() throws Exception {
        when(katalog.daftarMenu(eq(7L), eq(3L), eq(true))).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/katalog/menu")
                        .param("kategoriId", "3")
                        .param("hanyaAktif", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("daftarMenu menyertakan stokBerjalan dari stok_cache (hindari N+1)")
    void daftarMenuMenyertakanStokBerjalan() throws Exception {
        Menu menu = Menu.builder().id(99L).sekolahId(7L).nama("Nasi Goreng")
                .hargaJual(15_000L).satuan(SatuanMenu.PORSI).stokMinimum(5).isActive(true).build();
        when(katalog.daftarMenu(eq(7L), eq(null), eq(false))).thenReturn(java.util.List.of(menu));
        when(katalog.stokBerjalan(eq(7L), eq(java.util.List.of(99L))))
                .thenReturn(java.util.Map.of(99L, 12));

        mockMvc.perform(get("/api/katalog/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(99))
                .andExpect(jsonPath("$.data[0].stokMinimum").value(5))
                .andExpect(jsonPath("$.data[0].stokBerjalan").value(12));
    }

    @Test
    @DisplayName("menu tanpa baris stok → stokBerjalan 0")
    void daftarMenuStokNolBilaTidakAdaBaris() throws Exception {
        Menu menu = Menu.builder().id(99L).sekolahId(7L).nama("Kue")
                .hargaJual(2_000L).satuan(SatuanMenu.PCS).stokMinimum(0).isActive(true).build();
        when(katalog.daftarMenu(eq(7L), eq(null), eq(false))).thenReturn(java.util.List.of(menu));
        when(katalog.stokBerjalan(eq(7L), eq(java.util.List.of(99L))))
                .thenReturn(java.util.Map.of());

        mockMvc.perform(get("/api/katalog/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stokBerjalan").value(0));
    }

    @Test
    @DisplayName("lihatMenu menyertakan stokBerjalan")
    void lihatMenuMenyertakanStokBerjalan() throws Exception {
        Menu menu = Menu.builder().id(99L).sekolahId(7L).nama("Es Teh")
                .hargaJual(3_000L).satuan(SatuanMenu.BOTOL).stokMinimum(0).isActive(true).build();
        when(katalog.lihatMenu(eq(7L), eq(99L))).thenReturn(menu);
        when(katalog.stokBerjalan(eq(7L), eq(99L))).thenReturn(7);

        mockMvc.perform(get("/api/katalog/menu/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(99))
                .andExpect(jsonPath("$.data.stokBerjalan").value(7));
    }
}
