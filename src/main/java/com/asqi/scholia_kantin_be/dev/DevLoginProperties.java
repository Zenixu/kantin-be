package com.asqi.scholia_kantin_be.dev;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfigurasi <b>shim login dev</b> kantin-be (HANYA lokal).
 *
 * <p>kantin-be normalnya TIDAK punya login (ADR-0002) — token diterbitkan
 * admin-be. Karena admin-be belum dapat dijalankan di mesin dev (DB/Redis/klaim
 * JWT belum siap, lihat OPEN-QUESTIONS Q1), shim ini menerbitkan token RS256
 * <b>lokal</b> agar FE ↔ BE bisa diuji end-to-end tanpa admin-be.
 *
 * <p><b>AMAN untuk prod:</b> bean yang memakai properti ini digerbangi
 * {@code @Profile("local")} + {@code @ConditionalOnProperty(enabled=true)}.
 * Bila {@code kantin.dev-login.enabled} tidak diset {@code true}, shim tidak
 * pernah dimuat dan {@code /api/v1/auth/login} tetap tertutup (401).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "kantin.dev-login")
public class DevLoginProperties {

    /** Harus {@code true} agar shim aktif. Default {@code false} (mati). */
    private boolean enabled = false;

    /** Private key RSA base64 PKCS#8 DER — pasangan {@code jwt.admin-public-key}. */
    private String privateKey;

    /** Issuer token; HARUS sama dengan {@code jwt.issuer-admin} agar lolos verifikasi. */
    private String issuer = "skoolia-admin";

    /** Umur token (ms). Default 12 jam (parity admin-be). */
    private long ttlMs = 43_200_000L;
}
