package com.asqi.scholia_kantin_be.enums;

/**
 * Status transaksi kasir — kolom {@code transaksi.status VARCHAR(10)}.
 *
 * <p>CHECK di database: {@code status IN ('SUKSES','VOID')}. Void wajib
 * beralasan ({@code alasan_void} &amp; {@code void_at} tidak null).
 */
public enum StatusTransaksi {
    /** Transaksi tercatat &amp; saldo/stok sudah dipotong. */
    SUKSES,
    /** Transaksi dibatalkan; saldo &amp; stok dikembalikan lewat mutasi pembalik. */
    VOID
}
