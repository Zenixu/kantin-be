package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.SatuanMenu;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Request buat/ubah item menu (PRD §7.1). */
@Data
public class MenuRequest {

    @NotBlank(message = "Nama menu wajib diisi")
    @Size(max = 150, message = "Nama menu maksimal 150 karakter")
    private String nama;

    @NotNull(message = "Harga jual wajib diisi")
    @Min(value = 0, message = "Harga jual tidak boleh negatif")
    private Long hargaJual;

    /** Kategori item (opsional). */
    private Long kategoriId;

    /** Satuan (PCS/PORSI/BOTOL); default PCS bila kosong. */
    private SatuanMenu satuan;

    @Size(max = 500, message = "URL foto maksimal 500 karakter")
    private String fotoUrl;

    @Min(value = 0, message = "Stok minimum tidak boleh negatif")
    private Integer stokMinimum;
}
