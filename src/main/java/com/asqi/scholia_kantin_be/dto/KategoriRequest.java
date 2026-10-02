package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Request buat/ubah kategori katalog (PRD §7.1). */
@Data
public class KategoriRequest {

    @NotBlank(message = "Nama kategori wajib diisi")
    @Size(max = 100, message = "Nama kategori maksimal 100 karakter")
    private String nama;

    /** Urutan tampil di kasir (opsional, default 0). */
    private Integer urutan;
}
