package com.asqi.scholia_kantin_be.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Satu baris rekap setoran TU harian per petugas (PRD §9.2, issue #39).
 *
 * <p>Menampilkan total top-up tunai petugas pada tanggal tsb, uang yang sudah
 * dikonfirmasi disetor (bila ada), dan selisihnya. Bendahara memakai ini untuk
 * memutuskan konfirmasi setoran.
 */
@Data
@Builder
public class RekapSetoranTuItem {

    private Long petugasId;

    /** Tanggal rekap (zona waktu sekolah). */
    private LocalDate tanggal;

    /** Σ top-up tunai petugas hari itu (dari ledger). */
    private long totalTopup;

    /** Uang yang sudah dikonfirmasi disetor (0 bila belum ada). */
    private long jumlahDisetor;

    /** {@code totalTopup − jumlahDisetor}. Positif = kurang setor. */
    private long selisih;

    /** Berita acara setoran (bila sudah dikonfirmasi). */
    private String referensiId;
}
