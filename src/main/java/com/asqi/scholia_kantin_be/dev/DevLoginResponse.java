package com.asqi.scholia_kantin_be.dev;

/**
 * Data respons login shim dev — sengaja meniru bentuk yang diharapkan FE
 * ({@code LoginResponseData} di {@code user-auth-form.tsx}):
 * {@code { token, user: { id, nama, email, currentRole, roles, sekolah } }}.
 */
public record DevLoginResponse(String token, DevUser user) {

    public record DevUser(
            long id,
            String nama,
            String email,
            String currentRole,
            java.util.List<String> roles,
            Sekolah sekolah) {
    }

    public record Sekolah(long id, String nama) {
    }
}
