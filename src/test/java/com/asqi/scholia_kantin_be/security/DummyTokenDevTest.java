package com.asqi.scholia_kantin_be.security;

import com.asqi.scholia_kantin_be.config.security.JwtProperties;
import com.asqi.scholia_kantin_be.config.security.KantinJwtDecoder;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
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
 * HARNESS DUMMY (#14 staf &amp; #15 ortu) — token DUMMY yang ditandatangani
 * keypair dummy <b>lolos verifikasi RS256 asli</b>.
 *
 * <p>Selama admin-be/mobile-be belum menyediakan public key produksi
 * (OPEN-QUESTIONS Q1/Q2), pengembangan memakai keypair dummy
 * ({@code scripts/dev/gen-jwt-dummy.sh}) &amp; token dummy
 * ({@code scripts/dev/mint-jwt-dummy.sh}). Uji ini membuktikan dummy itu
 * <b>kompatibel dengan jalur verifikasi produksi</b> — bukan bypass auth.
 *
 * <p>Dua jalur yang dikunci:
 * <ul>
 *   <li><b>#14 staf (ADMIN)</b> — {@code role} → {@link AktorKantin} yang tepat.</li>
 *   <li><b>#15 ortu (MOBILE)</b> — token ortu selalu dipetakan ke
 *       {@link AktorKantin#ORANG_TUA} dan membawa {@code siswa_id}.</li>
 * </ul>
 */
@DisplayName("HARNESS DUMMY — token dummy staf & ortu lolos verifikasi asli")
class DummyTokenDevTest {

    private static final String ISSUER_ADMIN = "skoolia-admin";
    private static final String ISSUER_MOBILE = "skoolia-mobile";

    private KeyPair rsa() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    private KantinJwtDecoder decoder(PublicKey pubAdmin, PublicKey pubMobile) {
        JwtProperties props = new JwtProperties();
        props.setAdminPublicKey(Base64.getEncoder().encodeToString(pubAdmin.getEncoded()));
        props.setMobilePublicKey(Base64.getEncoder().encodeToString(pubMobile.getEncoded()));
        props.setIssuerAdmin(ISSUER_ADMIN);
        props.setIssuerMobile(ISSUER_MOBILE);
        props.setVerifyIssuer(true);
        return new KantinJwtDecoder(props);
    }

    /** Token staf meniru admin-be (tanpa `iss`) — sama seperti mint-jwt-dummy.sh staf. */
    private String tokenStaf(PrivateKey priv) {
        return Jwts.builder()
                .subject("petugas01").claim("typ", "access")
                .claim("user_id", 42L).claim("nama", "Petugas Dummy")
                .claim("role", "PETUGAS_KANTIN").claim("sekolah_id", 1L)
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(priv, Jwts.SIG.RS256).compact();
    }

    /** Token ortu meniru mobile-be — sama seperti mint-jwt-dummy.sh ortu. */
    private String tokenOrtu(PrivateKey priv) {
        return Jwts.builder()
                .subject("ortu01").claim("typ", "access")
                .claim("user_id", 100L).claim("nama", "Orang Tua Dummy")
                .claim("role", "ORANG_TUA").claim("sekolah_id", 1L)
                .claim("siswa_id", 7L)
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(priv, Jwts.SIG.RS256).compact();
    }

    @Test
    @DisplayName("#14 staf: token dummy staf lolos & dipetakan ke PETUGAS_KANTIN")
    void tokenDummyStafLolos() throws Exception {
        KeyPair admin = rsa(), mobile = rsa();
        KantinJwtDecoder dec = decoder(admin.getPublic(), mobile.getPublic());

        Claims claims = dec.verifikasi(tokenStaf(admin.getPrivate()), SumberToken.ADMIN);
        IdentitasKantin id = new KlaimResolver().bangun(claims, SumberToken.ADMIN);

        assertThat(id.getPeran()).isEqualTo(AktorKantin.PETUGAS_KANTIN);
        assertThat(id.getSekolahId()).isEqualTo(1L);
        assertThat(id.aktorIdWajib()).isEqualTo(42L);
    }

    @Test
    @DisplayName("#15 ortu: token dummy ortu lolos & dipetakan ke ORANG_TUA + siswa_id")
    void tokenDummyOrtuLolos() throws Exception {
        KeyPair admin = rsa(), mobile = rsa();
        KantinJwtDecoder dec = decoder(admin.getPublic(), mobile.getPublic());

        Claims claims = dec.verifikasi(tokenOrtu(mobile.getPrivate()), SumberToken.MOBILE);
        IdentitasKantin id = new KlaimResolver().bangun(claims, SumberToken.MOBILE);

        assertThat(id.getPeran()).isEqualTo(AktorKantin.ORANG_TUA);
        assertThat(id.getSiswaId()).isEqualTo(7L);
        assertThat(id.orangTua()).isTrue();
        assertThat(id.getSekolahId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Pemisahan issuer: token ortu TIDAK lolos verifikasi sebagai ADMIN (kunci beda)")
    void tokenOrtuTidakLolosSebagaiAdmin() throws Exception {
        KeyPair admin = rsa(), mobile = rsa();
        KantinJwtDecoder dec = decoder(admin.getPublic(), mobile.getPublic());

        // Signature kunci mobile-be → verifikasi dengan kunci admin-be GAGAL.
        assertThatThrownBy(() -> dec.verifikasi(tokenOrtu(mobile.getPrivate()), SumberToken.ADMIN))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    @DisplayName("Public key salah → token dummy DITOLAK (bukan diam-diam lolos)")
    void publicKeySalahDitolak() throws Exception {
        KeyPair admin = rsa(), mobile = rsa();
        KeyPair asing = rsa();
        KantinJwtDecoder dec = decoder(asing.getPublic(), mobile.getPublic());

        assertThatThrownBy(() -> dec.verifikasi(tokenStaf(admin.getPrivate()), SumberToken.ADMIN))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    @DisplayName("Public key BELUM dikonfigurasi → fail-closed (bukan lolos)")
    void publicKeyKosongFailClosed() throws Exception {
        KeyPair admin = rsa(), mobile = rsa();
        JwtProperties props = new JwtProperties();
        props.setMobilePublicKey(Base64.getEncoder().encodeToString(mobile.getPublic().getEncoded()));
        // adminPublicKey sengaja dibiarkan kosong
        KantinJwtDecoder dec = new KantinJwtDecoder(props);

        assertThatThrownBy(() -> dec.verifikasi(tokenStaf(admin.getPrivate()), SumberToken.ADMIN))
                .isInstanceOf(io.jsonwebtoken.JwtException.class)
                .hasMessageContaining("belum dikonfigurasi");
    }
}
