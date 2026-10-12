package com.asqi.scholia_kantin_be.config.integrasi;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registrasi properti adapter REST integrasi (issue <b>#160</b>).
 *
 * <p>Dibuat mandiri (bukan menambah ke {@code WebSecurityConfig}) agar fitur
 * integrasi <b>terisolasi</b> — menyentuh satu paket, tidak menyentuh berkas
 * keamanan inti.
 */
@Configuration
@EnableConfigurationProperties(IntegrasiRestProperties.class)
public class IntegrasiConfig {
}
