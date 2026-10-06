package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.HasilRefundResponse;
import com.asqi.scholia_kantin_be.dto.KandidatRefundItem;
import com.asqi.scholia_kantin_be.dto.KoreksiSaldoRequest;
import com.asqi.scholia_kantin_be.dto.PindahSaldoRequest;
import com.asqi.scholia_kantin_be.dto.RefundSaldoRequest;
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
import com.asqi.scholia_kantin_be.service.saldo.RefundSaldoService;
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
    private final RefundSaldoService refundService;

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

    // ────────────────────────────────────────────────────────────────
    // REFUND SALDO SISWA KELUAR & PINDAH KE SAUDARA (PRD §9.3)
    // ────────────────────────────────────────────────────────────────

    /**
     * Daftar kandidat refund/pindah: siswa nonaktif bersisa saldo (PRD §9.3).
     * {@code hanyaTidakAktif=true} menyaring yang <b>diketahui</b> nonaktif
     * (integrasi Q7 siap); default semua siswa bersisa saldo.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("refund/kandidat")
    public ResponseEntity<Response<List<KandidatRefundItem>>> kandidatRefund(
            @RequestParam(defaultValue = "false") boolean hanyaTidakAktif) {

        return CommonResponse.data(
                refundService.daftarKandidat(TenantContext.sekolahIdWajib(), hanyaTidakAktif));
    }

    /**
     * Refund seluruh sisa saldo siswa keluar ke ortu (PRD §9.3). Saldo menjadi 0,
     * kartu diminta diblokir. Idempoten lewat nomor bukti.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @PostMapping("refund")
    public ResponseEntity<Response<HasilRefundResponse>> refund(
            @Valid @RequestBody RefundSaldoRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        HasilRefundResponse hasil = refundService.refund(
                TenantContext.sekolahIdWajib(), request.getSubjekId(), request.getReferensiId(),
                request.getCatatan(), identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Refund saldo berhasil");
    }

    /**
     * Pindahkan seluruh sisa saldo ke saudara kandung yang masih aktif di sekolah
     * yang sama (PRD §9.3). Idempoten lewat nomor berita acara.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @PostMapping("pindah-saldo")
    public ResponseEntity<Response<HasilRefundResponse>> pindahSaldo(
            @Valid @RequestBody PindahSaldoRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        HasilRefundResponse hasil = refundService.pindahKeSaudara(
                TenantContext.sekolahIdWajib(), request.getSiswaSumberId(), request.getSiswaTujuanId(),
                request.getReferensiId(), request.getCatatan(), identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Saldo berhasil dipindahkan ke saudara");
    }
}
