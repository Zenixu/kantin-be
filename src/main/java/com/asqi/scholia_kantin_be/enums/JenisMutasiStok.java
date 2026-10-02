package com.asqi.scholia_kantin_be.enums;

/**
 * Jenis (sebab) mutasi stok — kolom {@code mutasi_stok.jenis VARCHAR(40)}.
 *
 * <p>Daftar ini mengikuti komentar skema {@code V3__CreateLedgerStok.sql}.
 */
public enum JenisMutasiStok {
    /** Pembelian stok dari pemasok; menaikkan stok &amp; memperbarui HPP (PRD §7.2). */
    BARANG_MASUK,
    /** Stok berkurang karena terjual (PRD §6.2). */
    PENJUALAN,
    /** Stok kembali karena void transaksi (PRD §6.3). */
    VOID_PENJUALAN,
    /** Penyesuaian opname menambah stok (PRD §7.3). */
    OPNAME_MASUK,
    /** Penyesuaian opname mengurangi stok — rusak/hilang/kedaluwarsa (PRD §7.3). */
    OPNAME_KELUAR,
    /** Koreksi barang masuk yang salah input (PRD §7.2) — pembalik. */
    BARANG_MASUK_PEMBALIK
}
