package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Respons saldo pemilik kartu (PRD §9): saldo berjalan + ringkasan belanja
 * hari ini + mutasi terbaru (untuk halaman riwayat TU/ortu).
 */
@Data
@Builder
public class SaldoResponse {

    private SubjekTipe subjekTipe;
    private Long subjekId;

    /** Saldo berjalan (rupiah). */
    private long saldo;

    /** Total belanja hari ini (rupiah) — dipakai cek limit harian. */
    private long belanjaHariIni;

    /** Mutasi terbaru (maks. {@code batas} entri terakhir). */
    private List<MutasiSaldoItem> mutasiTerbaru;
}
