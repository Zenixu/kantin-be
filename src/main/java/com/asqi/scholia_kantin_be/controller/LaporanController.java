package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.BarisKerugianStok;
import com.asqi.scholia_kantin_be.dto.BarisPenjualan;
import com.asqi.scholia_kantin_be.dto.BarisStok;
import com.asqi.scholia_kantin_be.dto.RingkasanPenjualan;
import com.asqi.scholia_kantin_be.dto.RingkasanRekonsiliasi;
import com.asqi.scholia_kantin_be.dto.RingkasanSaldoMengendap;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.PerluPeran;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.laporan.LaporanExportService;
import com.asqi.scholia_kantin_be.service.laporan.LaporanService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Endpoint laporan &amp; ekspor Excel (PRD §9.5).
 *
 * <p>Periode: kirim {@code tanggal} (YYYY-MM-DD, default hari ini zona sekolah)
 * <b>atau</b> rentang {@code dari}/{@code sampai} (ISO date-time). Bila keduanya
 * kosong → hari ini. Semua laporan tenant-scoped dari token.
 *
 * <p>Akses mengikuti PRD §9.5 (bendahara/TU, admin, pengelola, kepsek). Karena
 * kepsek belum dipetakan sebagai {@link AktorKantin}, laporan dibuka untuk
 * peran back-office yang berhak (TU, admin, pengelola).
 */
@RestController
@RequestMapping("api/laporan")
@RequiredArgsConstructor
public class LaporanController {

    private final LaporanService laporan;
    private final LaporanExportService exportService;

    /** Ringkasan penjualan &amp; laba kotor (PRD §9.5). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("penjualan")
    public ResponseEntity<Response<RingkasanPenjualan>> penjualan(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                laporan.ringkasanPenjualan(TenantContext.sekolahIdWajib(), tanggal, dari, sampai));
    }

    /** Penjualan per item (PRD §9.5). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("penjualan/item")
    public ResponseEntity<Response<List<BarisPenjualan>>> penjualanItem(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                laporan.penjualanPerItem(TenantContext.sekolahIdWajib(), tanggal, dari, sampai));
    }

    /** Penjualan per kategori (PRD §9.5). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("penjualan/kategori")
    public ResponseEntity<Response<List<BarisPenjualan>>> penjualanKategori(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                laporan.penjualanPerKategori(TenantContext.sekolahIdWajib(), tanggal, dari, sampai));
    }

    /** Saldo mengendap — dana titipan (PRD §9.5). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("saldo-mengendap")
    public ResponseEntity<Response<RingkasanSaldoMengendap>> saldoMengendap() {
        return CommonResponse.data(laporan.saldoMengendap(TenantContext.sekolahIdWajib()));
    }

    /** Rekonsiliasi harian + pemeriksaan invariant (PRD §9.5, §5). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("rekonsiliasi")
    public ResponseEntity<Response<RingkasanRekonsiliasi>> rekonsiliasi(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                laporan.rekonsiliasi(TenantContext.sekolahIdWajib(), tanggal, dari, sampai));
    }

    /** Laporan stok + nilai persediaan (PRD §9.5). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("stok")
    public ResponseEntity<Response<List<BarisStok>>> stok(
            @RequestParam(defaultValue = "false") boolean hanyaMenipis) {
        return CommonResponse.data(
                laporan.laporanStok(TenantContext.sekolahIdWajib(), hanyaMenipis));
    }

    /** Kerugian stok (opname keluar &amp; barang rusak) (PRD §9.5). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("kerugian-stok")
    public ResponseEntity<Response<List<BarisKerugianStok>>> kerugianStok(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                laporan.kerugianStok(TenantContext.sekolahIdWajib(), tanggal, dari, sampai));
    }

    /**
     * Ekspor laporan ke Excel (.xlsx) — PRD §9.5 "semua laporan dapat diekspor".
     * Mengembalikan berkas unduhan langsung (bukan JSON).
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("ekspor")
    public ResponseEntity<ByteArrayResource> ekspor(
            @RequestParam JenisLaporan jenis,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {

        LaporanExportService.HasilEkspor hasil = exportService.ekspor(
                TenantContext.sekolahIdWajib(), jenis, tanggal, dari, sampai);

        MediaType xlsx = MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + hasil.namaBerkas() + "\"")
                .contentType(xlsx)
                .body(new ByteArrayResource(hasil.isi()));
    }
}
