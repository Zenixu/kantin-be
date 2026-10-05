package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.UploadResponse;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uji wiring {@link StorageController}: tenant dari konteks, respons upload. */
@DisplayName("StorageController — wiring upload")
class StorageControllerTest {

    private StorageService storage;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        storage = mock(StorageService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new StorageController(storage)).build();
        TenantContext.set(IdentitasKantin.builder().userId("42").sekolahId(7L).build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("upload memakai tenant dari konteks & mengembalikan url/path")
    void uploadMemakaiTenantDariKonteks() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "foto.png", "image/png", "isi".getBytes());
        when(storage.unggah(eq(7L), eq("menu"), any()))
                .thenReturn(UploadResponse.builder()
                        .path("sekolah-7/menu/a.png")
                        .url("https://cdn.test/kantin/sekolah-7/menu/a.png")
                        .namaAsli("foto.png")
                        .ukuran(3)
                        .build());

        mockMvc.perform(multipart("/api/storage/upload").file(file).param("folder", "menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.path").value("sekolah-7/menu/a.png"))
                .andExpect(jsonPath("$.data.url").value("https://cdn.test/kantin/sekolah-7/menu/a.png"));

        verify(storage).unggah(eq(7L), eq("menu"), any());
    }
}
