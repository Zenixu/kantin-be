package com.asqi.scholia_kantin_be.config.internal;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Konfigurasi endpoint <b>internal</b> kantin-be ({@code /api/internal/**},
 * issue <b>#29</b>).
 *
 * <p>Endpoint internal dipanggil <b>mesin-ke-mesin</b> oleh SKOOLIA (mis.
 * admin-be menanyakan apakah sebuah {@code rfid_uid} sudah dipakai Kartu Tamu,
 * anti-tabrakan UID). Ia <b>bukan</b> endpoint ber-token user, jadi diamankan
 * dengan <b>HMAC-SHA256 + anti-replay</b> memakai rahasia bersama terpisah dari
 * webhook masuk ({@code KANTIN_WEBHOOK_SECRET}).
 *
 * <p><b>Fail-closed:</b> bila {@link #secret} kosong, endpoint internal ditutup
 * (503) — tidak pernah terbuka hanya karena lupa konfigurasi (pola
 * {@code WebhookProperties}).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "kantin.internal")
public class InternalApiProperties {

    /**
     * Rahasia bersama (shared secret) HMAC-SHA256 untuk endpoint internal —
     * dari environment {@code KANTIN_INTERNAL_SECRET}. <b>Kosong = ditolak (503)</b>.
     */
    private String secret = "";

    /** Aktifkan endpoint internal. {@code false} ⇒ semua request internal ditolak (503). */
    private boolean enabled = true;

    /** Toleransi jam anti-replay (detik) untuk {@code X-Internal-Timestamp}. Default 300. */
    private long toleranceSeconds = 300;

    /** Batas ukuran badan request (byte). Lewat batas ⇒ 413. Default 1 MiB. */
    private int maxBodyBytes = 1_048_576;

    /**
     * Daftar IP/CIDR pengirim internal yang diizinkan (mis. IP admin-be). Kosong =
     * tidak membatasi (signature tetap wajib). Isi di produksi bila alamat tetap.
     */
    private List<String> allowedIps = List.of();

    /** Proxy tepercaya yang boleh mengisi {@code X-Forwarded-For} (anti-spoof). */
    private List<String> trustedProxies = List.of();

    /** Nama header berisi epoch detik penandatanganan. */
    private String headerTimestamp = "X-Internal-Timestamp";

    /** Nama header berisi HMAC-SHA256 (hex, boleh ber-prefix {@code sha256=}). */
    private String headerSignature = "X-Internal-Signature";
}
