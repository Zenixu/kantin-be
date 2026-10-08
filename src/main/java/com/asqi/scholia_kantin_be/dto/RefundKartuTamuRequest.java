package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Permintaan refund sisa saldo Kartu Tamu saat <b>pengembalian kartu</b>
 * (PRD §9.4, issue #120).
 *
 * <p>Sisa saldo di-refund <b>tunai</b> oleh TU kepada pemegang, saldo menjadi 0,
 * lalu label pemegang dikosongkan sehingga kartu dapat dipakai ulang.
 * {@code referensiId} = nomor bukti (idempotency) — retry/double-submit tidak
 * mengembalikan dua kali.
 */
@Data
public class RefundKartuTamuRequest {

    @NotNull(message = "ID kartu tamu wajib diisi")
    private Long kartuId;

    @NotBlank(message = "Nomor bukti refund wajib diisi (idempotency)")
    @Size(max = 60)
    private String referensiId;

    @Size(max = 255)
    private String catatan;
}
