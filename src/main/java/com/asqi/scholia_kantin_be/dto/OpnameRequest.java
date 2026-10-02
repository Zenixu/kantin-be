package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Permintaan penyesuaian stok hasil <b>opname fisik</b> (PRD §7.3).
 *
 * <p>{@code qtyFisik} = stok hasil hitung ulang manual; server menghitung
 * selisih vs cache &amp; mencatatnya sebagai mutasi MASUK/KELUAR dengan
 * alasan wajib. HPP rata-rata tidak berubah.
 */
@Data
public class OpnameRequest {

    @NotNull(message = "ID menu wajib diisi")
    private Long menuId;

    @Min(value = 0, message = "Stok fisik tidak boleh negatif")
    private int qtyFisik;

    @NotBlank(message = "Alasan penyesuaian wajib diisi (PRD §7.3)")
    @Size(max = 255)
    private String alasan;

    @NotBlank(message = "Nomor berita acara opname wajib diisi")
    @Size(max = 60)
    private String referensiId;
}
