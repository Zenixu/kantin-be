package com.asqi.scholia_kantin_be.component.exception;

/** 401 — belum terautentikasi / token tidak valid. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
