package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.HasilRefundResponse;
import com.asqi.scholia_kantin_be.dto.KartuTamuRequest;
import com.asqi.scholia_kantin_be.dto.PindahSaldoKartuTamuRequest;
import com.asqi.scholia_kantin_be.dto.RefundKartuTamuRequest;
import com.asqi.scholia_kantin_be.dto.RiwayatKartuTamuResponse;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.model.KartuTamu;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.PerluPeran;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.kartu.KartuTamuService;
import com.asqi.scholia_kantin_be.service.kartu.RiwayatKartuTamuService;
import com.asqi.scholia_kantin_be.service.saldo.RefundKartuTamuService;
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
 * Endpoint Kartu Tamu: CRUD kartu RFID untuk non-siswa (guru/staf/tamu) — PRD §9.4.
 *
 * <p>Saldo terikat ke nomor kartu (bukan orang). Kartu bisa dipinjamkan,
 * dikembalikan, di-top-up di TU. Pengelola/TU/Admin bisa CRUD, petugas kasir
 * bisa baca (untuk validasi tap).
 */
@RestController
@RequestMapping("api/kartu-tamu")
@RequiredArgsConstructor
public class KartuTamuController {

    private final KartuTamuService service;
    private final RefundKartuTamuService refundService;
    private final RiwayatKartuTamuService riwayatService;

    /**
     * Daftar semua kartu tamu milik sekolah.
     * Petugas kasir bisa baca untuk validasi tap.
     */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.PENGELOLA_KANTIN,
            AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping
    public ResponseEntity<Response<List<KartuTamu>>> daftarKartu(
            @RequestParam(defaultValue = "false") boolean hanyaAktif) {
        List<KartuTamu> kartuList = service.daftarKartu(
                TenantContext.sekolahIdWajib(), hanyaAktif);
        return CommonResponse.data(kartuList);
    }

    /**
     * Detail 1 kartu tamu.
     */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.PENGELOLA_KANTIN,
            AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("{kartuId}")
    public ResponseEntity<Response<KartuTamu>> detailKartu(@PathVariable Long kartuId) {
        KartuTamu kartu = service.detailKartu(TenantContext.sekolahIdWajib(), kartuId);
        return CommonResponse.data(kartu);
    }

    /**
     * Buat kartu tamu baru (PRD §9.4).
     * Nomor kartu <b>digenerate otomatis</b> (KT- + urutan) bila tidak diisi.
     * Hanya pengelola/TU/admin yang bisa buat.
     */
    @PerluPeran({AktorKantin.PENGELOLA_KANTIN, AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @PostMapping
    public ResponseEntity<Response<KartuTamu>> buatKartu(
            @Valid @RequestBody KartuTamuRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        KartuTamu kartu = service.buatKartu(
                TenantContext.sekolahIdWajib(),
                request.getNomorKartu(),
                request.getRfidUid(),
                request.getCatatan(),
                request.getLabelPemegang(),
                identitas.aktorIdWajib());

        return CommonResponse.data(kartu, "Kartu tamu berhasil dibuat");
    }

    /**
     * Pratinjau nomor kartu berikutnya (PRD §9.4) — untuk ditampilkan di form
     * sebelum kartu disimpan/dicetak. Tidak menyimpan apa pun.
     */
    @PerluPeran({AktorKantin.PENGELOLA_KANTIN, AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("nomor-berikutnya")
    public ResponseEntity<Response<String>> nomorBerikutnya() {
        String nomor = service.generateNomorKartu(TenantContext.sekolahIdWajib());
        return CommonResponse.data(nomor);
    }

    /**
     * Update kartu tamu (nomor, UID, catatan, label pemegang, status).
     * Kirim {@code labelPemegang} = "" untuk mengosongkan (mis. pengembalian).
     * Hanya pengelola/TU/admin yang bisa update.
     */
    @PerluPeran({AktorKantin.PENGELOLA_KANTIN, AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @PutMapping("{kartuId}")
    public ResponseEntity<Response<KartuTamu>> updateKartu(
            @PathVariable Long kartuId,
            @Valid @RequestBody KartuTamuRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        KartuTamu kartu = service.updateKartu(
                TenantContext.sekolahIdWajib(),
                kartuId,
                request.getNomorKartu(),
                request.getRfidUid(),
                request.getCatatan(),
                request.getLabelPemegang(),
                request.getAktif(),
                identitas.aktorIdWajib());

        return CommonResponse.data(kartu, "Kartu tamu berhasil diperbarui");
    }

    /**
     * Nonaktifkan kartu tamu (soft delete).
     * Kartu nonaktif tidak bisa dipakai tap.
     */
    @PerluPeran({AktorKantin.PENGELOLA_KANTIN, AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @DeleteMapping("{kartuId}")
    public ResponseEntity<Response<KartuTamu>> nonaktifkanKartu(
            @PathVariable Long kartuId,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        KartuTamu kartu = service.nonaktifkanKartu(
                TenantContext.sekolahIdWajib(),
                kartuId,
                identitas.aktorIdWajib());

        return CommonResponse.data(kartu, "Kartu tamu berhasil dinonaktifkan");
    }

    // ────────────────────────────────────────────────────────────────
    // SALDO KHUSUS KARTU TAMU (PRD §9.4, issue #120)
    // ────────────────────────────────────────────────────────────────

    /**
     * Refund sisa saldo saat <b>pengembalian kartu</b> (PRD §9.4): sisa saldo
     * di-refund <b>tunai</b>, saldo → 0, label pemegang dikosongkan agar kartu
     * dapat dipakai ulang. Idempoten lewat nomor bukti. RBAC TU/bendahara.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @PostMapping("refund")
    public ResponseEntity<Response<HasilRefundResponse>> refundKartu(
            @Valid @RequestBody RefundKartuTamuRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        HasilRefundResponse hasil = refundService.refund(
                TenantContext.sekolahIdWajib(), request.getKartuId(), request.getReferensiId(),
                request.getCatatan(), identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Refund saldo kartu tamu berhasil");
    }

    /**
     * Pindahkan sisa saldo dari Kartu Tamu <b>hilang</b> ke Kartu Tamu baru
     * (PRD §9.4): kartu lama diblokir (berlaku instan), saldo pindah. Idempoten
     * lewat nomor berita acara. RBAC TU/bendahara.
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH, AktorKantin.PENGELOLA_KANTIN})
    @PostMapping("pindah-saldo")
    public ResponseEntity<Response<HasilRefundResponse>> pindahSaldoKartu(
            @Valid @RequestBody PindahSaldoKartuTamuRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        HasilRefundResponse hasil = refundService.pindahKartuHilang(
                TenantContext.sekolahIdWajib(), request.getKartuSumberId(),
                request.getKartuTujuanId(), request.getReferensiId(),
                request.getCatatan(), identitas.aktorIdWajib());
        return CommonResponse.data(hasil, "Saldo kartu tamu berhasil dipindahkan");
    }

    /**
     * Riwayat transaksi &amp; mutasi saldo satu Kartu Tamu (PRD §9.4/§9.5,
     * issue #122) — dapat dilihat/dicetak TU atas permintaan pemegang.
     * Transaksi terbaru dulu &amp; berhalaman; tenant-scoped.
     */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.TU_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("{kartuId}/riwayat")
    public ResponseEntity<Response<RiwayatKartuTamuResponse>> riwayatKartu(
            @PathVariable Long kartuId,
            @RequestParam(defaultValue = "20") int batasMutasi,
            @RequestParam(defaultValue = "0") int halaman,
            @RequestParam(defaultValue = "20") int ukuran) {

        RiwayatKartuTamuResponse hasil = riwayatService.riwayat(
                TenantContext.sekolahIdWajib(), kartuId, batasMutasi, halaman, ukuran);
        return CommonResponse.data(hasil);
    }
}
