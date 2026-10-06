package com.asqi.scholia_kantin_be.service.webhook;

/**
 * Port penangan satu jenis event webhook (SECURITY.md §5).
 *
 * <p><b>Kenapa port (interface)?</b> Kontrak payload tiap pengirim
 * (callback-be untuk top-up online, admin-be untuk sinkronisasi kartu) belum
 * final (OPEN-QUESTIONS Q4/Q7). Dengan port ini, pipa keamanan webhook
 * (verifikasi signature, anti-replay, idempotency) bisa dibangun, diuji, dan
 * <b>tidak terblokir</b>; begitu kontrak terjawab, cukup tambah implementasi
 * (mis. {@code TopUpOnlineWebhookHandler}) tanpa menyentuh keamanan.
 *
 * <p><b>Kontrak penting:</b> handler dipanggil <b>di dalam transaksi</b> yang
 * juga mencatat idempotency event. Bila handler melempar exception, transaksi
 * di-rollback sehingga efek tidak setengah jalan dan event bisa dicoba ulang.
 */
public interface WebhookHandlerPort {

    /** Apakah handler ini menangani {@code eventType} (mis. {@code TOPUP_ONLINE_SUKSES}). */
    boolean mendukung(String eventType);

    /** Proses event. Dipanggil sekali per (sumber, eventId) — idempotency sudah dijaga. */
    void tangani(KonteksWebhook konteks);
}
