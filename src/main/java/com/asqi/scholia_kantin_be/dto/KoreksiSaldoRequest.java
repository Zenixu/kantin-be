package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Permintaan koreksi saldo oleh bendahara (PRD §9.2) — dengan alasan &amp;
 * nomor berita acara <b>wajib</b> (jejak audit).
 */
@Data
public class KoreksiSaldoRequest {

    @NotNull
    private SubjekTipe subjekTipe;

    @NotNull
    private Long subjekId;

    @NotNull(message = "Arah koreksi wajib (KREDIT menambah / DEBIT mengurangi)")
    private ArahMutasi arah;

    @Min(value = 1, message = "Nominal koreksi harus > 0")
    private long nominal;

    @NotBlank(message = "Alasan koreksi wajib diisi (PRD §9.2)")
    @Size(max = 255)
    private String alasan;

    @NotBlank(message = "Nomor berita acara koreksi wajib diisi (idempotency)")
    @Size(max = 60)
    private String referensiId;
}
