package com.asqi.scholia_kantin_be.config.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nilai konfigurasi kecil seputar JWT (nama cookie) — parity
 * {@code jwt.cookieName} admin-be agar FE SKOOLIA bisa memakai cookie yang sama.
 *
 * <p>Properti: {@code jwt.cookie.name} (default {@code skoolia-cookies}).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jwt.cookie")
public class JwtConfigValues {

    /** Nama cookie berisi access token (HttpOnly) yang dipakai FE SKOOLIA. */
    private String cookieName = "skoolia-cookies";
}
