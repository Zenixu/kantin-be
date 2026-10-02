package com.asqi.scholia_kantin_be.component.exception;

/**
 * 401 — refresh token dipakai sebagai access token, atau sebaliknya.
 *
 * <p>kantin-be tidak menerbitkan token (ADR-0002), jadi exception ini hanya
 * dipakai bila token dari SKOOLIA ber-{@code typ=refresh} dicoba dipakai ke
 * endpoint kantin. Lihat {@code JwtAuthTokenFilter}.
 */
public class TokenRefreshException extends RuntimeException {
    public TokenRefreshException(String message) {
        super(message);
    }
}
