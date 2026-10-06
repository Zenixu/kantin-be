package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.KonfirmasiSetoranRequest;
import com.asqi.scholia_kantin_be.dto.KoreksiSaldoRequest;
import com.asqi.scholia_kantin_be.dto.RekapSetoranTuItem;
import com.asqi.scholia_kantin_be.dto.SaldoResponse;
import com.asqi.scholia_kantin_be.dto.SetoranTuResponse;
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
import com.asqi.scholia_kantin_be.service.saldo.SetoranTuService;
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

import java.time.LocalDate;
import java.util.List;

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
    private final SetoranTuService setoranService;

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

    /**
     * Rekap setoran kas TU harian per petugas (PRD §9.2) — dasar konfirmasi
     * bendahara. Menampilkan Σ top-up tunai petugas, uang disetor, selisih.
     * {@code tanggal} kosong = hari ini (zona kantin).
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("setoran-tu/rekap")
    public ResponseEntity<Response<List<RekapSetoranTuItem>>> rekapSetoran(
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate tanggal) {

        return CommonResponse.data(
                setoranService.rekap(TenantContext.sekolahIdWajib(), tanggal));
    }

    /**
     * Konfirmasi setoran kas TU oleh bendahara (PRD §9.2). Selisih kas dicatat,
     * tidak dihapus; idempoten lewat nomor berita acara.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @PostMapping("setoran-tu")
    public ResponseEntity<Response<SetoranTuResponse>> konfirmasiSetoran(
            @Valid @RequestBody KonfirmasiSetoranRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        SetoranTuResponse hasil = setoranService.konfirmasi(
                TenantContext.sekolahIdWajib(), request.getTanggal(), request.getPetugasId(),
                request.getJumlahDisetor(), request.getReferensiId(), request.getCatatan(),
                identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Setoran TU berhasil dikonfirmasi");
    }
}
