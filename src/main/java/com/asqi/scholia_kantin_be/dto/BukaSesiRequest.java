package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Permintaan buka sesi kasir (PRD §6.4).
 *
 * <p>Sesi diidentifikasi oleh (sekolah, titik kasir, tanggal) — jika sesi hari
 * ini untuk titik tsb. sudah ada, service mengembalikannya (idempoten), bukan
 * membuat duplikat.
 */
@Data
public class BukaSesiRequest {

    @NotNull(message = "Titik kasir wajib diisi")
    private Long titikKasirId;
}
