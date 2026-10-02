package com.asqi.scholia_kantin_be.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Konfigurasi Jackson untuk Spring Boot 4 (Jackson 3, paket {@code tools.jackson}).
 *
 * <p>CATATAN MIGRASI dari admin-be (Spring Boot 3 / Jackson 2):
 * <ul>
 *   <li>Feature {@code WRITE_DATES_AS_TIMESTAMPS} dan
 *       {@code FAIL_ON_UNKNOWN_PROPERTIES} sudah DIHAPUS di Jackson 3.</li>
 *   <li>Kelas {@code com.fasterxml.jackson.databind.ObjectMapper} bukan lagi
 *       mapper utama - Spring Boot 4 memakai {@code tools.jackson.databind.json.JsonMapper}.</li>
 *   <li>Serialisasi tanggal default = ISO-8601.</li>
 * </ul>
 *
 * <p>Kami memakai {@link JsonMapperBuilderCustomizer} agar dapat menyesuaikan
 * mapper bawaan Spring Boot TANPA menimpanya (menimpa JsonMapper bisa mematikan
 * auto-konfigurasi lain).
 */
@Configuration
public class JacksonConfig {

    /**
     * Kustomisasi mapper JSON aplikasi. Tambahkan setting di sini bila perlu
     * (mis. format tanggal sekolah, serialisasi enum, dsb.).
     */
    @Bean
    public JsonMapperBuilderCustomizer kantinJsonMapperCustomizer() {
        return builder -> {
            // Serialisasi tanggal default ISO-8601 (bawaan Jackson 3).
            // Contoh penyesuaian di masa depan:
            // builder.enable(SerializationFeature.INDENT_OUTPUT);
        };
    }
}
