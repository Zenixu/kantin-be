package com.asqi.scholia_kantin_be.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Riwayat lengkap satu Kartu Tamu (PRD §9.4/§9.5, issue #122): identitas kartu,
 * saldo berjalan, daftar transaksi, dan mutasi saldo.
 */
@Data
@Builder
public class RiwayatKartuTamuResponse {

    /** Identitas kartu. */
    private Long kartuId;
    private String nomorKartu;
    private String labelPemegang;
    private boolean aktif;

    /** Saldo berjalan kartu (rupiah). */
    private long saldo;

    /** Transaksi berhalaman (terbaru dulu). */
    private HalamanResponse<RiwayatTransaksiKartuItem> transaksi;

    /** Mutasi saldo terbaru (maks {@code batasMutasi}). */
    private List<MutasiSaldoItem> mutasi;
}
