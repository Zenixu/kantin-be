package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Permintaan konfirmasi setoran kas TU oleh bendahara (PRD §9.2, issue #39).
 *
 * <p>Bendahara mengonfirmasi bahwa petugas TU menyetor uang sejumlah
 * {@code jumlahDisetor} untuk rekap top-up tunai tanggal {@code tanggal}.
 * {@code referensiId} = nomor berita acara <b>unik</b> (idempotency) sehingga
 * double-submit tidak mencatat setoran dua kali.
 */
@Data
public class KonfirmasiSetoranRequest {

    @NotNull(message = "Tanggal rekap setoran wajib diisi")
    private java.time.LocalDate tanggal;

    @NotNull(message = "ID petugas TU wajib diisi")
    private Long petugasId;

    @Min(value = 0, message = "Jumlah disetor tidak boleh negatif")
    private long jumlahDisetor;

    @NotBlank(message = "Nomor berita acara setoran wajib diisi (idempotency)")
    @Size(max = 60, message = "Nomor referensi maksimal 60 karakter")
    private String referensiId;

    @Size(max = 500, message = "Catatan maksimal 500 karakter")
    private String catatan;
}
