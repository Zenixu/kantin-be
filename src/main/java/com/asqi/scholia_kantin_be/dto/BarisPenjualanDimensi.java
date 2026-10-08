package com.asqi.scholia_kantin_be.dto;

/**
 * Baris laporan penjualan per <b>titik kasir</b> atau per <b>petugas</b>
 * (PRD §9.5, issue #116).
 *
 * <p>{@code kunciId} = {@code titikKasirId} atau {@code petugasId}.
 * {@code nama} diisi bila dapat diresolusi (nama titik kasir); untuk petugas
 * biasanya {@code null} (nama dari admin-be, belum tersedia di kantin-be).
 * {@code labaKotor} = {@code nilai} − {@code hpp}.
 */
public record BarisPenjualanDimensi(
        Long kunciId,
        String nama,
        long jumlahTransaksi,
        long nilai,
        long hpp,
        long labaKotor) {
}
