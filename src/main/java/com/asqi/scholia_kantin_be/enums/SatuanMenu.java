package com.asqi.scholia_kantin_be.enums;

/**
 * Satuan item katalog (PRD §7.1: pcs/porsi/botol).
 *
 * <p>Disimpan sebagai VARCHAR(20) dengan CHECK constraint — jangan ubah nama
 * tanpa migrasi baru (CONVENTIONS.md §8).
 */
public enum SatuanMenu {
    /** Satuan buah/biji (mis. snack kemasan). */
    PCS,
    /** Satuan porsi (mis. nasi goreng). */
    PORSI,
    /** Satuan botol (mis. air mineral). */
    BOTOL
}
