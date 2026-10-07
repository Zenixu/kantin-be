package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.BarangMasukPembalikRequest;
import com.asqi.scholia_kantin_be.dto.BarangMasukRequest;
import com.asqi.scholia_kantin_be.dto.HalamanResponse;
import com.asqi.scholia_kantin_be.dto.OpnameBatchHasilItem;
import com.asqi.scholia_kantin_be.dto.OpnameBatchItemRequest;
import com.asqi.scholia_kantin_be.dto.OpnameBatchRequest;
import com.asqi.scholia_kantin_be.dto.OpnameBatchResponse;
import com.asqi.scholia_kantin_be.dto.RiwayatStokItem;
import com.asqi.scholia_kantin_be.enums.ArahStok;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
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

import java.util.List;

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

    @Test
    void barangMasukPembalikMeneruskanTenantDanAktor() throws Exception {
        BarangMasukPembalikRequest req = new BarangMasukPembalikRequest();
        req.setMutasiId(500L);
        req.setQty(5);
        req.setAlasan("SALAH INPUT");
        req.setReferensiId("PB-2026-0001");

        when(operasi.pembalikBarangMasuk(eq(7L), eq(500L), eq(5), eq("SALAH INPUT"),
                eq("PB-2026-0001"), eq(42L)))
                .thenReturn(HasilMutasiStok.baru(null, 5, 5_000L));

        mockMvc.perform(post("/api/stok/barang-masuk-pembalik")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void barangMasukPembalikTanpaAlasanDitolakValidasi() throws Exception {
        BarangMasukPembalikRequest req = new BarangMasukPembalikRequest();
        req.setMutasiId(500L);
        req.setAlasan("  "); // invalid
        req.setReferensiId("PB-1");

        mockMvc.perform(post("/api/stok/barang-masuk-pembalik")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void riwayatStokMengembalikanHalaman() throws Exception {
        RiwayatStokItem item = RiwayatStokItem.builder()
                .id(500L)
                .menuId(10L)
                .menuNama("Nasi Goreng")
                .arah(ArahStok.MASUK)
                .jenis(JenisMutasiStok.BARANG_MASUK)
                .qty(20)
                .hargaBeliSatuan(5_000L)
                .totalNilai(100_000L)
                .stokSetelah(20)
                .aktorId(42L)
                .aktorNama("Bu Sri")
                .dapatDibalik(true)
                .sisaDapatDibalik(20)
                .sudahDibalik(0)
                .build();
        HalamanResponse<RiwayatStokItem> halaman = HalamanResponse.<RiwayatStokItem>builder()
                .items(List.of(item))
                .total(1)
                .halaman(0)
                .ukuran(20)
                .totalHalaman(1)
                .build();

        when(operasi.riwayat(eq(7L), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(halaman);

        mockMvc.perform(get("/api/stok/riwayat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(500))
                .andExpect(jsonPath("$.data.items[0].aktorNama").value("Bu Sri"))
                .andExpect(jsonPath("$.data.items[0].dapatDibalik").value(true))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void opnameBatchMeneruskanTenantDanAktor() throws Exception {
        OpnameBatchItemRequest item = new OpnameBatchItemRequest();
        item.setMenuId(10L);
        item.setQtyFisik(15);
        item.setAlasan("Basi/Rusak");
        item.setRusak(true);

        OpnameBatchRequest req = new OpnameBatchRequest();
        req.setReferensiId("OPN-20261006-001");
        req.setItems(List.of(item));

        OpnameBatchResponse hasil = OpnameBatchResponse.builder()
                .referensiId("OPN-20261006-001")
                .jumlahBerubah(1)
                .jumlahTanpaSelisih(0)
                .items(List.of(OpnameBatchHasilItem.builder()
                        .menuId(10L).stokSebelum(20).stokFisik(15).selisih(-5)
                        .jenis(JenisMutasiStok.BARANG_RUSAK).mutasiId(900L).stokSetelah(15)
                        .build()))
                .build();

        when(operasi.opnameBatch(eq(7L), eq("OPN-20261006-001"), any(), eq(42L)))
                .thenReturn(hasil);

        mockMvc.perform(post("/api/stok/opname-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referensiId").value("OPN-20261006-001"))
                .andExpect(jsonPath("$.data.items[0].jenis").value("BARANG_RUSAK"))
                .andExpect(jsonPath("$.data.items[0].selisih").value(-5));
    }

    @Test
    void opnameBatchTanpaItemsDitolakValidasi() throws Exception {
        OpnameBatchRequest req = new OpnameBatchRequest();
        req.setReferensiId("OPN-1");
        req.setItems(List.of()); // invalid: minimal satu item

        mockMvc.perform(post("/api/stok/opname-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
