package com.asqi.scholia_kantin_be.config.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfigurasi JWT (lihat {@code application.properties} §JWT).
 *
 * <p>Public key berformat <b>base64 X.509 DER</b> (sama seperti admin-be
 * {@code jwt.publicKey}, minus private key karena kantin-be tidak menerbitkan
 * token).
 *
 * <p>⚠️ Di <b>production</b>, {@code adminPublicKey}/{@code mobilePublicKey}
 * WAJIB terisi (RS256). Bila kosong, decoder gagal-nyala dengan pesan jelas —
 * lebih baik gagal boot daripada diam-diam memakai HS256 (dilarang, PRD §11.10).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String adminPublicKey;
    private String mobilePublicKey;
    private String issuerAdmin = "skoolia-admin";
    private String issuerMobile = "skoolia-mobile";

    /** Bila true, verifikasi issuer juga ditegakkan (disarankan di produksi). */
    private boolean verifyIssuer = false;

    public boolean adminSiap() {
        return adminPublicKey != null && !adminPublicKey.isBlank();
    }

    public boolean mobileSiap() {
        return mobilePublicKey != null && !mobilePublicKey.isBlank();
    }
}
