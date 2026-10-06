package com.asqi.scholia_kantin_be.enums;

/**
 * Tipe transaksi Buku Kas (sisi admin-be) — INTEGRATIONS.md §3.2.
 *
 * <p>Nilai <b>wajib sama</b> dengan enum {@code com.asqi.scholia_admin_be.helper.TipeTransaksi}
 * agar posting kantin tidak ditolak/dianggap asing oleh admin-be.
 */
public enum TipeBukuKas {
    /** Uang masuk (mis. pendapatan penjualan kantin). */
    MASUK,
    /** Uang keluar (mis. belanja stok kantin). */
    KELUAR
}
