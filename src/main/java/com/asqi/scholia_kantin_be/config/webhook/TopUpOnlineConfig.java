package com.asqi.scholia_kantin_be.config.webhook;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registrasi bean konfigurasi handler webhook <b>top-up online</b>
 * ({@link TopUpOnlineProperties}).
 *
 * <p>Dibuat sebagai kelas tersendiri (bukan menambah ke daftar di
 * {@code WebSecurityConfig}) agar fitur ini <b>mandiri</b>: perubahan hanya
 * menyentuh paket webhook, tidak menyentuh berkas keamanan inti.
 */
@Configuration
@EnableConfigurationProperties(TopUpOnlineProperties.class)
public class TopUpOnlineConfig {
}
