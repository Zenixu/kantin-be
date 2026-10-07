package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.AktivasiModulResponse;
import com.asqi.scholia_kantin_be.dto.PengaturanKantinRequest;
import com.asqi.scholia_kantin_be.dto.PengaturanKantinResponse;
import com.asqi.scholia_kantin_be.dto.TitikKasirRequest;
import com.asqi.scholia_kantin_be.dto.TitikKasirResponse;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.PerluPeran;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.konfigurasi.AktivasiModulService;
import com.asqi.scholia_kantin_be.service.konfigurasi.PengaturanKantinService;
import com.asqi.scholia_kantin_be.service.konfigurasi.TitikKasirService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Pengaturan kantin &amp; titik kasir + status aktivasi modul
 * (PRD §9.1, §10, issue <b>#42</b>).
 *
 * <p>Controller tipis (AGENTS.md §10): tenant dari {@link TenantContext}, RBAC
 * {@code @PerluPeran}, logika di service. Aktivasi modul &amp; fee platform
 * (§10) adalah milik internal-be (Q6) — endpoint ini hanya meneruskan status
 * dari port (fail-open).
 */
@RestController
@RequestMapping("api/pengaturan-kantin")
@RequiredArgsConstructor
public class PengaturanKantinController {

    private final PengaturanKantinService pengaturanService;
    private final TitikKasirService titikKasirService;
    private final AktivasiModulService aktivasiModulService;

    // ────────────────────────────────────────────────────────────────
    // PENGATURAN KANTIN (PRD §9.1)
    // ────────────────────────────────────────────────────────────────

    /** Ambil pengaturan kantin (default aman bila belum diatur). */
    @PerluPeran({AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN, AktorKantin.TU_SEKOLAH})
    @GetMapping
    public ResponseEntity<Response<PengaturanKantinResponse>> ambil() {
        return CommonResponse.data(pengaturanService.ambil(TenantContext.sekolahIdWajib()));
    }

    /** Ubah pengaturan kantin (nama, jam tutup, konfirmasi manual, min/maks top-up, batas saldo). */
    @PerluPeran({AktorKantin.ADMIN_SEKOLAH})
    @PutMapping
    public ResponseEntity<Response<PengaturanKantinResponse>> ubah(
            @Valid @RequestBody PengaturanKantinRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {
        PengaturanKantinResponse hasil = pengaturanService.ubah(
                TenantContext.sekolahIdWajib(), request, identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Pengaturan kantin diperbarui");
    }

    // ────────────────────────────────────────────────────────────────
    // TITIK KASIR (PRD §9.1, §6.6)
    // ────────────────────────────────────────────────────────────────

    /** Daftar titik kasir sekolah. */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.PENGELOLA_KANTIN,
            AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("titik-kasir")
    public ResponseEntity<Response<List<TitikKasirResponse>>> daftarTitik(
            @RequestParam(defaultValue = "false") boolean hanyaAktif) {
        return CommonResponse.data(
                titikKasirService.daftar(TenantContext.sekolahIdWajib(), hanyaAktif));
    }

    /** Detail satu titik kasir. */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.PENGELOLA_KANTIN,
            AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("titik-kasir/{titikId}")
    public ResponseEntity<Response<TitikKasirResponse>> detailTitik(@PathVariable Long titikId) {
        return CommonResponse.data(
                titikKasirService.detail(TenantContext.sekolahIdWajib(), titikId));
    }

    /** Buat titik kasir baru. */
    @PerluPeran({AktorKantin.ADMIN_SEKOLAH})
    @PostMapping("titik-kasir")
    public ResponseEntity<Response<TitikKasirResponse>> buatTitik(
            @Valid @RequestBody TitikKasirRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {
        TitikKasirResponse hasil = titikKasirService.buat(
                TenantContext.sekolahIdWajib(), request.getNama(), request.getKode(),
                identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Titik kasir dibuat");
    }

    /** Ubah titik kasir (nama, kode, status aktif). */
    @PerluPeran({AktorKantin.ADMIN_SEKOLAH})
    @PutMapping("titik-kasir/{titikId}")
    public ResponseEntity<Response<TitikKasirResponse>> ubahTitik(
            @PathVariable Long titikId,
            @Valid @RequestBody TitikKasirRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {
        TitikKasirResponse hasil = titikKasirService.ubah(
                TenantContext.sekolahIdWajib(), titikId, request.getNama(), request.getKode(),
                request.getAktif(), identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Titik kasir diperbarui");
    }

    /** Nonaktifkan titik kasir (soft delete). */
    @PerluPeran({AktorKantin.ADMIN_SEKOLAH})
    @DeleteMapping("titik-kasir/{titikId}")
    public ResponseEntity<Response<TitikKasirResponse>> nonaktifkanTitik(
            @PathVariable Long titikId,
            @AuthenticationPrincipal IdentitasKantin identitas) {
        TitikKasirResponse hasil = titikKasirService.nonaktifkan(
                TenantContext.sekolahIdWajib(), titikId, identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Titik kasir dinonaktifkan");
    }

    // ────────────────────────────────────────────────────────────────
    // AKTIVASI MODUL & FEE (PRD §10 — milik internal-be, Q6)
    // ────────────────────────────────────────────────────────────────

    /** Status aktivasi modul kantin &amp; fee platform (fail-open, Q6). */
    @PerluPeran({AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN, AktorKantin.TU_SEKOLAH})
    @GetMapping("aktivasi-modul")
    public ResponseEntity<Response<AktivasiModulResponse>> aktivasiModul() {
        return CommonResponse.data(
                aktivasiModulService.status(TenantContext.sekolahIdWajib()));
    }
}
