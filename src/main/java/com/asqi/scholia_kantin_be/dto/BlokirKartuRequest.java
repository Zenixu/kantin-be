package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Blokir / buka blokir kartu (PRD §8.3, issue #40).
 *
 * <p>Kontrol melekat ke subjek: siswa (saldo terikat siswa) atau Kartu Tamu
 * (PRD §9.4). Berlaku <b>instan</b> — tap berikutnya langsung ditolak
 * (PRD §11.11).
 */
@Data
public class BlokirKartuRequest {

    @NotNull(message = "Tipe subjek wajib diisi (SISWA/KARTU_TAMU)")
    private SubjekTipe subjekTipe;

    @NotNull(message = "ID subjek wajib diisi")
    private Long subjekId;

    /** {@code true} = blokir, {@code false} = buka blokir. Default {@code true}. */
    private Boolean diblokir;

    @Size(max = 500, message = "Alasan maksimal 500 karakter")
    private String alasan;
}
