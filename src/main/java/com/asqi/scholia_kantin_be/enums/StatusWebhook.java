package com.asqi.scholia_kantin_be.enums;

/**
 * Status penanganan sebuah event webhook yang sudah diterima &amp; terverifikasi
 * (lihat tabel {@code webhook_event}, SECURITY.md §5).
 */
public enum StatusWebhook {
    /** Event sah dan ditangani (ada handler-nya). */
    DIPROSES,
    /** Event sah tetapi jenisnya belum dikenal — dicatat, tidak berefek. */
    DIABAIKAN
}
