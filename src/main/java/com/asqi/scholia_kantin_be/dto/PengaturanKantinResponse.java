package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.model.SekolahKantinConfig;
import lombok.Builder;
import lombok.Data;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Pengaturan kantin per sekolah (PRD §9.1, issue #42).
 *
 * <p>{@code disimpan=false} berarti sekolah belum pernah menyimpan pengaturan;
 * nilai yang ditampilkan adalah <b>default aman</b> (PRD §6.1/§6.4).
 */
@Data
@Builder
public class PengaturanKantinResponse {

    private Long sekolahId;
    private String namaKantin;

    /** Jam tutup kasir otomatis (zona sekolah). */
    private LocalTime jamTutupOtomatis;

    private boolean konfirmasiManual;
    private int durasiFotoDetik;

    /** Minimum per top-up; {@code null} = tanpa batas. */
    private Long minTopup;
    /** Maksimum per top-up; {@code null} = tanpa batas. */
    private Long maksTopup;
    /** Batas saldo maksimum per siswa; {@code null} = tanpa batas. */
    private Long batasSaldoSiswa;
    /** Batas saldo maksimum per Kartu Tamu; {@code null} = tanpa batas. */
    private Long batasSaldoKartuTamu;

    /** Apakah baris pengaturan sudah benar-benar tersimpan (bukan default). */
    private boolean disimpan;

    private OffsetDateTime updatedAt;

    /** Proyeksi entitas → DTO (baris tersimpan). */
    public static PengaturanKantinResponse dari(SekolahKantinConfig c) {
        return PengaturanKantinResponse.builder()
                .sekolahId(c.getSekolahId())
                .namaKantin(c.getNamaKantin())
                .jamTutupOtomatis(c.getJamTutupOtomatis())
                .konfirmasiManual(c.isKonfirmasiManual())
                .durasiFotoDetik(c.getDurasiFotoDetik())
                .minTopup(c.getMinTopup())
                .maksTopup(c.getMaksTopup())
                .batasSaldoSiswa(c.getBatasSaldoSiswa())
                .batasSaldoKartuTamu(c.getBatasSaldoKartuTamu())
                .disimpan(true)
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
