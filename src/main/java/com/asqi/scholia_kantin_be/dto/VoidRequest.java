package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Permintaan void (pembatalan) transaksi kasir (PRD §6.3).
 *
 * <p>Alasan <b>wajib</b> — dicatat ke {@code audit_log} (§11.7) dan disimpan
 * pada transaksi sebagai {@code alasan_void}.
 */
@Data
public class VoidRequest {

    @NotBlank(message = "Alasan void wajib diisi (PRD §6.3)")
    @Size(max = 255, message = "Alasan void maksimal 255 karakter")
    private String alasan;
}
