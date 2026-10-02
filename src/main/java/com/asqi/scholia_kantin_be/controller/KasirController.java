package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.dto.TapRequest;
import com.asqi.scholia_kantin_be.dto.TapResponse;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.PerluPeran;
import com.asqi.scholia_kantin_be.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint kasir (tap).
 *
 * <p><b>Status: KERANGKA.</b> Service belum ada karena menunggu Q7
 * (kontrak lookup kartu) &amp; Q1/Q2 (klaim JWT). Yang ditunjukkan di sini
 * adalah <b>pola</b> yang wajib diikuti semua controller kantin:
 * <ol>
 *   <li>Controller <b>tipis</b> — tanpa logika bisnis (AGENTS.md §10).</li>
 *   <li>{@code @Valid} pada {@code @RequestBody} untuk memicu Bean Validation.</li>
 *   <li>{@code @PerluPeran} untuk RBAC backend (PRD §11.5).</li>
 *   <li>Tenant diambil lewat {@link TenantContext} (bukan dari request).</li>
 *   <li>Respons dibungkus {@code CommonResponse}.</li>
 * </ol>
 *
 * <p>⚠️ {@code TapService} belum diimplementasikan. Saat menambah, ganti badan
 * method dengan pemanggilan service — <b>jangan</b> menaruh aturan bisnis di sini.
 */
@RestController
@RequestMapping("api/kasir")
@RequiredArgsConstructor
public class KasirController {

    /**
     * Proses satu tap: validasi 6 tahap → potong saldo → kurangi stok → catat
     * transaksi (satu transaksi DB atomik, PRD §11.2).
     *
     * @param request  data tap (termasuk idempotency key dari klien)
     * @param identitas diisi otomatis oleh Spring dari SecurityContext
     */
    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.PENGELOLA_KANTIN, AktorKantin.ADMIN_SEKOLAH})
    @PostMapping("tap")
    public ResponseEntity<Response<TapResponse>> tap(
            @Valid @RequestBody TapRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {

        // TODO: panggil tapService.tap(request, identitas, TenantContext.sekolahIdWajib())
        throw new InvalidOperationException(
                "TapService belum diimplementasikan — menunggu OPEN-QUESTIONS Q1/Q2/Q7");

        // Contoh bentuk akhirnya:
        // TapResponse hasil = tapService.tap(request, identitas, TenantContext.sekolahIdWajib());
        // return CommonResponse.data(hasil, "Transaksi berhasil");
    }
}
