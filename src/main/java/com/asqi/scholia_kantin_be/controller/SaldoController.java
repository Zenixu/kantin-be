package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.KoreksiSaldoRequest;
import com.asqi.scholia_kantin_be.dto.SaldoResponse;
import com.asqi.scholia_kantin_be.dto.TopUpRequest;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.PerluPeran;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.kasir.HasilMutasiSaldo;
import com.asqi.scholia_kantin_be.service.saldo.SaldoOperasiService;
import com.asqi.scholia_kantin_be.service.saldo.SaldoTopUpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint saldo: <b>top-up tunai</b>, <b>koreksi</b> (bendahara), dan
 * <b>baca saldo/riwayat</b> (PRD §9).
 *
 * <p>Controller tipis (AGENTS.md §10): validasi {@code @Valid}, RBAC
 * {@code @PerluPeran}, tenant dari {@link TenantContext}, aktor dari token
 * ({@code aktorIdWajib()}). Logika &amp; idempotency ada di service.
 */
@RestController
@RequestMapping("api/saldo")
@RequiredArgsConstructor
public class SaldoController {

    private final SaldoTopUpService topUpService;
    private final SaldoOperasiService operasi;

    /**
     * Top-up tunai di TU (PRD §9.1). Idempotent lewat {@code referensiId}
     * (nomor bukti) — input bukti sama tidak menambah saldo dua kali.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @PostMapping("topup")
    public ResponseEntity<Response<HasilMutasiSaldo>> topUp(
            @Valid @RequestBody TopUpRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        HasilMutasiSaldo hasil = topUpService.topUpTunai(
                TenantContext.sekolahIdWajib(), request.getSubjekTipe(), request.getSubjekId(),
                request.getNominal(), request.getPenyetor(), request.getReferensiId(),
                identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Top-up berhasil");
    }

    /**
     * Koreksi saldo oleh bendahara (PRD §9.2) — menambah/mengurangi dengan
     * alasan &amp; berita acara wajib (idempotency).
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @PostMapping("koreksi")
    public ResponseEntity<Response<HasilMutasiSaldo>> koreksi(
            @Valid @RequestBody KoreksiSaldoRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        HasilMutasiSaldo hasil = topUpService.koreksi(
                TenantContext.sekolahIdWajib(), request.getSubjekTipe(), request.getSubjekId(),
                request.getArah(), request.getNominal(), request.getAlasan(),
                request.getReferensiId(), identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Koreksi saldo berhasil");
    }

    /** Saldo berjalan + belanja hari ini + mutasi terbaru (PRD §9.3). */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.TU_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping
    public ResponseEntity<Response<SaldoResponse>> lihat(
            @RequestParam SubjekTipe subjekTipe,
            @RequestParam Long subjekId,
            @RequestParam(defaultValue = "20") int batasMutasi) {

        return CommonResponse.data(operasi.lihat(
                TenantContext.sekolahIdWajib(), subjekTipe, subjekId, batasMutasi));
    }

    /** Rekonsiliasi: hitung ulang saldo dari ledger (harus sama dengan cache). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("rekonsiliasi")
    public ResponseEntity<Response<Long>> rekonsiliasi(
            @RequestParam SubjekTipe subjekTipe,
            @RequestParam Long subjekId) {

        long dariLedger = operasi.hitungUlangDariLedger(
                TenantContext.sekolahIdWajib(), subjekTipe, subjekId);
        return CommonResponse.data(dariLedger, "Saldo hasil hitung ulang ledger");
    }
}
