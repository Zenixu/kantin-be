package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.BukaSesiRequest;
import com.asqi.scholia_kantin_be.dto.TapRequest;
import com.asqi.scholia_kantin_be.dto.TapResponse;
import com.asqi.scholia_kantin_be.dto.VoidRequest;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.model.SesiKasir;
import com.asqi.scholia_kantin_be.model.Transaksi;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.PerluPeran;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.kasir.SesiKasirService;
import com.asqi.scholia_kantin_be.service.kasir.TapService;
import com.asqi.scholia_kantin_be.service.kasir.VoidService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint kasir: <b>tap</b>, <b>void</b>, dan <b>siklus sesi kasir</b>.
 *
 * <p>Controller <b>tipis</b> — tanpa logika bisnis (AGENTS.md §10):
 * <ol>
 *   <li>{@code @Valid} pada {@code @RequestBody} (Bean Validation).</li>
 *   <li>{@code @PerluPeran} untuk RBAC backend (PRD §11.5).</li>
 *   <li>Tenant lewat {@link TenantContext} (bukan dari body request) — PRD §11.4.</li>
 *   <li>Respons dibungkus {@code CommonResponse}.</li>
 * </ol>
 */
@RestController
@RequestMapping("api/kasir")
@RequiredArgsConstructor
public class KasirController {

    private final TapService tapService;
    private final VoidService voidService;
    private final SesiKasirService sesiKasirService;

    /**
     * Proses satu tap: validasi 6 tahap → potong saldo → kurangi stok → catat
     * transaksi (satu transaksi DB atomik, PRD §11.2).
     *
     * @param request   data tap (termasuk idempotency key dari klien)
     * @param identitas diisi otomatis oleh Spring dari SecurityContext
     */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.PENGELOLA_KANTIN, AktorKantin.ADMIN_SEKOLAH})
    @PostMapping("tap")
    public ResponseEntity<Response<TapResponse>> tap(
            @Valid @RequestBody TapRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        TapResponse hasil = tapService.tap(TenantContext.sekolahIdWajib(), identitas, request);
        return CommonResponse.data(hasil, "Transaksi berhasil");
    }

    /**
     * Batalkan (void) transaksi — kembalikan saldo &amp; stok (PRD §6.3).
     * Hanya untuk transaksi pada sesi yang belum ditutup; wajib alasan.
     */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.PENGELOLA_KANTIN, AktorKantin.ADMIN_SEKOLAH})
    @PostMapping("transaksi/{transaksiId}/void")
    public ResponseEntity<Response<Transaksi>> voidTransaksi(
            @PathVariable Long transaksiId,
            @Valid @RequestBody VoidRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        Transaksi trx = voidService.voidTransaksi(
                TenantContext.sekolahIdWajib(), transaksiId, identitas.aktorIdWajib(), request.getAlasan());
        return CommonResponse.data(trx, "Transaksi berhasil di-void");
    }

    /**
     * Ambil sesi kasir terbuka hari ini untuk titik kasir, atau buka sesi baru
     * (PRD §6.4). Idempoten per (sekolah, titik, hari) — aman dipanggil
     * berkali-kali oleh klien kasir saat mulai shift.
     */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.PENGELOLA_KANTIN, AktorKantin.ADMIN_SEKOLAH})
    @PostMapping("sesi/buka")
    public ResponseEntity<Response<SesiKasir>> bukaSesi(
            @Valid @RequestBody BukaSesiRequest request) {

        SesiKasir sesi = sesiKasirService.bukaSesi(
                TenantContext.sekolahIdWajib(), request.getTitikKasirId());
        return CommonResponse.data(sesi, "Sesi kasir dibuka");
    }

    /** Pratinjau rekap sesi (bruto/void/bersih) sebelum tutup kasir (PRD §6.4). */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.PENGELOLA_KANTIN, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("sesi/{sesiId}/rekap")
    public ResponseEntity<Response<SesiKasirService.RekapSesi>> rekap(
            @PathVariable Long sesiId) {

        return CommonResponse.data(sesiKasirService.rekap(TenantContext.sekolahIdWajib(), sesiId));
    }

    /**
     * Tutup sesi kasir: hitung rekap, tandai DITUTUP, kunci transaksi
     * (PRD §6.4). Setelah ditutup, koreksi hanya boleh oleh bendahara.
     */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.PENGELOLA_KANTIN, AktorKantin.ADMIN_SEKOLAH})
    @PostMapping("sesi/{sesiId}/tutup")
    public ResponseEntity<Response<SesiKasir>> tutupSesi(
            @PathVariable Long sesiId,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        SesiKasir sesi = sesiKasirService.tutupSesi(
                TenantContext.sekolahIdWajib(), sesiId, identitas.aktorIdWajib(), false);
        return CommonResponse.data(sesi, "Sesi kasir ditutup");
    }

    /** Ambil detail satu sesi kasir (tenant-scoped). */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.PENGELOLA_KANTIN, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("sesi/{sesiId}")
    public ResponseEntity<Response<SesiKasir>> ambilSesi(@PathVariable Long sesiId) {
        return CommonResponse.data(sesiKasirService.ambil(TenantContext.sekolahIdWajib(), sesiId));
    }
}
