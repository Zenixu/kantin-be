package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.BarisKartuTamu;
import com.asqi.scholia_kantin_be.component.exception.ForbiddenException;
import com.asqi.scholia_kantin_be.dto.BarisKerugianStok;
import com.asqi.scholia_kantin_be.dto.BarisPembatalanKasir;
import com.asqi.scholia_kantin_be.dto.BarisPenjualanDimensi;
import com.asqi.scholia_kantin_be.dto.BarisPenjualan;
import com.asqi.scholia_kantin_be.dto.BarisStok;
import com.asqi.scholia_kantin_be.dto.KartuStokItem;
import com.asqi.scholia_kantin_be.dto.LaporanPerSiswa;
import com.asqi.scholia_kantin_be.dto.RekapSetoranTuItem;
import com.asqi.scholia_kantin_be.dto.RingkasanPenjualan;
import com.asqi.scholia_kantin_be.dto.RingkasanRekonsiliasi;
import com.asqi.scholia_kantin_be.dto.RingkasanSaldoMengendap;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Endpoint laporan &amp; ekspor Excel (PRD §9.5).
 *
 * <p>Periode: kirim {@code tanggal} (YYYY-MM-DD, default hari ini zona sekolah)
 * <b>atau</b> rentang {@code dari}/{@code sampai} (ISO date-time). Bila keduanya
 * kosong → hari ini. Semua laporan tenant-scoped dari token.
 *
 * <p>Akses mengikuti PRD §9.5 per kolom <i>Akses</i>. Peran {@link AktorKantin#KEPSEK}
 * (kepala sekolah) bersifat <b>read-only</b> dan hanya pada laporan yang
 * menyebutnya — penjualan/laba kotor, saldo mengendap, rekonsiliasi, kerugian
 * stok. Laporan <b>stok</b> &amp; <b>barang masuk</b> tidak dibuka untuk kepsek
 * (PRD §9.5: hanya Pengelola &amp; Bendahara). Lihat ADR-0012 &amp; issue #123.
 */
@RestController
@RequestMapping("api/laporan")
@RequiredArgsConstructor
public class LaporanController {

    /**
     * Jenis laporan yang boleh <b>diekspor</b> oleh kepsek (PRD §9.5 — hanya
     * laporan yang kolom aksesnya menyebut Kepsek). Dipakai untuk menegakkan
     * batas read-only pada endpoint ekspor yang melayani semua jenis.
     */
    private static final java.util.Set<JenisLaporan> JENIS_EKSPOR_KEPSEK = java.util.EnumSet.of(
            JenisLaporan.PENJUALAN,
            JenisLaporan.PENJUALAN_ITEM,
            JenisLaporan.PENJUALAN_KATEGORI,
            JenisLaporan.SALDO_MENGENDAP,
            JenisLaporan.REKONSILIASI,
            JenisLaporan.KERUGIAN_STOK);

    private final LaporanService laporan;
    private final LaporanExportService exportService;

    /** Ringkasan penjualan &amp; laba kotor (PRD §9.5). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK})
    @GetMapping("penjualan")
    public ResponseEntity<Response<RingkasanPenjualan>> penjualan(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                laporan.ringkasanPenjualan(TenantContext.sekolahIdWajib(), tanggal, dari, sampai));
    }

    /** Penjualan per item (PRD §9.5). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK})
    @GetMapping("penjualan/item")
    public ResponseEntity<Response<List<BarisPenjualan>>> penjualanItem(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                laporan.penjualanPerItem(TenantContext.sekolahIdWajib(), tanggal, dari, sampai));
    }

    /** Penjualan per kategori (PRD §9.5). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK})
    @GetMapping("penjualan/kategori")
    public ResponseEntity<Response<List<BarisPenjualan>>> penjualanKategori(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                laporan.penjualanPerKategori(TenantContext.sekolahIdWajib(), tanggal, dari, sampai));
    }

    /** Saldo mengendap — dana titipan (PRD §9.5). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK})
    @GetMapping("saldo-mengendap")
    public ResponseEntity<Response<RingkasanSaldoMengendap>> saldoMengendap() {
        return CommonResponse.data(laporan.saldoMengendap(TenantContext.sekolahIdWajib()));
    }

    /** Rekonsiliasi harian + pemeriksaan invariant (PRD §9.5, §5). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK})
    @GetMapping("rekonsiliasi")
    public ResponseEntity<Response<RingkasanRekonsiliasi>> rekonsiliasi(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                laporan.rekonsiliasi(TenantContext.sekolahIdWajib(), tanggal, dari, sampai));
    }

    /**
     * Laporan stok + nilai persediaan (PRD §9.5). <b>Tanpa kepsek</b> — kolom
     * akses §9.5 hanya Pengelola &amp; Bendahara.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("stok")
    public ResponseEntity<Response<List<BarisStok>>> stok(
            @RequestParam(defaultValue = "false") boolean hanyaMenipis) {
        return CommonResponse.data(
                laporan.laporanStok(TenantContext.sekolahIdWajib(), hanyaMenipis));
    }

    /** Kerugian stok (opname keluar &amp; barang rusak) (PRD §9.5). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK})
    @GetMapping("kerugian-stok")
    public ResponseEntity<Response<List<BarisKerugianStok>>> kerugianStok(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                laporan.kerugianStok(TenantContext.sekolahIdWajib(), tanggal, dari, sampai));
    }

    /**
     * Laporan <b>Pembatalan kasir</b> (PRD §9.5, issue #114): daftar transaksi
     * yang di-void (tombol Batalkan) per subjek pada periode. RBAC Bendahara/Admin.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("pembatalan")
    public ResponseEntity<Response<List<BarisPembatalanKasir>>> pembatalan(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                laporan.pembatalanKasir(TenantContext.sekolahIdWajib(), tanggal, dari, sampai));
    }

    /**
     * Laporan <b>Kartu Tamu</b> (PRD §9.5, issue #115): daftar kartu + pemegang
     * + saldo + status. Riwayat per kartu lewat {@code GET /api/kartu-tamu/{id}/riwayat}.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("kartu-tamu")
    public ResponseEntity<Response<List<BarisKartuTamu>>> kartuTamu() {
        return CommonResponse.data(laporan.laporanKartuTamu(TenantContext.sekolahIdWajib()));
    }

    /**
     * Laporan <b>Penjualan per titik kasir</b> (PRD §9.5, issue #116): jumlah
     * transaksi, nilai, HPP, laba kotor per titik. RBAC Bendahara/Admin.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("penjualan/titik")
    public ResponseEntity<Response<List<BarisPenjualanDimensi>>> penjualanPerTitik(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                laporan.penjualanPerTitik(TenantContext.sekolahIdWajib(), tanggal, dari, sampai));
    }

    /**
     * Laporan <b>Penjualan per petugas</b> (PRD §9.5, issue #116): jumlah
     * transaksi, nilai, HPP, laba kotor per petugas. RBAC Bendahara/Admin.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("penjualan/petugas")
    public ResponseEntity<Response<List<BarisPenjualanDimensi>>> penjualanPerPetugas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                laporan.penjualanPerPetugas(TenantContext.sekolahIdWajib(), tanggal, dari, sampai));
    }

    /**
     * Laporan <b>Per siswa</b> (PRD §9.5, issue #117): riwayat lengkap satu
     * subjek (SISWA/KARTU_TAMU) pada periode — ringkasan + daftar transaksi +
     * mutasi saldo. Untuk menjawab komplain orang tua. RBAC Bendahara/Admin.
     *
     * @param subjekTipe SISWA (default) atau KARTU_TAMU
     * @param subjekId   id siswa/kartu tamu
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("per-siswa")
    public ResponseEntity<Response<LaporanPerSiswa>> perSiswa(
            @RequestParam(defaultValue = "SISWA") SubjekTipe subjekTipe,
            @RequestParam Long subjekId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai) {
        return CommonResponse.data(
                laporan.laporanPerSiswa(TenantContext.sekolahIdWajib(), subjekTipe, subjekId,
                        tanggal, dari, sampai));
    }

    /**
     * Laporan <b>Setoran kas TU</b> (PRD §9.5, issue #143): rekap top-up tunai
     * per petugas per hari + uang disetor + <b>selisih</b>. RBAC <b>Bendahara</b>
     * (PRD §9.5: hanya Bendahara) — kepsek <b>tidak</b> berhak. Tanpa kepsek.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("setoran-tu")
    public ResponseEntity<Response<List<RekapSetoranTuItem>>> setoranTu(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal) {
        return CommonResponse.data(
                laporan.laporanSetoranTu(TenantContext.sekolahIdWajib(), tanggal));
    }

    /**
     * Laporan <b>Kartu stok per item</b> (PRD §9.5, issue #118): riwayat mutasi
     * satu menu (masuk, terjual, void, penyesuaian) + saldo stok berjalan.
     * RBAC Pengelola/Bendahara.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @GetMapping("kartu-stok/{menuId}")
    public ResponseEntity<Response<KartuStokItem>> kartuStok(
            @org.springframework.web.bind.annotation.PathVariable Long menuId,
            @RequestParam(required = false) Integer batas) {
        KartuStokItem kartu = laporan.kartuStokItem(TenantContext.sekolahIdWajib(), menuId,
                batas == null ? 0 : batas);
        if (kartu == null) {
            throw new com.asqi.scholia_kantin_be.component.exception.NotFoundEntity(
                    "Menu tidak ditemukan");
        }
        return CommonResponse.data(kartu);
    }

    /**
     * Ekspor laporan ke Excel (.xlsx) — PRD §9.5 "semua laporan dapat diekspor".
     * Mengembalikan berkas unduhan langsung (bukan JSON).
     *
     * <p>Kepsek boleh mengekspor <b>hanya</b> jenis laporan yang boleh dibacanya
     * (§9.5); jenis lain → 403 meski anotasi mengizinkan peran tersebut.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK})
    @GetMapping("ekspor")
    public ResponseEntity<ByteArrayResource> ekspor(
            @RequestParam JenisLaporan jenis,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai,
            @RequestParam(required = false) Long menuId,
            @RequestParam(required = false) SubjekTipe subjekTipe,
            @RequestParam(required = false) Long subjekId) {

        IdentitasKantin identitas = TenantContext.get();
        if (identitas != null && identitas.getPeran() == AktorKantin.KEPSEK
                && !JENIS_EKSPOR_KEPSEK.contains(jenis)) {
            throw new ForbiddenException(
                    "Kepsek tidak berhak mengekspor laporan " + jenis + " (PRD §9.5)");
        }

        LaporanExportService.HasilEkspor hasil = exportService.ekspor(
                TenantContext.sekolahIdWajib(), jenis, tanggal, dari, sampai, menuId,
                subjekTipe, subjekId);

        MediaType xlsx = MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + hasil.namaBerkas() + "\"")
                .contentType(xlsx)
                .body(new ByteArrayResource(hasil.isi()));
    }
}
