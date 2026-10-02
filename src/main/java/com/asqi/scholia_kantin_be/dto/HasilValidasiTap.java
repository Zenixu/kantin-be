package com.asqi.scholia_kantin_be.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Hasil validasi 6 tahap sebelum tap dieksekusi (PRD §6.1).
 *
 * <p>Dipakai {@code TapValidator} agar urutan pemeriksaan <b>eksplisit</b> dan
 * mudah diuji. Urutan wajib: kartu dikenal → tidak diblokir → item tidak
 * diblokir ortu → stok cukup → limit harian → saldo cukup.
 */
@Getter
@Builder
public class HasilValidasiTap {

    private boolean valid;

    /** Pesan gagal yang ditampilkan ke kasir (null bila valid). */
    private String pesan;

    /** Kode tahap yang gagal (1–6) untuk audit/telemetri. */
    private Integer tahapGagal;

    public static HasilValidasiTap lolos() {
        return HasilValidasiTap.builder().valid(true).build();
    }

    public static HasilValidasiTap gagal(int tahap, String pesan) {
        return HasilValidasiTap.builder().valid(false).tahapGagal(tahap).pesan(pesan).build();
    }
}
