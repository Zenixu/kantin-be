package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.KonteksResponse;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint identitas kantin-be.
 *
 * <p>kantin-be <b>tidak punya login</b> (ADR-0002) — tidak ada {@code /login},
 * {@code /refresh}, atau {@code /logout}. Endpoint di sini hanya <b>membaca</b>
 * identitas yang sudah diverifikasi dari token SKOOLIA.
 */
@RestController
@RequestMapping("api/auth")
public class AuthController {

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
}
