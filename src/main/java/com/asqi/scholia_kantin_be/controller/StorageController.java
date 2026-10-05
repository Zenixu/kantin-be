package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.UploadResponse;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.PerluPeran;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unggah &amp; tampilkan berkas (foto menu, nota barang masuk) — PRD §7.1, §7.2.
 *
 * <p>Endpoint {@code POST /api/storage/upload} mengembalikan URL/path yang bisa
 * langsung disimpan FE ke {@code fotoUrl} pada {@code MenuRequest}.
 *
 * <p><b>Isolasi tenant (PRD §11.4, B19):</b> prefix {@code sekolah-<id>/} dibentuk
 * di {@link StorageService} dari tenant token — bukan dari input klien. Berkas
 * sekolah lain ⇒ 404, bukan 403.
 */
@RestController
@RequestMapping("api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storage;

    /**
     * Unggah berkas (multipart/form-data) sebagai foto menu / nota.
     *
     * @param file   berkas gambar/dokumen (field {@code file})
     * @param folder folder logis opsional: {@code menu}, {@code nota},
     *               {@code kartu-tamu}, {@code lain} (default {@code lain})
     */
    @PerluPeran({AktorKantin.PENGELOLA_KANTIN, AktorKantin.TU_SEKOLAH,
            AktorKantin.ADMIN_SEKOLAH})
    @PostMapping(value = "upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Response<UploadResponse>> unggah(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false) String folder) {
        UploadResponse hasil = storage.unggah(TenantContext.sekolahIdWajib(), folder, file);
        return CommonResponse.data(hasil, "Berkas berhasil diunggah");
    }

    /**
     * Tampilkan berkas milik tenant pemanggil (opsional; berguna bila URL publik
     * belum dikonfigurasi). Path di luar prefix tenant ⇒ 404.
     */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.PENGELOLA_KANTIN,
            AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("file")
    public ResponseEntity<InputStreamResource> lihat(@RequestParam("path") String path) throws Exception {
        InputStreamResource resource = new InputStreamResource(
                storage.buka(TenantContext.sekolahIdWajib(), path));
        return CommonResponse.fileResponse(resource, mediaType(path));
    }

    private MediaType mediaType(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (p.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (p.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (p.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
