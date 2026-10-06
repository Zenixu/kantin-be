package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.TapRequest;
import com.asqi.scholia_kantin_be.dto.TapResponse;
import com.asqi.scholia_kantin_be.dto.VoidRequest;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.integrasi.BukuKasPostingService;
import com.asqi.scholia_kantin_be.service.kasir.SesiKasirService;
import com.asqi.scholia_kantin_be.service.kasir.TapService;
import com.asqi.scholia_kantin_be.service.kasir.VoidService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uji wiring {@link KasirController} (temuan: endpoint tap sebelumnya stub).
 *
 * <p>Memakai MockMvc standalone + service tiruan (tanpa Spring context / DB).
 * Karena standalone tidak punya {@code SecurityContextArgumentResolver}, kita
 * daftarkan resolver sederhana yang mengembalikan identitas dari konten
 * {@link TenantContext} — meniru perilaku produksi.
 */
@DisplayName("KasirController — wiring tap/void")
class KasirControllerTest {

    private TapService tapService;
    private VoidService voidService;
    private SesiKasirService sesiKasirService;
    private BukuKasPostingService bukuKasPosting;
    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Resolver tiruan untuk @AuthenticationPrincipal: ambil dari TenantContext. */
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
        tapService = mock(TapService.class);
        voidService = mock(VoidService.class);
        sesiKasirService = mock(SesiKasirService.class);
        bukuKasPosting = mock(BukuKasPostingService.class);

        KasirController controller = new KasirController(tapService, voidService, sesiKasirService, bukuKasPosting);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PrincipalResolver())
                .build();

        // TenantContext diisi seperti yang dilakukan JwtAuthTokenFilter.
        TenantContext.set(IdentitasKantin.builder()
                .userId("42")
                .sekolahId(7L)
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void tapMemanggilServiceDenganTenantDariKonteks() throws Exception {
        TapResponse hasil = TapResponse.builder()
                .transaksiId(1001L)
                .nama("Budi")
                .total(15000L)
                .saldoSisa(35000L)
                .namaItem(List.of("Nasi Goreng"))
                .build();
        when(tapService.tap(eq(7L), any(IdentitasKantin.class), any(TapRequest.class)))
                .thenReturn(hasil);

        TapRequest req = new TapRequest();
        req.setRfidUid("A1B2C3D4");
        req.setTitikKasirId(1L);
        req.setIdempotencyKey("tap-001");
        TapRequest.ItemTap item = new TapRequest.ItemTap();
        item.setMenuId(10L);
        item.setQty(2);
        req.setItems(List.of(item));

        mockMvc.perform(post("/api/kasir/tap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transaksiId").value(1001))
                .andExpect(jsonPath("$.data.saldoSisa").value(35000));

        // Tenant WAJIB dari TenantContext (7), bukan dari body.
        verify(tapService).tap(eq(7L), any(IdentitasKantin.class), any(TapRequest.class));
    }

    @Test
    void voidMeneruskanAktorIdDariToken() throws Exception {
        VoidRequest req = new VoidRequest();
        req.setAlasan("salah input");

        mockMvc.perform(post("/api/kasir/transaksi/55/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // aktorId = 42 (dari token), sekolahId = 7 (tenant).
        verify(voidService).voidTransaksi(eq(7L), eq(55L), eq(42L), eq("salah input"));
    }

    @Test
    void voidTanpaAlasanDitolakValidasi() throws Exception {
        VoidRequest req = new VoidRequest(); // alasan null

        mockMvc.perform(post("/api/kasir/transaksi/55/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
