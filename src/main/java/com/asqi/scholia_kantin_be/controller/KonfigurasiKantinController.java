package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.InsidenOfflineResponse;
import com.asqi.scholia_kantin_be.dto.KebijakanKantinResponse;
import com.asqi.scholia_kantin_be.dto.LaporInsidenRequest;
import com.asqi.scholia_kantin_be.dto.PosBukuKasResponse;
import com.asqi.scholia_kantin_be.dto.ProfilKantinResponse;
import com.asqi.scholia_kantin_be.dto.UbahKebijakanRequest;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.PerluPeran;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.konfigurasi.InsidenOfflineService;
import com.asqi.scholia_kantin_be.service.konfigurasi.KebijakanKantinService;
import com.asqi.scholia_kantin_be.service.konfigurasi.PosBukuKasService;
import com.asqi.scholia_kantin_be.service.konfigurasi.ProfilKantinService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Konfigurasi kantin &amp; solusi demo untuk pertanyaan terblokir
 * (issues <b>#21, #23, #25</b>).
 *
 * <p>Karena kantin-be belum rilis penuh, endpoint ini memberi solusi "seadanya"
 * agar modul tetap jalan tanpa menunggu keputusan tim lain:
 * <ul>
 *   <li><b>#21 / Q8</b> — pos Buku Kas dibuat otomatis per sekolah
 *       (fallback lokal sampai admin-be mengonfirmasi).</li>
 *   <li><b>#23 / Q14</b> — pencatatan insiden offline (prosedur darurat
 *       ADR-0006) sebagai dasar keputusan memajukan mode offline.</li>
 *   <li><b>#25 / Q16</b> — kebijakan saldo mengendap per sekolah
 *       (default REFUND).</li>
 * </ul>
 *
 * <p>Controller tipis (AGENTS.md §10): tenant dari {@link TenantContext}, RBAC
 * {@code @PerluPeran}, logika di service.
 */
@RestController
@RequestMapping("api/konfigurasi")
@RequiredArgsConstructor
public class KonfigurasiKantinController {

    private final PosBukuKasService posBukuKasService;
    private final InsidenOfflineService insidenOfflineService;
    private final KebijakanKantinService kebijakanKantinService;
    private final ProfilKantinService profilKantinService;

    // ────────────────────────────────────────────────────────────────
    // #21 / Q8 — POS BUKU KAS
    // ────────────────────────────────────────────────────────────────

    /**
     * Aktifkan modul kantin: pastikan pos Buku Kas standar dibuat otomatis
     * (idempoten). Aman dipanggil berkali-kali.
     */
    @PerluPeran({AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN, AktorKantin.TU_SEKOLAH})
    @PostMapping("pos-buku-kas/aktivasi")
    public ResponseEntity<Response<List<PosBukuKasResponse>>> aktivasiPos(
            @AuthenticationPrincipal IdentitasKantin identitas) {

        List<PosBukuKasResponse> pos = posBukuKasService.pastikanPosStandar(
                TenantContext.sekolahIdWajib(), identitas.aktorIdWajib());
        return CommonResponse.data(pos, "Pos Buku Kas kantin siap");
    }

    /** Daftar pos Buku Kas kantin (tenant-scoped). */
    @PerluPeran({AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN,
            AktorKantin.TU_SEKOLAH, AktorKantin.PETUGAS_KANTIN})
    @GetMapping("pos-buku-kas")
    public ResponseEntity<Response<List<PosBukuKasResponse>>> daftarPos() {
        return CommonResponse.data(posBukuKasService.daftar(TenantContext.sekolahIdWajib()));
    }

    // ────────────────────────────────────────────────────────────────
    // #23 / Q14 — INSIDEN OFFLINE (prosedur darurat)
    // ────────────────────────────────────────────────────────────────

    /**
     * Laporkan insiden offline (internet/server mati) — prosedur darurat
     * ADR-0006. Append-only; dicatat oleh petugas saat gangguan terjadi.
     */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.PENGELOLA_KANTIN,
            AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @PostMapping("insiden-offline")
    public ResponseEntity<Response<InsidenOfflineResponse>> laporInsiden(
            @Valid @RequestBody LaporInsidenRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        InsidenOfflineResponse hasil = insidenOfflineService.lapor(
                TenantContext.sekolahIdWajib(), request.getTitikKasirId(),
                request.getKeterangan(), identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Insiden offline dicatat");
    }

    /** Daftar insiden offline satu sekolah, terbaru dulu (tenant-scoped). */
    @PerluPeran({AktorKantin.PENGELOLA_KANTIN, AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("insiden-offline")
    public ResponseEntity<Response<List<InsidenOfflineResponse>>> daftarInsiden() {
        return CommonResponse.data(
                insidenOfflineService.daftar(TenantContext.sekolahIdWajib()));
    }

    // ────────────────────────────────────────────────────────────────
    // #25 / Q16 — KEBIJAKAN SALDO MENGENDAP
    // ────────────────────────────────────────────────────────────────

    /** Ambil kebijakan kantin (default REFUND bila belum diubah). */
    @PerluPeran({AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN, AktorKantin.TU_SEKOLAH})
    @GetMapping("kebijakan")
    public ResponseEntity<Response<KebijakanKantinResponse>> ambilKebijakan() {
        return CommonResponse.data(kebijakanKantinService.ambil(TenantContext.sekolahIdWajib()));
    }

    /** Ubah kebijakan kantin (mis. saldo mengendap siswa lulus). */
    @PerluPeran({AktorKantin.ADMIN_SEKOLAH})
    @PutMapping("kebijakan")
    public ResponseEntity<Response<KebijakanKantinResponse>> ubahKebijakan(
            @Valid @RequestBody UbahKebijakanRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        KebijakanKantinResponse hasil = kebijakanKantinService.ubah(
                TenantContext.sekolahIdWajib(), request.getKebijakanSaldoMengendap(),
                request.getAmbangHari(), request.getCatatan(), identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Kebijakan kantin diperbarui");
    }

    // ────────────────────────────────────────────────────────────────
    // #22 / Q17 & #24 / Q15 — PROFIL PERANGKAT & POSTUR REGULASI
    // ────────────────────────────────────────────────────────────────

    /**
     * Profil perangkat &amp; postur kepatuhan kantin (asumsi demo):
     * reader RFID USB (#22/Q17) dan model dana titipan (#24/Q15).
     * Dipakai tim RFID/legal &amp; FE untuk melihat asumsi aktif sekarang.
     */
    @PerluPeran({AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN,
            AktorKantin.TU_SEKOLAH, AktorKantin.PETUGAS_KANTIN})
    @GetMapping("profil")
    public ResponseEntity<Response<ProfilKantinResponse>> profil() {
        return CommonResponse.data(profilKantinService.ambil());
    }
}
