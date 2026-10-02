package com.asqi.scholia_kantin_be.config.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfigurasi rate limit (SECURITY.md §7).
 *
 * <p>Semua nilai punya default aman; bisa di-override via environment
 * (lihat {@code application.properties}). Bila {@link #enabled} {@code false},
 * filter dilewati sepenuhnya (mis. saat dev tanpa Redis).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "kantin.rate-limit")
public class RateLimitProperties {

    /** Saklar utama. Default: aktif. */
    private boolean enabled = true;

    /** Jendela hitung (detik). Default 60. */
    private int windowSeconds = 60;

    /** Batas request per jendela untuk endpoint sensitif (mis. tap, top-up). */
    private int sensitiveLimit = 60;

    /** Batas request per jendela untuk endpoint baca umum. */
    private int defaultLimit = 600;

    /** Batas request per jendela untuk endpoint autentikasi (login/refresh). */
    private int authLimit = 20;

    /**
     * Bila {@code true}, kegagalan Redis (koneksi/timeout) mengembalikan
     * <b>429</b> (fail-closed). Default {@code false} — <b>fail-open</b>:
     * gangguan Redis tidak boleh melumpuhkan kantin (operasional > proteksi
     * saat infra rusak). Aktifkan hanya bila kebijakan keamanan menuntut.
     */
    private boolean failClosed = false;
}
