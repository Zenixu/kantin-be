package com.asqi.scholia_kantin_be.enums;

/**
 * Status tap yang menunggu konfirmasi manual (PRD §6.1).
 *
 * <p>Kolom {@code transaksi_menunggu_konfirmasi.status VARCHAR(15)} dengan CHECK.
 */
public enum StatusPendingTap {
    /** Tap diterima, menunggu petugas menekan "Konfirmasi" (belum memotong apa pun). */
    MENUNGGU,
    /** Sudah dikonfirmasi → transaksi dibuat &amp; saldo/stok terpotong. */
    DIKONFIRMASI,
    /** Dibatalkan petugas (tidak jadi transaksi). */
    DIBATALKAN
}
