package com.asqi.scholia_kantin_be.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Satu baris laporan <b>Kartu Tamu</b> (PRD §9.5, issue #115): daftar kartu +
 * pemegang + saldo + status. Riwayat per kartu disediakan terpisah lewat
 * {@code GET /api/kartu-tamu/{kartuId}/riwayat} (issue #122).
 */
@Data
@Builder
public class BarisKartuTamu {

    private Long kartuId;
    private String nomorKartu;
    private String labelPemegang;
    private String rfidUid;
    private boolean aktif;

    /** Saldo berjalan kartu (rupiah). */
    private long saldo;
}
