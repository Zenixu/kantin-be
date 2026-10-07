package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Blokir / buka blokir item atau kategori (PRD §8.3, issue #40).
 *
 * <p>Tepat satu dari {@code menuId} atau {@code kategoriId} wajib terisi —
 * blokir per item spesifik <b>atau</b> per kategori. Ditegakkan di service
 * (pesan ramah) &amp; CHECK database.
 */
@Data
public class BlokirItemRequest {

    @NotNull(message = "Tipe subjek wajib diisi (SISWA/KARTU_TAMU)")
    private SubjekTipe subjekTipe;

    @NotNull(message = "ID subjek wajib diisi")
    private Long subjekId;

    /** Menu yang diblokir (isi salah satu dengan kategoriId). */
    private Long menuId;

    /** Kategori yang diblokir (isi salah satu dengan menuId). */
    private Long kategoriId;

    /** {@code true} = blokir, {@code false} = buka blokir. Default {@code true}. */
    private Boolean diblokir;
}
