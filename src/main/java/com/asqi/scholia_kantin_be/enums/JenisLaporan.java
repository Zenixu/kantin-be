package com.asqi.scholia_kantin_be.enums;

/**
 * Jenis laporan yang dapat diekspor ke Excel (PRD §9.5).
 *
 * <p>Dipakai sebagai parameter endpoint ekspor {@code GET /api/laporan/ekspor}
 * agar satu endpoint melayani semua laporan.
 */
public enum JenisLaporan {
    /** Ringkasan penjualan &amp; laba kotor satu periode. */
    PENJUALAN,
    /** Penjualan per item (top-N). */
    PENJUALAN_ITEM,
    /** Penjualan per kategori. */
    PENJUALAN_KATEGORI,
    /** Saldo mengendap (dana titipan). */
    SALDO_MENGENDAP,
    /** Rekonsiliasi saldo + invariant. */
    REKONSILIASI,
    /** Stok sekarang + nilai persediaan. */
    STOK,
    /** Kerugian stok (opname keluar &amp; barang rusak). */
    KERUGIAN_STOK,
    /** Barang masuk per periode. */
    BARANG_MASUK
}
