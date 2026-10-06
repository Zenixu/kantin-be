package com.asqi.scholia_kantin_be.helper;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * Penentuan alamat IP klien dari sebuah request HTTP.
 *
 * <p><b>Anti-spoof (audit keamanan B33/B34):</b> {@code X-Forwarded-For} hanya
 * dipercaya bila koneksi datang dari proxy tepercaya yang dikonfigurasi. Tanpa
 * itu, header XFF dikendalikan klien dan bisa diputar untuk mengelabui rate limit
 * maupun allowlist IP webhook — jadi kita pakai {@code remoteAddr} apa adanya.
 *
 * <p>Dipakai bersama oleh {@code RateLimitFilter} dan
 * {@code WebhookSignatureFilter} agar aturan tidak berbeda antar lapisan.
 */
public final class AlamatKlien {

    private AlamatKlien() {
    }

    /**
     * Alamat IP klien untuk request ini.
     *
     * @param request        request yang sedang diproses
     * @param trustedProxies daftar IP proxy tepercaya (kosong = jangan percaya XFF)
     */
    public static String ip(HttpServletRequest request, List<String> trustedProxies) {
        String remote = request.getRemoteAddr();
        if (remote != null && !remote.isBlank()
                && trustedProxies != null && trustedProxies.contains(remote)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                // Entri paling kanan = hop pertama yang ditambahkan proxy tepercaya
                // (paling sulit dipalsukan klien, karena proxy menambah di kanan).
                int koma = xff.lastIndexOf(',');
                String kandidat = (koma >= 0 ? xff.substring(koma + 1) : xff).trim();
                if (!kandidat.isBlank()) {
                    return kandidat;
                }
            }
            String real = request.getHeader("X-Real-IP");
            if (real != null && !real.isBlank()) {
                return real.trim();
            }
        }
        return remote;
    }
}
