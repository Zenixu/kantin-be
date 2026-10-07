package com.asqi.scholia_kantin_be.dev;

import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Penerbit token RS256 <b>khusus lokal</b> — pengganti sementara admin-be.
 *
 * <p>Menghasilkan token dengan klaim yang persis dibaca {@code KlaimResolver}
 * kantin-be ({@code user_id}, {@code nama}, {@code role}, {@code sekolah_id})
 * plus {@code iss} yang cocok dengan {@code jwt.issuer-admin}. Token
 * ditandatangani memakai private key dev yang pasangannya
 * ({@code jwt.admin-public-key}) dipakai decoder untuk verifikasi.
 *
 * <p>Gerbang keamanan: {@code @Profile("local")} + {@code enabled=true}.
 */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "kantin.dev-login", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DevTokenService {

    private final DevLoginProperties props;

    private PrivateKey privateKey;

    @PostConstruct
    void init() {
        if (props.getPrivateKey() == null || props.getPrivateKey().isBlank()) {
            throw new IllegalStateException(
                    "kantin.dev-login.enabled=true tetapi kantin.dev-login.private-key kosong");
        }
        try {
            byte[] der = Base64.getDecoder().decode(props.getPrivateKey());
            KeyFactory kf = KeyFactory.getInstance("RSA");
            this.privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(der));
            log.warn("⚠️ SHIM LOGIN DEV AKTIF (profil local) — token RS256 diterbitkan lokal. "
                    + "JANGAN aktifkan di staging/production.");
        } catch (Exception e) {
            throw new IllegalStateException("kantin.dev-login.private-key tidak valid", e);
        }
    }

    /**
     * Terbitkan access token RS256.
     *
     * @param username  subjek token (email/username staf)
     * @param userId    klaim {@code user_id}
     * @param nama      klaim {@code nama}
     * @param role      klaim {@code role} (mis. ADMIN, PETUGAS, TATA_USAHA)
     * @param sekolahId klaim {@code sekolah_id} (tenant scoping)
     */
    public String terbitkan(String username, long userId, String nama, String role, long sekolahId) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .issuer(props.getIssuer())
                .claim("typ", "access")
                // Kirim sebagai String (seperti token admin-be nyata) agar tidak
                // terbaca sebagai Double ("1" -> "1.0") yang memecah konversi Long.
                .claim("user_id", String.valueOf(userId))
                .claim("nama", nama)
                .claim("role", role)
                .claim("sekolah_id", String.valueOf(sekolahId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + props.getTtlMs()))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }
}
