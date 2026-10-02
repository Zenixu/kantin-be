package com.asqi.scholia_kantin_be.component.exception;

/** 401 — token JWT kedaluwarsa (parity admin-be). */
public class JWTExpiredException extends RuntimeException {
    public JWTExpiredException(String message) {
        super(message);
    }
}
