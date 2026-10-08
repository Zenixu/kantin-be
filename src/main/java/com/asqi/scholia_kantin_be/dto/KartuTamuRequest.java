package com.asqi.scholia_kantin_be.dto;

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
     *
     * <p><b>Opsional saat create</b> (PRD §9.4): bila kosong, sistem
     * <b>menggenerate</b> nomor berikutnya (KT- + urutan per sekolah). Boleh
     * diisi manual untuk override. Saat update, {@code null} = tidak diubah.
     */
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
     * Label pemegang kartu (PRD §9.4) — nama guru/staf, atau "Tamu".
     * Opsional. Saat update, kirim string kosong untuk <b>mengosongkan</b>
     * (mis. saat pengembalian kartu).
     */
    @Size(max = 150, message = "Label pemegang maksimal 150 karakter")
    private String labelPemegang;

    /**
     * Status aktif kartu.
     * Default true saat create.
     */
    private Boolean aktif;
}
