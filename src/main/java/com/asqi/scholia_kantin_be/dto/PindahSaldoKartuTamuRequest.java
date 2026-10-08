package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Permintaan pemindahan sisa saldo dari Kartu Tamu <b>hilang</b> ke Kartu Tamu
 * baru (PRD §9.4, issue #120).
 *
 * <p>Pemegang lapor ke TU dengan menyebut nomor kartu → TU memblokir kartu lama
 * (berlaku instan) → sisa saldo dipindahkan ke Kartu Tamu baru.
 * {@code referensiId} = nomor berita acara (idempotency).
 */
@Data
public class PindahSaldoKartuTamuRequest {

    @NotNull(message = "ID kartu tamu sumber wajib diisi")
    private Long kartuSumberId;

    @NotNull(message = "ID kartu tamu tujuan wajib diisi")
    private Long kartuTujuanId;

    @NotBlank(message = "Nomor berita acara pemindahan wajib diisi (idempotency)")
    @Size(max = 60)
    private String referensiId;

    @Size(max = 255)
    private String catatan;
}
