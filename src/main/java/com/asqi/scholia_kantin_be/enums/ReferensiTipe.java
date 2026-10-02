package com.asqi.scholia_kantin_be.enums;

/**
 * Tipe sumber sebuah mutasi (kolom {@code referensi_tipe} pada
 * {@code saldo_ledger} &amp; {@code mutasi_stok}).
 *
 * <p>Dipakai untuk menautkan mutasi ke dokumen asalnya (transaksi, barang
 * masuk, opname, sesi, top-up) sehingga rekonsiliasi &amp; audit mudah.
 */
public enum ReferensiTipe {
    /** Mutasi berasal dari sebuah transaksi kasir. */
    TRANSAKSI,
    /** Mutasi berasal dari top-up (tunai/online). */
    TOPUP,
    /** Mutasi berasal dari sesi kasir (mis. posting/rekap). */
    SESI_KASIR,
    /** Mutasi berasal dari dokumen barang masuk. */
    BARANG_MASUK,
    /** Mutasi berasal dari opname stok. */
    OPNAME,
    /** Mutasi koreksi bendahara. */
    KOREKSI
}
