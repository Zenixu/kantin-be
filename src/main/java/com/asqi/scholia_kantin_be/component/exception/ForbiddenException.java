package com.asqi.scholia_kantin_be.component.exception;

/**
 * 403 — terautentikasi tetapi tidak punya hak (RBAC).
 *
 * <p>⚠️ <b>BUKAN</b> untuk data sekolah lain — itu wajib 404
 * ({@link NotFoundEntity}). 403 khusus kegagalan RBAC di sekolah yang sama.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException() {
        super("Access is forbidden");
    }
}
