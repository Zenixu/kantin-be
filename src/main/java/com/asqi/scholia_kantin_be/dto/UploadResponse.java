package com.asqi.scholia_kantin_be.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Hasil unggah file ke object storage (MinIO/S3) — PRD §7.1 (foto menu),
 * §7.2 (nota barang masuk).
 *
 * <p>FE cukup menyimpan {@link #url} (atau {@link #path} bila URL publik belum
 * dikonfigurasi) ke dalam {@code fotoUrl} pada {@code MenuRequest}.
 */
@Data
@Builder
public class UploadResponse {

    /** Nama objek di dalam bucket, selalu ber-prefix tenant ({@code sekolah-<id>/...}). */
    private String path;

    /**
     * URL siap pakai untuk FE: {@code minio.url-final} + {@code path} bila
     * dikonfigurasi, selain itu sama dengan {@link #path}.
     */
    private String url;

    /** Nama asli file dari klien (untuk ditampilkan/log). */
    private String namaAsli;

    /** Ukuran file dalam byte. */
    private long ukuran;
}
