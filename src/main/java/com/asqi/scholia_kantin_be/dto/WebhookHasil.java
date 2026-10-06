package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.StatusWebhook;
import lombok.Builder;
import lombok.Data;

/**
 * Hasil penerimaan sebuah event webhook (SECURITY.md §5).
 *
 * <p>{@code replay=true} berarti event sudah pernah diterima dengan payload
 * sama dan <b>tidak diproses ulang</b> (idempotency, PRD §11.3). Pengirim yang
 * me-retry karena timeout tetap mendapat jawaban sukses sehingga berhenti
 * mencoba, tanpa efek ganda.
 */
@Data
@Builder
public class WebhookHasil {

    private String sumber;
    private String eventId;
    private String eventType;

    /** {@code DIPROSES} bila ada handler; {@code DIABAIKAN} bila jenis tak dikenal. */
    private StatusWebhook status;

    /** {@code true} bila ini pengulangan event yang sudah pernah diterima. */
    private boolean replay;

    private String keterangan;

    public static WebhookHasil dari(com.asqi.scholia_kantin_be.model.WebhookEvent e, boolean replay) {
        return WebhookHasil.builder()
                .sumber(e.getSumber())
                .eventId(e.getEventId())
                .eventType(e.getEventType())
                .status(e.getStatus())
                .replay(replay)
                .keterangan(e.getKeterangan())
                .build();
    }
}
