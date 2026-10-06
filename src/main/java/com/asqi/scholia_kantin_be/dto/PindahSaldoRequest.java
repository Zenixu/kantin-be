package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Permintaan pemindahan sisa saldo ke saudara kandung yang masih aktif di
 * sekolah yang sama (PRD §9.3, issue #38).
 *
 * <p>{@code referensiId} = nomor berita acara (idempotency) — satu berita acara
 * menghasilkan tepat satu pemindahan (dua kaki ledger).
 */
@Data
public class PindahSaldoRequest {

    @NotNull(message = "ID siswa sumber wajib diisi")
    private Long siswaSumberId;

    @NotNull(message = "ID siswa tujuan (saudara) wajib diisi")
    private Long siswaTujuanId;

    @NotBlank(message = "Nomor berita acara pemindahan wajib diisi (idempotency)")
    @Size(max = 60)
    private String referensiId;

    @Size(max = 255)
    private String catatan;
}
