package com.asqi.scholia_kantin_be.security;

import com.asqi.scholia_kantin_be.config.security.JwtProperties;
import com.asqi.scholia_kantin_be.config.security.KantinJwtDecoder;
import com.asqi.scholia_kantin_be.enums.SumberToken;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AUDIT INTEGRASI (Q1) — kompatibilitas token <b>staf</b> admin-be dengan
 * verifikator kantin-be.
 *
 * <p>Dua bug integrasi yang direproduksi &amp; diperbaiki di sini:
 *
 * <ol>
 *   <li><b>Klaim {@code iss} absen.</b> {@code admin-be/JwtUtils.buildToken()}
 *       menerbitkan {@code sub/typ/user_id/nama/role/sekolah_id} tetapi TIDAK
 *       menyetel {@code iss}. Kantin-be sebelumnya memakai
 *       {@code requireIssuer("skoolia-admin")} → token staf yang SAH ditolak
 *       ({@code MissingClaimException}). Perbaikan: {@code iss} yang SALAH tetap
 *       ditolak, tetapi {@code iss} yang ABSEN diterima (pemisahan issuer sejati
 *       ditegakkan public key berbeda per sumber).</li>
 *   <li><b>Angka jadi {@code Double}.</b> jjwt-gson membaca klaim numerik sebagai
 *       {@link Double}: {@code user_id=42} → {@code "42.0"} → {@code
 *       IdentitasKantin.aktorIdWajib()} GAGAL, mematahkan semua endpoint tulis
 *       dengan token staf yang sah. Perbaikan: normalisasi angka integral di
 *       {@link KlaimResolver}.</li>
 * </ol>
 */
@DisplayName("AUDIT Q1 — token staf admin-be vs verifikator kantin-be")
class KompatibilitasTokenStafAdminTest {

    private static final String ISSUER_ADMIN = "skoolia-admin";

    /** Bangun token staf meniru admin-be: klaim lengkap, {@code iss} opsional. */
    private String tokenStafAdmin(PrivateKey priv, boolean pakaiIssuer) {
        var builder = Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .subject("petugas01")
                .claim("typ", "access")
                .claim("user_id", 42L)
                .claim("nama", "Budi Petugas")
                .claim("role", "PETUGAS_KANTIN")
                .claim("sekolah_id", 10L)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L));
        if (pakaiIssuer) {
            builder.issuer(ISSUER_ADMIN);
        }
        return builder.signWith(priv, Jwts.SIG.RS256).compact();
    }

    private KantinJwtDecoder decoder(PublicKey pub, boolean verifyIssuer) {
        JwtProperties props = new JwtProperties();
        props.setAdminPublicKey(Base64.getEncoder().encodeToString(pub.getEncoded()));
        props.setIssuerAdmin(ISSUER_ADMIN);
        props.setVerifyIssuer(verifyIssuer);
        return new KantinJwtDecoder(props);
    }

    private KeyPair rsa() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    @Test
    @DisplayName("FIX (1): token admin-be tanpa `iss` DITERIMA (sebelumnya ditolak MissingClaimException)")
    void tokenTanpaIssuerDiterimaSetelahPerbaikan() throws Exception {
        KeyPair kp = rsa();
        String token = tokenStafAdmin(kp.getPrivate(), false);

        Claims claims = decoder(kp.getPublic(), true).verifikasi(token, SumberToken.ADMIN);
        assertThat(claims.getSubject()).isEqualTo("petugas01");
    }

    @Test
    @DisplayName("FIX (2): user_id numerik terbaca \"42\" (bukan \"42.0\") → aktorIdWajib() berhasil")
    void userIdNumerikTerbacaSebagaiLong() throws Exception {
        KeyPair kp = rsa();
        String token = tokenStafAdmin(kp.getPrivate(), false);

        Claims claims = decoder(kp.getPublic(), true).verifikasi(token, SumberToken.ADMIN);
        IdentitasKantin id = new KlaimResolver().bangun(claims, SumberToken.ADMIN);

        assertThat(id.getUserId()).isEqualTo("42");
        assertThat(id.aktorIdWajib()).isEqualTo(42L);   // sebelumnya NumberFormatException
        assertThat(id.getSekolahId()).isEqualTo(10L);
        assertThat(id.getNama()).isEqualTo("Budi Petugas");
        assertThat(id.getRoleMentah()).isEqualTo("PETUGAS_KANTIN");
    }

    @Test
    @DisplayName("KONTROL: token admin-be yang menyetel `iss`=skoolia-admin diterima")
    void tokenDenganIssuerBenarDiterima() throws Exception {
        KeyPair kp = rsa();
        String token = tokenStafAdmin(kp.getPrivate(), true);

        Claims claims = decoder(kp.getPublic(), true).verifikasi(token, SumberToken.ADMIN);
        assertThat(claims.getSubject()).isEqualTo("petugas01");
    }

    @Test
    @DisplayName("KONTROL KEAMANAN: token dengan `iss` SALAH tetap DITOLAK")
    void tokenIssuerSalahDitolak() throws Exception {
        KeyPair kp = rsa();
        String token = Jwts.builder()
                .subject("petugas01")
                .claim("role", "PETUGAS_KANTIN")
                .issuer("skoolia-mobile")   // issuer milik mobile-be
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(kp.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> decoder(kp.getPublic(), true).verifikasi(token, SumberToken.ADMIN))
                .isInstanceOf(io.jsonwebtoken.JwtException.class)
                .hasMessageContaining("Issuer token tidak sesuai");
    }

    @Test
    @DisplayName("KONTROL KEAMANAN: tanda tangan salah tetap DITOLAK")
    void tandaTanganSalahDitolak() throws Exception {
        KeyPair kp = rsa();
        KeyPair lain = rsa();
        String token = tokenStafAdmin(kp.getPrivate(), false);   // ditandatangani kunci lain

        assertThatThrownBy(() -> decoder(lain.getPublic(), true).verifikasi(token, SumberToken.ADMIN))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}
