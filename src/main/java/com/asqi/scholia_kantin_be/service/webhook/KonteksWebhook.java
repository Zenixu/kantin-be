package com.asqi.scholia_kantin_be.service.webhook;

import com.asqi.scholia_kantin_be.enums.StatusWebhook;

import java.util.Map;

/**
 * Konteks satu event webhook yang sudah <b>terverifikasi signature</b>-nya,
 * diteruskan ke handler (SECURITY.md §5).
 *
 * <p>Objek nilai immutable agar handler tidak bisa memodifikasi data yang
 * dipakai untuk audit/idempotency.
 */
public record KonteksWebhook(
        String sumber,
        String eventId,
        String eventType,
        Long sekolahId,
        Map<String, Object> data
) {
    /** Status yang dicatat bila handler ini menangani event. */
    public StatusWebhook statusDefault() {
        return StatusWebhook.DIPROSES;
    }
}
