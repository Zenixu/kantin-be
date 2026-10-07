package com.asqi.scholia_kantin_be.config.notifikasi;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registrasi bean konfigurasi notifikasi ({@link NotifikasiProperties}).
 *
 * <p>Dibuat sebagai kelas tersendiri (bukan menambah ke daftar di
 * {@code WebSecurityConfig}) agar fitur ini <b>mandiri</b>: perubahan hanya
 * menyentuh paket notifikasi, tidak menyentuh berkas keamanan inti.
 */
@Configuration
@EnableConfigurationProperties(NotifikasiProperties.class)
public class NotifikasiConfig {
}
