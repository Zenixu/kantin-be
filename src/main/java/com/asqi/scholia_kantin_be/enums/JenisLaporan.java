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
    BARANG_MASUK,
    /** Pembatalan kasir — transaksi di-void per subjek (PRD §9.5). */
    PEMBATALAN,
    /** Kartu Tamu — daftar kartu, pemegang, saldo, status (PRD §9.5). */
    KARTU_TAMU,
    /** Penjualan per titik kasir (PRD §9.5). */
    PENJUALAN_TITIK,
    /** Penjualan per petugas (PRD §9.5). */
    PENJUALAN_PETUGAS,
    /** Kartu stok per item — riwayat mutasi satu menu + saldo berjalan (PRD §9.5). */
    KARTU_STOK,
    /** Setoran kas TU — rekap top-up tunai per petugas per hari + selisih (PRD §9.5). */
    SETORAN_TU
}
