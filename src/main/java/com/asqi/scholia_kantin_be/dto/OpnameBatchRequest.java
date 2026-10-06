package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Permintaan <b>opname batch</b> (PRD §7.3) — menyesuaikan banyak menu sekaligus
 * dalam <b>satu</b> transaksi (all-or-nothing), agar audit puluhan menu cukup
 * satu request.
 *
 * <p>{@code referensiId} = nomor berita acara opname (mis.
 * {@code OPN-20261006-001}); dipakai sebagai idempotency key untuk seluruh batch
 * — retry dengan nomor sama tidak menerapkan penyesuaian dua kali.
 */
@Data
public class OpnameBatchRequest {

    @NotBlank(message = "Nomor berita acara opname wajib diisi (idempotency)")
    @Size(max = 60, message = "Nomor berita acara maksimal 60 karakter")
    private String referensiId;

    @NotEmpty(message = "Minimal satu item opname harus dikirim")
    @Valid
    private List<OpnameBatchItemRequest> items;
}
