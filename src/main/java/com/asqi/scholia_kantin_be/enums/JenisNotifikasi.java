package com.asqi.scholia_kantin_be.enums;

/**
 * Jenis notifikasi ke orang tua (PRD §8.4).
 *
 * <p>Dipakai sebagai penanda pada {@code PerintahNotifikasi} agar pengirim
 * (mobile-be) dapat memilih template/kanal yang tepat. Nilai mengikuti daftar
 * peristiwa PRD §8.4: belanja, void, top-up online, top-up tunai (TU),
 * refund, dan pindah saldo.
 */
public enum JenisNotifikasi {
    /** Belanja di kasir (contoh: "Budi belanja Rp8.000 di Kantin: ..."). */
    BELANJA,
    /** Transaksi dibatalkan (void). */
    VOID,
    /** Top-up online berhasil (via payment gateway). */
    TOPUP_ONLINE,
    /** Top-up tunai oleh TU/bendahara. */
    TOPUP_TUNAI,
    /** Refund sisa saldo siswa keluar. */
    REFUND,
    /** Pemindahan saldo ke saudara kandung. */
    PINDAH_SALDO
}
