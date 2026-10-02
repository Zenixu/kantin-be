package com.asqi.scholia_kantin_be.enums;

/**
 * Penerbit token JWT yang diterima kantin-be (ADR-0002).
 *
 * <p>kantin-be TIDAK menerbitkan token sendiri — hanya memverifikasi.
 */
public enum SumberToken {
    /** Token staf sekolah dari admin-be (petugas, pengelola, TU, bendahara, admin, kepsek). */
    ADMIN,
    /** Token orang tua dari mobile-be. */
    MOBILE
}
