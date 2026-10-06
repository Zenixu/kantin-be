package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Permintaan refund sisa saldo siswa keluar (PRD §9.3, issue #38).
 *
 * <p>Refund mengembalikan <b>seluruh</b> sisa saldo ke ortu (tunai/transfer).
 * {@code referensiId} = nomor bukti (idempotency) — retry/double-submit tidak
 * mengembalikan dua kali.
 */
@Data
public class RefundSaldoRequest {

    @NotNull(message = "ID siswa wajib diisi")
    private Long subjekId;

    @NotBlank(message = "Nomor bukti refund wajib diisi (idempotency)")
    @Size(max = 60)
    private String referensiId;

    @Size(max = 255)
    private String catatan;
}
