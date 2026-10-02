package com.asqi.scholia_kantin_be.enums;

/**
 * Arah mutasi stok pada {@code mutasi_stok}.
 *
 * <p>Nilai disimpan sebagai string (kolom {@code arah VARCHAR(10)}) dan dijaga
 * CHECK constraint di database.
 */
public enum ArahStok {
    /** Stok bertambah (barang masuk, void penjualan, opname masuk). */
    MASUK,
    /** Stok berkurang (penjualan, opname keluar, barang masuk pembalik). */
    KELUAR
}
