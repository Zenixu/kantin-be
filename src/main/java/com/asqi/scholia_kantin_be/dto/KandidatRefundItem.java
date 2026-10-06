package com.asqi.scholia_kantin_be.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Satu baris daftar kandidat refund/pindah saldo (PRD §9.3, issue #38).
 *
 * <p>Berasal dari saldo berjalan ({@code saldo_cache}) subjek SISWA yang masih
 * bersisa, diperkaya status keaktifan dari admin-be (port
 * {@code StatusSiswaPort}).
 */
@Data
@Builder
public class KandidatRefundItem {

    private Long subjekId;

    /** Saldo berjalan subjek (rupiah). */
    private long saldo;

    /**
     * Status keaktifan siswa: {@code TRUE} nonaktif (lulus/pindah/keluar),
     * {@code FALSE} aktif, {@code null} tidak diketahui (integrasi Q7 belum
     * siap).
     */
    private Boolean tidakAktif;
}
