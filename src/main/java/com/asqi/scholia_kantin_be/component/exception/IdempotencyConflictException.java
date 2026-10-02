package com.asqi.scholia_kantin_be.component.exception;

/**
 * 409 — idempotency key sudah dipakai dengan payload berbeda.
 *
 * <p>Bila key sama &amp; payload sama → kembalikan hasil yang lama (sukses),
 * <b>bukan</b> exception. Exception ini hanya untuk key sama + payload BEDA
 * (indikasi bug klien / penyalahgunaan). Lihat PRD §11.3.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
