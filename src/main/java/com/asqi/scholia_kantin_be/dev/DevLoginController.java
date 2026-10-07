package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * Endpoint login <b>shim dev</b> — meniru {@code POST /api/v1/auth/login}
 * admin-be agar FE dapat token RS256 nyata yang diterima kantin-be.
 *
 * <p>Hanya hidup bila profil {@code local} <b>dan</b>
 * {@code kantin.dev-login.enabled=true}. Password <b>tidak</b> diverifikasi
 * (alat uji). Pemetaan role mengikuti {@code KlaimResolver.petakanPeran}.
 */
@RestController
@Profile("local")
@ConditionalOnProperty(prefix = "kantin.dev-login", name = "enabled", havingValue = "true")
@RequestMapping("api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class DevLoginController {

    private final DevTokenService tokenService;

    @PostMapping("login")
    public ResponseEntity<Response<DevLoginResponse>> login(@RequestBody DevLoginRequest request) {
        String identitas = request.email() != null && !request.email().isBlank()
                ? request.email()
                : (request.username() != null ? request.username() : "staf@skoolia.id");

        long sekolahId = request.sekolah_id() != null ? request.sekolah_id() : 10L;
        String role = petakanRole(identitas);
        long userId = 1L;
        String nama = namaUntuk(role);

        String token = tokenService.terbitkan(identitas, userId, nama, role, sekolahId);

        log.info("[DEV-LOGIN] token diterbitkan untuk {} (role={}, sekolah={})",
                identitas, role, sekolahId);

        DevLoginResponse body = new DevLoginResponse(
                token,
                new DevLoginResponse.DevUser(
                        userId,
                        nama,
                        identitas,
                        roleFe(role),
                        List.of("ROLE_" + role),
                        new DevLoginResponse.Sekolah(sekolahId, "SMA Negeri 1 SKOOLIA")));

        return CommonResponse.data(body, "Login berhasil (dev shim)");
    }

    /** Role gaya FE ({@code admin|pengelola|tu|bendahara|kasir}) untuk {@code currentRole}. */
    private String roleFe(String role) {
        return switch (role) {
            case "PETUGAS" -> "kasir";
            case "PENGELOLA" -> "pengelola";
            case "TATA_USAHA" -> "tu";
            case "BENDAHARA" -> "bendahara";
            default -> "admin";
        };
    }

    /**
     * Petakan role dari email → nilai klaim {@code role} yang dibaca
     * {@code KlaimResolver}. Nilai sengaja dipilih agar TIDAK memicu cabang
     * yang salah: mis. {@code "PETUGAS"} (bukan {@code "PETUGAS_KANTIN"}, yang
     * mengandung "KANTIN" dan justru dipetakan ke PENGELOLA_KANTIN).
     */
    private String petakanRole(String identitas) {
        String s = identitas.toLowerCase(Locale.ROOT);
        if (s.contains("kasir") || s.contains("petugas")) {
            return "PETUGAS";
        }
        if (s.contains("pengelola") || s.contains("kantin")) {
            return "PENGELOLA";
        }
        if (s.contains("bendahara")) {
            return "BENDAHARA";
        }
        if (s.contains("tu") || s.contains("tata_usaha") || s.contains("tata usaha")) {
            return "TATA_USAHA";
        }
        return "ADMIN";
    }

    private String namaUntuk(String role) {
        return switch (role) {
            case "PETUGAS" -> "Ahmad Kasir (Petugas POS)";
            case "PENGELOLA" -> "Deryl Fabiensyah (Pengelola)";
            case "TATA_USAHA" -> "Andika Pratama (Petugas TU)";
            case "BENDAHARA" -> "Siti Rahma (Bendahara)";
            default -> "Wibisana Bama (Admin)";
        };
    }
}
