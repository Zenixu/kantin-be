package com.asqi.scholia_kantin_be.enums;

/**
 * Kebijakan saldo mengendap siswa lulus/keluar yang tak diklaim
 * (DEMO, OPEN-QUESTIONS <b>Q16</b> / issue #25).
 *
 * <p>Nilai disimpan sebagai string (kolom
 * {@code kebijakan_kantin.kebijakan_saldo_mengendap VARCHAR(30)}) dan dijaga
 * CHECK constraint di database. Jangan ubah nama tanpa migrasi baru
 * (CONVENTIONS.md §8).
 */
public enum KebijakanSaldoMengendap {
    /** Kembalikan seluruh sisa saldo ke orang tua (tunai/transfer). */
    REFUND,
    /** Pindahkan saldo ke saudara kandung yang masih aktif di sekolah yang sama. */
    PINDAH_SAUDARA,
    /** Dibiarkan mengendap (menjadi kewajiban sekolah) sampai ada klaim. */
    TETAP_MENGENDAP
}
