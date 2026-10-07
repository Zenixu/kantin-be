package com.asqi.scholia_kantin_be.dev;

/**
 * Permintaan login shim dev (bentuknya meniru payload FE
 * {@code user-auth-form.tsx}: email, password, sekolah_id).
 *
 * <p>Shim ini <b>tidak memverifikasi password</b> — ia hanya alat uji lokal
 * agar FE mendapat token RS256 nyata. Jangan dipakai sebagai autentikasi nyata.
 */
public record DevLoginRequest(String email, String username, String password, Long sekolah_id) {
}
