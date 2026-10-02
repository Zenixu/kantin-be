package com.asqi.scholia_kantin_be.enums;

/**
 * Arah mutasi saldo pada {@code saldo_ledger}.
 *
 * <p>Nilai disimpan sebagai string (kolom {@code arah VARCHAR(10)}) dan dijaga
 * CHECK constraint di database. Jangan ubah nama tanpa migrasi baru
 * (CONVENTIONS.md §8).
 */
public enum ArahMutasi {
    /** Saldo bertambah (top-up, refund, void penjualan, koreksi masuk). */
    KREDIT,
    /** Saldo berkurang (penjualan, koreksi keluar, refund keluar). */
    DEBIT
}
