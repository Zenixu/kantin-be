package com.asqi.scholia_kantin_be.enums;

/**
 * Metode pembayaran Buku Kas (sisi admin-be) — INTEGRATIONS.md §3.2.
 *
 * <p>Nilai string <b>wajib sama</b> dengan enum
 * {@code com.asqi.scholia_admin_be.helper.MetodePembayaran}:
 * {@code TUNAI("1")}, {@code NON_TUNAI("2")}, {@code DANA_BOS("3")}.
 *
 * <p>Posting pendapatan kantin memakai {@link #NON_TUNAI} (saldo/kartu, bukan
 * uang fisik) — lihat pemetaan INTEGRATIONS.md §3.3.
 */
public enum MetodeBukuKas {
    /** Tunai (uang fisik). */
    TUNAI("1"),
    /** Non-tunai (saldo kartu/kantin). */
    NON_TUNAI("2"),
    /** Dana BOS. */
    DANA_BOS("3");

    private final String kode;

    MetodeBukuKas(String kode) {
        this.kode = kode;
    }

    /** Kode yang dikirim ke Buku Kas admin-be (mis. {@code "2"}). */
    public String kode() {
        return kode;
    }
}
