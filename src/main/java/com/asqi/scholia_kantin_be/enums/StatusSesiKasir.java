package com.asqi.scholia_kantin_be.enums;

/**
 * Status sesi kasir — kolom {@code sesi_kasir.status VARCHAR(15)}.
 *
 * <p>CHECK di database: {@code status IN ('TERBUKA','DITUTUP')}.
 */
public enum StatusSesiKasir {
    /** Sesi masih menerima transaksi &amp; void. */
    TERBUKA,
    /** Sesi sudah ditutup; transaksi terkunci, total bersih siap diposting ke Buku Kas. */
    DITUTUP
}
