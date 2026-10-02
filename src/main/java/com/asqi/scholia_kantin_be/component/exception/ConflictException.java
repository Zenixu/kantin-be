package com.asqi.scholia_kantin_be.component.exception;

/**
 * 409 — konflik state/aturan bisnis.
 *
 * <p>Contoh kantin: saldo kurang saat tap, stok tidak cukup, sesi kasir sudah
 * ditutup, idempotency key sudah pernah dipakai dengan payload berbeda.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
