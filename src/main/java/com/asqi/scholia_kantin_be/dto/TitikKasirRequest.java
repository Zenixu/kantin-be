package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Request buat/ubah titik kasir (PRD §9.1, issue #42). */
@Data
public class TitikKasirRequest {

    @NotBlank(message = "Nama titik kasir wajib diisi")
    @Size(max = 100, message = "Nama titik kasir maksimal 100 karakter")
    private String nama;

    /** Kode unik per sekolah (opsional). */
    @Size(max = 30, message = "Kode maksimal 30 karakter")
    private String kode;

    /** Status aktif (opsional; {@code null} = pertahankan). */
    private Boolean aktif;
}
