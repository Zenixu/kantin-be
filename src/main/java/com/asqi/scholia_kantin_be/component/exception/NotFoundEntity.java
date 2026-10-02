package com.asqi.scholia_kantin_be.component.exception;

/**
 * 404 — entitas tidak ada.
 *
 * <p>⚠️ <b>WAJIB</b> dipakai untuk data milik sekolah lain (PRD §11.4,
 * AGENTS.md §3.4): sekolah lain harus dijawab 404, <b>bukan</b> 403, agar
 * keberadaan data sekolah lain tidak bocor.
 */
public class NotFoundEntity extends RuntimeException {
    public NotFoundEntity(String message) {
        super(message);
    }
}
