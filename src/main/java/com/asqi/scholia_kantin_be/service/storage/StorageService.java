package com.asqi.scholia_kantin_be.service.storage;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.dto.UploadResponse;
import com.asqi.scholia_kantin_be.helper.S3Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;

/**
 * Unggah/ambil berkas ke object storage (MinIO/S3) untuk foto menu (PRD §7.1)
 * dan nota barang masuk (PRD §7.2).
 *
 * <p><b>Isolasi tenant (PRD §11.4, B19):</b> setiap objek <b>wajib</b> berada
 * di bawah prefix {@code sekolah-<id>/} milik pemanggil. Prefix dibentuk di
 * sini — bukan dari input klien — sehingga sekolah lain tak bisa menulis atau
 * membaca objek di luar namespace-nya (mencegah path-traversal/IDOR).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    /**
     * Folder yang boleh dipakai klien. Sengaja berupa allowlist agar klien tak
     * bisa membuat struktur folder sembarang di dalam namespace tenant.
     */
    private static final Set<String> FOLDER_DIIZINKAN = Set.of(
            "menu", "nota", "kartu-tamu", "lain");

    private static final String FOLDER_DEFAULT = "lain";

    private final S3Storage storage;

    /** Basis URL publik (mis. {@code https://cdn.sekolah.id/kantin/}); boleh kosong. */
    @Value("${minio.url-final:}")
    private String urlFinal;

    /**
     * Unggah satu berkas atas nama tenant pemanggil.
     *
     * @param sekolahId tenant dari JWT
     * @param folder    folder logis ({@code menu}/{@code nota}/...); default {@code lain}
     * @param file      berkas multipart (wajib, tidak boleh kosong)
     * @return path objek + URL siap pakai untuk disimpan FE (mis. ke {@code fotoUrl})
     */
    public UploadResponse unggah(Long sekolahId, String folder, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidOperationException("Berkas wajib diunggah dan tidak boleh kosong");
        }
        String folderBersih = normalisasiFolder(folder);
        // Prefix tenant dibentuk server-side (bukan dari input) — B19.
        String prefix = "sekolah-" + sekolahId + "/" + folderBersih;

        String path;
        try {
            path = storage.uploadFile(prefix, file);
        } catch (IllegalArgumentException e) {
            // Validasi ekstensi/nama objek dari S3Storage → kesalahan input klien.
            throw new InvalidOperationException(e.getMessage());
        } catch (Exception e) {
            // Detail (mis. MinIO belum dikonfigurasi / jaringan) hanya ke log server.
            log.error("Gagal mengunggah berkas ke object storage (sekolah={}, folder={})",
                    sekolahId, folderBersih, e);
            throw new InvalidOperationException(
                    "Gagal mengunggah berkas ke penyimpanan. Periksa konfigurasi MinIO atau coba lagi.");
        }

        return UploadResponse.builder()
                .path(path)
                .url(susunUrl(path))
                .namaAsli(file.getOriginalFilename())
                .ukuran(file.getSize())
                .build();
    }

    /**
     * Buka berkas milik tenant pemanggil untuk ditampilkan/diunduh.
     * Path <b>wajib</b> ber-prefix {@code sekolah-<id>/} — selain itu dianggap
     * tidak ada (404 via {@code NotFoundEntity} di controller), sesuai aturan
     * emas data sekolah lain (PRD §11.4).
     */
    public InputStream buka(Long sekolahId, String path) {
        String bersih = pathWajibMilikTenant(sekolahId, path);
        try {
            return storage.viewFile(bersih);
        } catch (IllegalArgumentException e) {
            throw new InvalidOperationException(e.getMessage());
        } catch (Exception e) {
            log.error("Gagal membaca berkas dari object storage (sekolah={}, path={})",
                    sekolahId, bersih, e);
            throw new InvalidOperationException(
                    "Gagal membaca berkas dari penyimpanan. Periksa konfigurasi MinIO atau coba lagi.");
        }
    }

    // ────────────────────────────────────────────────────────────────
    // HELPER
    // ────────────────────────────────────────────────────────────────

    private String normalisasiFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return FOLDER_DEFAULT;
        }
        String bersih = folder.trim().toLowerCase();
        if (!FOLDER_DIIZINKAN.contains(bersih)) {
            throw new InvalidOperationException(
                    "Folder tidak dikenal: " + folder + " (pilihan: " + FOLDER_DIIZINKAN + ")");
        }
        return bersih;
    }

    /**
     * Pastikan {@code path} berada di dalam namespace tenant pemanggil.
     *
     * @return path apa adanya bila valid
     * @throws NotFoundEntity bila prefix tenant tidak cocok (sekolah lain ⇒ 404)
     */
    private String pathWajibMilikTenant(Long sekolahId, String path) {
        if (path == null || path.isBlank()) {
            throw new InvalidOperationException("Path berkas wajib diisi");
        }
        String prefix = "sekolah-" + sekolahId + "/";
        if (!path.startsWith(prefix)) {
            // Jangan bocorkan keberadaan objek sekolah lain — perlakukan sebagai tidak ditemukan.
            throw new NotFoundEntity("Berkas tidak ditemukan");
        }
        return path;
    }

    private String susunUrl(String path) {
        if (urlFinal == null || urlFinal.isBlank()) {
            return path;
        }
        return urlFinal.endsWith("/") ? urlFinal + path : urlFinal + "/" + path;
    }
}
