package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Permintaan barang masuk / stok awal (PRD §7.2).
 *
 * <p>{@code referensiId} = nomor bukti penerimaan (mis.
 * {@code BM-2026-0001}) — idempotency key agar input ulang tidak
 * menggandakan stok.
 */
@Data
public class BarangMasukRequest {

    @NotNull(message = "ID menu wajib diisi")
    private Long menuId;

    @Min(value = 1, message = "Qty barang masuk harus > 0")
    private int qty;

    @Min(value = 0, message = "Harga beli tidak boleh negatif")
    private long hargaBeliPerUnit;

    @NotBlank(message = "Nomor bukti barang masuk wajib diisi")
    @Size(max = 60)
    private String referensiId;
}
