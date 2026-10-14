package com.asqi.scholia_kantin_be.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Uji regresi <b>nama properti</b> public key JWT.
 *
 * <p>Latar (bug #204): {@code JwtProperties} memakai field {@code adminPublicKey}
 * &amp; {@code mobilePublicKey}, sehingga nama properti yang benar adalah
 * <b>kebab</b> — {@code jwt.admin-public-key}. Sebelumnya
 * {@code application.properties} menulis bentuk <b>dotted</b>
 * {@code jwt.admin.public-key}, yang oleh Spring dianggap nested
 * ({@code admin} → {@code public} → {@code key}) dan <b>tidak mengikat apa pun</b>.
 * Akibatnya {@code adminPublicKey} selalu {@code null}, decoder gagal-nyala, dan
 * <b>setiap</b> request ber-token dibalas {@code 401 "Token tidak valid"}.
 *
 * <p>Uji lama tak menangkap ini karena {@code KompatibilitasTokenStafAdminTest}
 * membangun decoder manual ({@code setAdminPublicKey(...)}) sehingga melewati
 * binding properti. Uji ini menegakkan binding-nya secara langsung.
 */
class JwtPublicKeyBindingTest {

    private static Properties applicationProperties() throws Exception {
        Properties p = new Properties();
        try (InputStream in = JwtPublicKeyBindingTest.class.getResourceAsStream("/application.properties")) {
            assertThat(in).as("application.properties harus ada di classpath").isNotNull();
            p.load(in);
        }
        return p;
    }

    @Test
    void applicationPropertiesMemakaiNamaKebabDanTetapMembacaEnv() throws Exception {
        Properties p = applicationProperties();

        assertThat(p)
                .as("kunci public key admin WAJIB kebab 'jwt.admin-public-key' (dotted = konfigurasi mati)")
                .containsKey("jwt.admin-public-key");
        assertThat(p)
                .as("kunci public key mobile WAJIB kebab 'jwt.mobile-public-key' (dotted = konfigurasi mati)")
                .containsKey("jwt.mobile-public-key");

        assertThat(p.getProperty("jwt.admin-public-key"))
                .as("nilai tetap diisi dari environment variable JWT_ADMIN_PUBLIC_KEY")
                .contains("JWT_ADMIN_PUBLIC_KEY");
        assertThat(p.getProperty("jwt.mobile-public-key"))
                .as("nilai tetap diisi dari environment variable JWT_MOBILE_PUBLIC_KEY")
                .contains("JWT_MOBILE_PUBLIC_KEY");
    }

    @Test
    void namaKebabMengikatKeField() {
        JwtProperties props = bind(Map.of(
                "jwt.admin-public-key", "PUB-ADMIN",
                "jwt.mobile-public-key", "PUB-MOBILE"));

        assertThat(props.adminSiap()).as("jwt.admin-public-key harus mengikat ke adminPublicKey").isTrue();
        assertThat(props.mobileSiap()).as("jwt.mobile-public-key harus mengikat ke mobilePublicKey").isTrue();
    }

    @Test
    void namaBertitikTidakMengikat() {
        JwtProperties props = bind(Map.of("jwt.admin.public-key", "PUB-ADMIN"));

        assertThat(props.adminSiap())
                .as("REGRESI #204: 'jwt.admin.public-key' (dotted) dianggap nested dan TIDAK mengikat")
                .isFalse();
    }

    private static JwtProperties bind(Map<String, Object> nilai) {
        return new Binder(new MapConfigurationPropertySource(nilai))
                .bind("jwt", Bindable.of(JwtProperties.class))
                .orElseGet(JwtProperties::new);
    }
}
