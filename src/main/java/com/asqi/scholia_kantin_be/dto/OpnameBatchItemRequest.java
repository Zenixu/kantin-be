package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Satu baris item dalam {@link OpnameBatchRequest} (PRD §7.3).
 *
 * <p>{@code qtyFisik} = hasil hitung fisik menu ini; server menghitung selisih
 * vs stok sistem.
 *
 * <p>{@code rusak} = {@code true} bila selisih <b>kurang</b> ini karena
 * barang <b>rusak/basi/kedaluwarsa</b> (bukan selisih audit) — maka dicatat
 * berjenis {@code BARANG_RUSAK} agar riwayat kerugian harian bisa difilter
 * terpisah dari selisih opname berkala. Default {@code false}.
 */
@Data
public class OpnameBatchItemRequest {

    @NotNull(message = "ID menu wajib diisi")
    private Long menuId;

    @Min(value = 0, message = "Stok fisik tidak boleh negatif")
    private int qtyFisik;

    @NotBlank(message = "Alasan penyesuaian wajib diisi (PRD §7.3)")
    @Size(max = 255, message = "Alasan maksimal 255 karakter")
    private String alasan;

    /** Bila {@code true} dan stok berkurang → jenis {@code BARANG_RUSAK}. */
    private Boolean rusak;
}
