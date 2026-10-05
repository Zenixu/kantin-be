package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request untuk membuat/update kartu tamu.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KartuTamuRequest {

    /**
     * Nomor kartu human-readable (KT-001, KT-002, dll).
     * Wajib saat create, opsional saat update (null = tidak diubah).
     */
    @NotBlank(message = "Nomor kartu wajib diisi")
    @Size(max = 20, message = "Nomor kartu maksimal 20 karakter")
    private String nomorKartu;

    /**
     * UID RFID kartu fisik (nullable jika belum di-bind).
     * Opsional saat create/update.
     */
    @Size(max = 50, message = "RFID UID maksimal 50 karakter")
    private String rfidUid;

    /**
     * Catatan bebas (untuk siapa, keperluan apa, dll).
     * Opsional.
     */
    private String catatan;

    /**
     * Status aktif kartu.
     * Default true saat create.
     */
    private Boolean aktif;
}
