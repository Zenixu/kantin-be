package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.BarangMasukPembalikRequest;
import com.asqi.scholia_kantin_be.dto.BarangMasukRequest;
import com.asqi.scholia_kantin_be.dto.HalamanResponse;
import com.asqi.scholia_kantin_be.dto.OpnameBatchRequest;
import com.asqi.scholia_kantin_be.dto.OpnameBatchResponse;
import com.asqi.scholia_kantin_be.dto.OpnameRequest;
import com.asqi.scholia_kantin_be.dto.RiwayatStokItem;
import com.asqi.scholia_kantin_be.dto.StokResponse;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import com.asqi.scholia_kantin_be.model.StokCache;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.PerluPeran;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.stok.HppService;
import com.asqi.scholia_kantin_be.service.stok.HasilMutasiStok;
import com.asqi.scholia_kantin_be.service.stok.StokOperasiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Endpoint stok: <b>barang masuk</b> (+ <b>pembalik</b>), <b>opname</b>,
 * <b>riwayat</b>, dan <b>baca stok</b> (PRD §7, §9.5). Semua operasi tulis
 * lewat {@link StokOperasiService} (pembuka transaksi), bukan
 * {@code LedgerStokService} langsung.
 */
@RestController
@RequestMapping("api/stok")
@RequiredArgsConstructor
public class StokController {

    private final StokOperasiService operasi;
    private final HppService hppService;

    /** Barang masuk / stok awal (PRD §7.2) — perbarui HPP rata-rata tertimbang. */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.PENGELOLA_KANTIN, AktorKantin.ADMIN_SEKOLAH})
    @PostMapping("barang-masuk")
    public ResponseEntity<Response<HasilMutasiStok>> barangMasuk(
            @Valid @RequestBody BarangMasukRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        HasilMutasiStok hasil = operasi.masukBarang(
                TenantContext.sekolahIdWajib(), request.getMenuId(), request.getQty(),
                request.getHargaBeliPerUnit(), request.getReferensiId(), identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Barang masuk tercatat");
    }

    /**
     * Barang masuk <b>pembalik</b> (PRD §7.2) — koreksi barang masuk yang salah
     * input. Baris asal tak diubah; dicatat mutasi pembalik baru (wajib alasan).
     * {@code qty} opsional (kosong = batalkan seluruh sisa).
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.PENGELOLA_KANTIN, AktorKantin.ADMIN_SEKOLAH})
    @PostMapping("barang-masuk-pembalik")
    public ResponseEntity<Response<HasilMutasiStok>> barangMasukPembalik(
            @Valid @RequestBody BarangMasukPembalikRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        HasilMutasiStok hasil = operasi.pembalikBarangMasuk(
                TenantContext.sekolahIdWajib(), request.getMutasiId(), request.getQty(),
                request.getAlasan(), request.getReferensiId(), identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Barang masuk pembalik tercatat");
    }

    /** Penyesuaian stok hasil opname fisik (PRD §7.3) — alasan wajib. */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.PENGELOLA_KANTIN, AktorKantin.ADMIN_SEKOLAH})
    @PostMapping("opname")
    public ResponseEntity<Response<HasilMutasiStok>> opname(
            @Valid @RequestBody OpnameRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        HasilMutasiStok hasil = operasi.sesuaikanOpname(
                TenantContext.sekolahIdWajib(), request.getMenuId(), request.getQtyFisik(),
                request.getAlasan(), request.getReferensiId(), identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Penyesuaian stok tercatat");
    }

    /**
     * Opname <b>batch</b> (PRD §7.3) — sesuaikan banyak menu dalam <b>satu</b>
     * transaksi (all-or-nothing), satu nomor berita acara. Setiap item bisa
     * ditandai {@code rusak=true} agar dicatat sebagai {@code BARANG_RUSAK}
     * (rusak/basi), bukan selisih audit {@code OPNAME_KELUAR}.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.PENGELOLA_KANTIN, AktorKantin.ADMIN_SEKOLAH})
    @PostMapping("opname-batch")
    public ResponseEntity<Response<OpnameBatchResponse>> opnameBatch(
            @Valid @RequestBody OpnameBatchRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        OpnameBatchResponse hasil = operasi.opnameBatch(
                TenantContext.sekolahIdWajib(), request.getReferensiId(),
                request.getItems(), identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Penyesuaian stok batch tercatat");
    }

    /**
     * Riwayat mutasi stok (PRD §9.5) — untuk melihat daftar restock sebelum
     * memilih baris yang akan dibalik. Filter opsional: {@code menuId},
     * {@code jenis}, rentang waktu. Terbaru dulu, berhalaman.
     */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.TU_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("riwayat")
    public ResponseEntity<Response<HalamanResponse<RiwayatStokItem>>> riwayat(
            @RequestParam(required = false) Long menuId,
            @RequestParam(required = false) JenisMutasiStok jenis,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dari,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime sampai,
            @RequestParam(defaultValue = "0") int halaman,
            @RequestParam(defaultValue = "20") int ukuran) {

        return CommonResponse.data(operasi.riwayat(
                TenantContext.sekolahIdWajib(), menuId, jenis, dari, sampai, halaman, ukuran));
    }

    /** Stok &amp; HPP berjalan satu menu (PRD §7.4). */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.TU_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("{menuId}")
    public ResponseEntity<Response<StokResponse>> lihat(@PathVariable Long menuId) {
        StokCache cache = operasi.lihat(TenantContext.sekolahIdWajib(), menuId);
        return CommonResponse.data(toResponse(cache));
    }

    /** Daftar menu dengan stok di bawah minimum — untuk peringatan restock (PRD §7.5). */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.TU_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("menipis")
    public ResponseEntity<Response<List<StokResponse>>> menipis() {
        List<StokResponse> daftar = operasi.stokMenipis(TenantContext.sekolahIdWajib())
                .stream()
                .map(this::toResponse)
                .toList();
        return CommonResponse.data(daftar, "Daftar stok menipis");
    }

    /** Rekonsiliasi: hitung ulang stok dari ledger (harus sama dengan cache). */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.PENGELOLA_KANTIN, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("{menuId}/rekonsiliasi")
    public ResponseEntity<Response<Long>> rekonsiliasi(@PathVariable Long menuId) {
        long dariLedger = operasi.hitungUlangDariLedger(TenantContext.sekolahIdWajib(), menuId);
        return CommonResponse.data(dariLedger, "Stok hasil hitung ulang ledger");
    }

    private StokResponse toResponse(StokCache c) {
        return StokResponse.builder()
                .menuId(c.getMenuId())
                .stok(c.getStok())
                .stokMinimum(c.getStokMinimum())
                .hpp(c.getHpp())
                .nilaiPersediaan(hppService.nilaiPersediaan(c.getStok(), c.getHpp()))
                .menipis(c.getStok() <= c.getStokMinimum())
                .habis(c.getStok() <= 0)
                .build();
    }
}
