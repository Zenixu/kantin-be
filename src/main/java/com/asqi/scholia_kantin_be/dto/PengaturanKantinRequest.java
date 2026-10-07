package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;

/**
 * Ubah pengaturan kantin (PRD §9.1, issue #42).
 *
 * <p>Semua field opsional: {@code null} = pertahankan nilai lama. Batas
 * min/maks top-up &amp; batas saldo {@code null} = tanpa batas (PRD §8.2/§9.4).
 */
@Data
public class PengaturanKantinRequest {

    @Size(max = 150, message = "Nama kantin maksimal 150 karakter")
    private String namaKantin;

    /** Jam tutup kasir otomatis (zona sekolah) — dipakai penjadwal auto-close (§6.4). */
    private LocalTime jamTutupOtomatis;

    /** Langkah konfirmasi manual sebelum transaksi tercatat (default nonaktif, §6.1). */
    private Boolean konfirmasiManual;

    @Min(value = 0, message = "Durasi foto tidak boleh negatif")
    private Integer durasiFotoDetik;

    @Min(value = 0, message = "Minimum top-up tidak boleh negatif")
    private Long minTopup;

    @Min(value = 0, message = "Maksimum top-up tidak boleh negatif")
    private Long maksTopup;

    @Min(value = 0, message = "Batas saldo siswa tidak boleh negatif")
    private Long batasSaldoSiswa;

    @Min(value = 0, message = "Batas saldo Kartu Tamu tidak boleh negatif")
    private Long batasSaldoKartuTamu;
}
