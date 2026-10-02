package com.asqi.scholia_kantin_be.enums;

/**
 * Jenis pemilik saldo di ledger kantin (lihat skema {@code saldo_ledger}).
 */
public enum SubjekTipe {
    /** Siswa (identitas dari admin-be). */
    SISWA,
    /** Kartu Tamu — guru/karyawan/tamu tanpa akun siswa (PRD §9.4). */
    KARTU_TAMU
}
