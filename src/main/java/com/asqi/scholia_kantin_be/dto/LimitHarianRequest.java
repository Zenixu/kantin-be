package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Set / ubah limit belanja harian (PRD §8.3, issue #40).
 *
 * <p>{@code nominal} {@code null} = <b>tanpa limit</b>. Berlaku seketika; hari
 * direset pukul 00:00 zona sekolah (PRD §11.9).
 */
@Data
public class LimitHarianRequest {

    @NotNull(message = "Tipe subjek wajib diisi (SISWA/KARTU_TAMU)")
    private SubjekTipe subjekTipe;

    @NotNull(message = "ID subjek wajib diisi")
    private Long subjekId;

    /** Batas belanja harian (rupiah); {@code null} = tanpa limit. */
    @Min(value = 0, message = "Limit tidak boleh negatif")
    private Long nominal;
}
