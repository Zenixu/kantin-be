package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Permintaan top-up tunai saldo (PRD §9.1).
 *
 * <p>{@code referensiId} = nomor bukti/kuitansi <b>unik</b> dari TU; dipakai
 * sebagai idempotency key — menyimpan bukti yang sama dua kali tidak menambah
 * saldo dua kali (Aturan Emas §3.3).
 */
@Data
public class TopUpRequest {

    @NotNull(message = "Tipe subjek wajib diisi (SISWA/KARTU_TAMU)")
    private SubjekTipe subjekTipe;

    @NotNull(message = "ID subjek wajib diisi")
    private Long subjekId;

    @Min(value = 1, message = "Nominal top-up harus > 0")
    private long nominal;

    @Size(max = 120, message = "Nama penyetor maksimal 120 karakter")
    private String penyetor;

    @NotBlank(message = "Nomor referensi/bukti wajib diisi")
    @Size(max = 60, message = "Nomor referensi maksimal 60 karakter")
    private String referensiId;
}
