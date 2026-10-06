package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * Badan request webhook masuk (SECURITY.md §5).
 *
 * <p>Bentuk generik karena kontrak tiap pengirim (SKOOLIA, callback-be) belum
 * final (OPEN-QUESTIONS Q4/Q7). Field yang dipakai untuk keamanan &amp;
 * idempotency:
 * <ul>
 *   <li>{@code eventId} — id unik event dari pengirim (kunci idempotency). Bila
 *       kosong, diambil dari header {@code X-Webhook-Id}.</li>
 *   <li>{@code eventType} — jenis event (mis. {@code TOPUP_ONLINE_SUKSES}).</li>
 *   <li>{@code sekolahId} — tenant terkait (bila ada).</li>
 *   <li>{@code data} — isi event apa adanya untuk handler.</li>
 * </ul>
 * Semua opsional di level bean validation; keharusan {@code eventId} ditegakkan
 * di service agar pesannya jelas (400).
 */
@Data
public class WebhookRequest {

    @Size(max = 128, message = "ID event maksimal 128 karakter")
    private String eventId;

    @Size(max = 80, message = "Jenis event maksimal 80 karakter")
    private String eventType;

    private Long sekolahId;

    /** Isi event mentah (struktur bergantung pengirim). */
    private Map<String, Object> data;
}
