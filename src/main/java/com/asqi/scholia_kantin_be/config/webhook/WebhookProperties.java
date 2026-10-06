package com.asqi.scholia_kantin_be.config.webhook;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Konfigurasi keamanan webhook masuk (SECURITY.md §5, BUGS-DITEMUKAN B34).
 *
 * <p><b>Latar (audit keamanan):</b> {@code WebSecurityConfig} membuka
 * {@code /api/webhook/**} dengan {@code permitAll} agar SKOOLIA bisa memanggil
 * kantin-be tanpa token user. Tanpa verifikasi tambahan, endpoint itu berarti
 * <b>tanpa autentikasi sama sekali</b> — siapa pun bisa memalsukan event
 * (mis. memalsukan pembayaran/saldo). Karena itu setiap request webhook
 * <b>wajib</b> membawa HMAC sah; kelas ini menyediakan setelannya.
 *
 * <p><b>Rahasia dari environment, bukan hardcode</b> (AGENTS.md §10,
 * CONVENTIONS.md §7): isi lewat {@code KANTIN_WEBHOOK_SECRET}. Bila rahasia
 * <b>kosong</b>, verifikasi <b>fail-closed</b> (503) — endpoint tidak pernah
 * terbuka hanya karena lupa konfigurasi.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "kantin.webhook")
public class WebhookProperties {

    /**
     * Rahasia bersama (shared secret) untuk HMAC-SHA256 — dari environment
     * {@code KANTIN_WEBHOOK_SECRET}. <b>Kosong = webhook ditolak (503)</b>.
     */
    private String secret = "";

    /**
     * Toleransi jam untuk anti-replay (detik). Request dengan
     * {@code X-Webhook-Timestamp} di luar jendela {@code ±toleransi} ditolak
     * (401) walau signature-nya sah. Default 300 detik (5 menit).
     */
    private long toleranceSeconds = 300;

    /**
     * Batas ukuran badan request (byte). Lewat batas ⇒ <b>413</b>. Melindungi
     * dari request raksasa yang di-HMAC untuk memaksa CPU/memori. Default 1 MiB.
     */
    private int maxBodyBytes = 1_048_576;

    /**
     * Daftar IP/rentang (CIDR) pengirim yang diizinkan, mis.
     * {@code 10.0.0.0/8,203.0.113.7}. <b>Kosong = tidak membatasi</b> (signature
     * tetap wajib) — isi di produksi bila alamat callback-be diketahui tetap.
     */
    private List<String> allowedIps = List.of();

    /**
     * Proxy/CDN tepercaya yang boleh mengisi {@code X-Forwarded-For}. Hanya bila
     * {@code remoteAddr} ada di daftar ini header XFF dipakai untuk menentukan IP
     * klien; selain itu memakai {@code remoteAddr} apa adanya (anti-spoof, pola
     * sama dengan rate limit B33). Kosong (default) = jangan percaya XFF.
     */
    private List<String> trustedProxies = List.of();

    /** Nama header berisi epoch detik penandatanganan. */
    private String headerTimestamp = "X-Webhook-Timestamp";

    /** Nama header berisi HMAC-SHA256 (hex, boleh ber-prefix {@code sha256=}). */
    private String headerSignature = "X-Webhook-Signature";

    /** Nama header berisi id event unik dari pengirim (kunci idempotency). */
    private String headerEventId = "X-Webhook-Id";
}
