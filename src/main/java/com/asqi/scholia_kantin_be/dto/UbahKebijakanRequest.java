package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.KebijakanSaldoMengendap;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Ubah kebijakan kantin (DEMO, OPEN-QUESTIONS Q16 / issue #25).
 */
@Data
public class UbahKebijakanRequest {

    /** Kebijakan saldo mengendap siswa lulus/keluar (REFUND/PINDAH_SAUDARA/TETAP_MENGENDAP). */
    @NotNull(message = "Kebijakan saldo mengendap wajib diisi")
    private KebijakanSaldoMengendap kebijakanSaldoMengendap;

    /** Ambang hari (mis. berapa hari setelah lulus saldo ditindaklanjuti). */
    @Min(value = 0, message = "Ambang hari tidak boleh negatif")
    private int ambangHari;

    @Size(max = 500, message = "Catatan maksimal 500 karakter")
    private String catatan;
}
