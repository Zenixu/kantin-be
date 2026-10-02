package com.asqi.scholia_kantin_be.config.security;

import com.asqi.scholia_kantin_be.enums.SumberToken;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Verifikator JWT RS256 untuk token dari admin-be &amp; mobile-be.
 *
 * <p><b>Hanya RS256</b> — tidak ada fallback HS256 (dilarang, PRD §11.10).
 * Bila public key belum dikonfigurasi untuk suatu issuer, verifikasi untuk
 * issuer itu <b>fail-closed</b> (ditolak), bukan diam-diam dilewati.
 *
 * <p><b>Perbaikan dari admin-be:</b> admin-be men-<i>fallback</i> ke HS256
 * ketika RSA gagal dimuat. Itu berbahaya — token mana pun yang tahu secret
 * shared bisa lolos. Di kantin-be tidak ada fallback.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KantinJwtDecoder {

    private final JwtProperties jwtProperties;

    private final Object kunciAdminLock = new Object();
    private final Object kunciMobileLock = new Object();

    private volatile PublicKey kunciAdmin;
    private volatile PublicKey kunciMobile;

    /**
     * Verifikasi token dan kembalikan klaimnya.
     *
     * @param token  token tanpa prefiks "Bearer "
     * @param sumber issuer yang diharapkan
     * @throws JwtException bila tidak valid / issuer tak siap
     */
    public Claims verifikasi(String token, SumberToken sumber) throws JwtException {
        PublicKey key = publicKey(sumber);
        if (key == null) {
            throw new JwtException("Public key untuk issuer " + sumber + " belum dikonfigurasi");
        }

        var parserBuilder = Jwts.parser().verifyWith(key);
        // Verifikasi issuer: token dari issuer lain (walau signature sah) DITOLAK.
        // Mencegah token mobile-be dipakai sebagai staf, atau sebaliknya.
        if (jwtProperties.isVerifyIssuer()) {
            parserBuilder.requireIssuer(issuerUntuk(sumber));
        }

        var parsed = parserBuilder.build().parseSignedClaims(token);

        // Cek algoritma di HEADER (bukan claims — 'alg' bukan klaim standar).
        // RS256 sudah ditegakkan verifyWith(key); ini sabuk pengaman eksplisit.
        String alg = parsed.getHeader().getAlgorithm();
        if (alg == null || !"RS256".equalsIgnoreCase(alg)) {
            throw new JwtException("Algoritma token tidak didukung: " + alg);
        }
        return parsed.getPayload();
    }

    private String issuerUntuk(SumberToken sumber) {
        return sumber == SumberToken.ADMIN
                ? jwtProperties.getIssuerAdmin()
                : jwtProperties.getIssuerMobile();
    }

    private PublicKey publicKey(SumberToken sumber) {
        return switch (sumber) {
            case ADMIN -> kunciAdmin != null ? kunciAdmin : muatAdmin();
            case MOBILE -> kunciMobile != null ? kunciMobile : muatMobile();
        };
    }

    private PublicKey muatAdmin() {
        synchronized (kunciAdminLock) {
            if (kunciAdmin == null && jwtProperties.adminSiap()) {
                kunciAdmin = bacaPublicKey(jwtProperties.getAdminPublicKey(), "admin-be");
            }
            return kunciAdmin;
        }
    }

    private PublicKey muatMobile() {
        synchronized (kunciMobileLock) {
            if (kunciMobile == null && jwtProperties.mobileSiap()) {
                kunciMobile = bacaPublicKey(jwtProperties.getMobilePublicKey(), "mobile-be");
            }
            return kunciMobile;
        }
    }

    private PublicKey bacaPublicKey(String base64, String label) {
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey key = kf.generatePublic(new X509EncodedKeySpec(der));
            log.info("JWT public key dimuat untuk {}", label);
            return key;
        } catch (Exception e) {
            log.error("Gagal membaca public key RSA {}: {}", label, e.getMessage());
            throw new IllegalStateException("Public key " + label + " tidak valid", e);
        }
    }
}
