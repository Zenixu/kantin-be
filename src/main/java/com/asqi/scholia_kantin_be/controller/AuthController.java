package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.config.security.KantinJwtDecoder;
import com.asqi.scholia_kantin_be.dto.KonteksResponse;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.enums.SumberToken;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.PerluPeran;
import com.asqi.scholia_kantin_be.security.blacklist.TokenBlacklistPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Endpoint identitas kantin-be.
 *
 * <p>kantin-be <b>tidak punya login</b> (ADR-0002) — tidak ada {@code /login}
 * atau {@code /refresh}. Endpoint di sini hanya <b>membaca</b> identitas dari
 * token SKOOLIA, ditambah {@code /cabut} untuk mencabut token yang dikelola
 * platform (mis. akun dinonaktifkan, token bocor).
 */
@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private static final String BEARER = "Bearer ";

    private final TokenBlacklistPort blacklist;
    private final KantinJwtDecoder decoder;

    /**
     * Kembalikan konteks pemanggil berdasarkan token yang sudah diverifikasi
     * filter. Berguna untuk FE memeriksa sesi & hak akses.
     */
    @GetMapping("me")
    public ResponseEntity<Response<KonteksResponse>> me(
            @AuthenticationPrincipal IdentitasKantin identitas) {

        KonteksResponse konteks = KonteksResponse.builder()
                .userId(identitas.getUserId())
                .nama(identitas.getNama())
                .sekolahId(identitas.getSekolahId())
                .peran(identitas.getPeran())
                .sumber(identitas.getSumber())
                .siswaId(identitas.getSiswaId())
                .punyaSekolah(identitas.punyaSekolah())
                .build();

        return CommonResponse.data(konteks);
    }

    /**
     * Cabut token yang dipakai pada permintaan ini (self-revoke).
     *
     * <p>Berguna saat pengguna keluar perangkat bersama atau token bocor.
     * Hanya admin sekolah / TU yang boleh — dicatat agar tidak disalahgunakan.
     * Token bertahan di blacklist sampai kedaluwarsa alaminya.
     */
    @PostMapping("cabut")
    @PerluPeran({AktorKantin.ADMIN_SEKOLAH, AktorKantin.TU_SEKOLAH})
    public ResponseEntity<Response<Void>> cabut(HttpServletRequest request,
                                                @AuthenticationPrincipal IdentitasKantin identitas) {
        String token = ambilToken(request);
        if (token == null) {
            return CommonResponse.badRequest("Tidak ada token untuk dicabut pada permintaan ini");
        }

        long sisaDetik = hitungSisaDetik(token);
        blacklist.cabut(token, sisaDetik);

        log.info("Token dicabut oleh aktor {} (sekolah {}), sisa umur {} detik",
                identitas.getUserId(), identitas.getSekolahId(), Math.max(sisaDetik, 0));

        return CommonResponse.success("Token dicabut");
    }

    /** Sisa umur token (detik); 0 bila sudah kedaluwarsa / tak diketahui. */
    private long hitungSisaDetik(String token) {
        try {
            // Coba kedua issuer; ambil exp dari yang berhasil.
            for (SumberToken sumber : SumberToken.values()) {
                try {
                    Claims claims = decoder.verifikasi(token, sumber);
                    Instant exp = claims.getExpiration() == null
                            ? null : claims.getExpiration().toInstant();
                    if (exp == null) {
                        return 0;
                    }
                    return exp.getEpochSecond() - Instant.now().getEpochSecond();
                } catch (JwtException ignored) {
                    // coba issuer berikutnya
                }
            }
        } catch (RuntimeException e) {
            log.warn("Gagal menghitung sisa umur token: {}", e.getMessage());
        }
        return 0;
    }

    private String ambilToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length()).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        return null;
    }
}
