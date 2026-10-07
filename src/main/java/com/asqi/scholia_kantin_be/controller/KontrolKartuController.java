package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.BlokirItemRequest;
import com.asqi.scholia_kantin_be.dto.BlokirKartuRequest;
import com.asqi.scholia_kantin_be.dto.KontrolSubjekResponse;
import com.asqi.scholia_kantin_be.dto.LimitHarianRequest;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.PerluPeran;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.kartu.KontrolKartuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint kontrol kartu: blokir kartu, limit harian &amp; blokir item
 * (PRD §6.1, §8.3, issue <b>#40</b>).
 *
 * <p>Blokir berlaku <b>instan</b> — tap berikutnya langsung ditolak
 * (PRD §11.11). RBAC backend (PRD §11.5): admin sekolah mengatur kontrol atas
 * nama ortu (§9.6 "Kontrol Siswa"); ortu (mobile) mengatur anaknya sendiri.
 * Semua tenant-scoped (PRD §11.4).
 */
@RestController
@RequestMapping("api/kontrol-kartu")
@RequiredArgsConstructor
public class KontrolKartuController {

    private final KontrolKartuService service;

    // ────────────────────────────────────────────────────────────────
    // BLOKIR KARTU
    // ────────────────────────────────────────────────────────────────

    /** Blokir / buka blokir kartu (berlaku instan). */
    @PerluPeran({AktorKantin.ADMIN_SEKOLAH, AktorKantin.TU_SEKOLAH, AktorKantin.ORANG_TUA})
    @PostMapping("blokir")
    public ResponseEntity<Response<KontrolSubjekResponse>> ubahBlokir(
            @Valid @RequestBody BlokirKartuRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        boolean diblokir = request.getDiblokir() == null || request.getDiblokir();
        KontrolSubjekResponse hasil = service.ubahBlokir(
                TenantContext.sekolahIdWajib(), request.getSubjekTipe(), request.getSubjekId(),
                diblokir, request.getAlasan(), identitas.aktorIdWajib());
        return CommonResponse.data(hasil, diblokir ? "Kartu diblokir" : "Blokir kartu dibuka");
    }

    // ────────────────────────────────────────────────────────────────
    // LIMIT HARIAN
    // ────────────────────────────────────────────────────────────────

    /** Set / ubah limit belanja harian (nominal null = tanpa limit). */
    @PerluPeran({AktorKantin.ADMIN_SEKOLAH, AktorKantin.TU_SEKOLAH, AktorKantin.ORANG_TUA})
    @PutMapping("limit-harian")
    public ResponseEntity<Response<KontrolSubjekResponse>> setLimit(
            @Valid @RequestBody LimitHarianRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        KontrolSubjekResponse hasil = service.setLimit(
                TenantContext.sekolahIdWajib(), request.getSubjekTipe(), request.getSubjekId(),
                request.getNominal(), identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Limit harian diperbarui");
    }

    // ────────────────────────────────────────────────────────────────
    // BLOKIR ITEM / KATEGORI
    // ────────────────────────────────────────────────────────────────

    /** Blokir / buka blokir item atau kategori. */
    @PerluPeran({AktorKantin.ADMIN_SEKOLAH, AktorKantin.TU_SEKOLAH, AktorKantin.ORANG_TUA})
    @PostMapping("blokir-item")
    public ResponseEntity<Response<KontrolSubjekResponse>> ubahBlokirItem(
            @Valid @RequestBody BlokirItemRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        boolean diblokir = request.getDiblokir() == null || request.getDiblokir();
        KontrolSubjekResponse hasil = service.ubahBlokirItem(
                TenantContext.sekolahIdWajib(), request.getSubjekTipe(), request.getSubjekId(),
                request.getMenuId(), request.getKategoriId(), diblokir, identitas.aktorIdWajib());
        return CommonResponse.data(hasil, diblokir ? "Item diblokir" : "Blokir item dibuka");
    }

    // ────────────────────────────────────────────────────────────────
    // BACA
    // ────────────────────────────────────────────────────────────────

    /** Ringkasan kontrol satu subjek (blokir, limit, item diblokir). */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.PENGELOLA_KANTIN,
            AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.ORANG_TUA})
    @GetMapping("{subjekTipe}/{subjekId}")
    public ResponseEntity<Response<KontrolSubjekResponse>> kontrol(
            @PathVariable SubjekTipe subjekTipe,
            @PathVariable Long subjekId) {
        return CommonResponse.data(
                service.kontrol(TenantContext.sekolahIdWajib(), subjekTipe, subjekId));
    }
}
