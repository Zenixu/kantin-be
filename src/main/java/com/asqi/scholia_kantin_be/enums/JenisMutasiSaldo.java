package com.asqi.scholia_kantin_be.enums;

/**
 * Jenis (sebab) mutasi saldo — kolom {@code saldo_ledger.jenis VARCHAR(40)}.
 *
 * <p>Kolom ini <b>tanpa</b> CHECK di database sehingga penambahan nilai baru
 * aman (tidak butuh migrasi). Tetap jaga konsistensi penamaan.
 */
public enum JenisMutasiSaldo {
    /** Top-up tunai di TU / bendahara (PRD §9.2). */
    TOPUP_TUNAI,
    /** Top-up online via payment gateway (PRD §8.2). */
    TOPUP_ONLINE,
    /** Potongan saldo karena penjualan di kasir (PRD §6.2). */
    PENJUALAN,
    /** Pengembalian saldo karena void transaksi (PRD §6.3). */
    VOID_PENJUALAN,
    /** Koreksi bendahara — mutasi pembalik (PRD §9.2). */
    KOREKSI,
    /** Refund sisa saldo saat siswa keluar / Kartu Tamu dikembalikan (PRD §9.3, §9.4). */
    REFUND,
    /** Pemindahan saldo ke saudara kandung (PRD §9.3). */
    TRANSFER,
    /** Penyesuaian lain yang beralasan (mis. koreksi saldo awal). */
    PENYESUAIAN
}
