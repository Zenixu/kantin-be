package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Laporan insiden offline kasir (DEMO, OPEN-QUESTIONS Q14 / issue #23).
 *
 * <p>Petugas mencatat insiden saat internet/server mati (prosedur darurat
 * ADR-0006). Minimal kronologi singkat; titik kasir opsional.
 */
@Data
public class LaporInsidenRequest {

    /** Titik kasir terdampak (opsional; null = seluruh kantin). */
    private Long titikKasirId;

    /** Kronologi singkat gangguan (wajib). */
    @NotBlank(message = "Keterangan insiden wajib diisi")
    @Size(max = 500, message = "Keterangan maksimal 500 karakter")
    private String keterangan;
}
