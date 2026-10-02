package com.asqi.scholia_kantin_be.helper;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

/**
 * Object storage MinIO/S3 untuk nota barang masuk &amp; foto stok.
 * Parity dengan admin-be (helper/S3Storage). Lihat AGENTS.md §2.
 *
 * <p><b>Lazy init:</b> klien MinIO baru dibuat saat benar-benar dipakai.
 * {@code MinioClient.builder().build()} MELEMPAR exception bila kredensial
 * kosong, sehingga membuatnya di konstruktor akan menggagalkan start
 * aplikasi di DEV/CI yang belum menyiapkan MinIO.
 */
@Service
public class S3Storage {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg",
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
        ".mp4", ".mp3", ".zip"
    );

    private final String url;
    private final String accessKey;
    private final String secretKey;

    /** Klien dibuat malas (double-checked locking) pada pemakaian pertama. */
    private volatile MinioClient client;

    @Value("${minio.bucket-name}")
    private String bucketName;

    public S3Storage(@Value("${minio.url}") String url,
                     @Value("${minio.access-key}") String accessKey,
                     @Value("${minio.secret-key}") String secretKey) {
        this.url = url;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    /** @return klien MinIO siap pakai; melempar bila kredensial belum diisi. */
    private MinioClient client() {
        MinioClient local = this.client;
        if (local == null) {
            synchronized (this) {
                local = this.client;
                if (local == null) {
                    if (accessKey == null || accessKey.isBlank()
                            || secretKey == null || secretKey.isBlank()) {
                        throw new IllegalStateException(
                            "MinIO belum dikonfigurasi (minio.access-key / minio.secret-key kosong). "
                            + "Isi di application-local.properties sebelum memakai fitur upload.");
                    }
                    local = MinioClient.builder()
                            .endpoint(url)
                            .credentials(accessKey, secretKey)
                            .build();
                    this.client = local;
                }
            }
        }
        return local;
    }

    private String validateAndExtractExtension(String originalFileName) {
        if (originalFileName == null || !originalFileName.contains(".")) {
            throw new IllegalArgumentException("File harus memiliki ekstensi");
        }
        String ext = originalFileName.substring(originalFileName.lastIndexOf(".")).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Tipe file tidak diizinkan: " + ext);
        }
        return ext;
    }

    /**
     * Validasi nama objek agar tak bisa keluar dari namespace yang diizinkan
     * (cegah path-traversal / IDOR baca objek sembarang — B19, PRD §11.4).
     *
     * <p>Aturan: tidak kosong, tidak absolut, tanpa segmen {@code ..} atau
     * {@code .}, tidak memuat backslash/NUL, dan seluruh segmen hanya berisi
     * karakter aman ({@code [A-Za-z0-9._-]}). Karena penyimpanan sudah
     * menyimpan objek ber-prefix tenant (mis. {@code sekolah-7/...}), ketatnya
     * validasi ini memastikan pemanggil tak bisa meminta objek di luar prefix-nya
     * dengan menyisipkan {@code ../}.
     */
    private void validasiObjectName(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("Nama objek wajib diisi");
        }
        if (objectName.startsWith("/") || objectName.contains("\\") || objectName.contains("\0")) {
            throw new IllegalArgumentException("Nama objek tidak valid");
        }
        for (String segmen : objectName.split("/")) {
            if (segmen.isEmpty() || segmen.equals(".") || segmen.equals("..")) {
                throw new IllegalArgumentException("Nama objek tidak valid (segmen terlarang)");
            }
            if (!segmen.matches("[A-Za-z0-9._-]+")) {
                throw new IllegalArgumentException("Nama objek memuat karakter tidak diizinkan");
            }
        }
    }

    public String uploadFile(String folderName, MultipartFile file) throws Exception {
        try (InputStream inputStream = file.getInputStream()) {
            String originalFileName = file.getOriginalFilename();
            String extension = validateAndExtractExtension(originalFileName);
            String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
            String objectName = folderName + "/" + fileName;
            validasiObjectName(objectName);

            client().putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            return objectName;
        } catch (MinioException e) {
            throw new Exception("Error while uploading file to MinIO", e);
        }
    }

    public void deleteFile(String objectName) throws Exception {
        validasiObjectName(objectName);
        client().removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );
    }

    public InputStream viewFile(String objectName) throws Exception {
        // Guard path-traversal/IDOR: jangan pernah meneruskan objek tak tervalidasi (B19).
        validasiObjectName(objectName);
        try {
            GetObjectResponse response = client().getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            return response;
        } catch (Exception e) {
            throw new Exception("Gagal mengambil file dari MinIO: " + e.getMessage());
        }
    }
}
