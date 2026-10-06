package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.config.webhook.WebhookProperties;
import com.asqi.scholia_kantin_be.dto.WebhookHasil;
import com.asqi.scholia_kantin_be.dto.WebhookRequest;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.service.webhook.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Endpoint penerima webhook dari SKOOLIA (SECURITY.md §5, BUGS-DITEMUKAN B34).
 *
 * <p><b>Bukan</b> endpoint ber-token: autentikasinya adalah <b>verifikasi HMAC</b>
 * yang dijalankan {@code WebhookSignatureFilter} <i>sebelum</i> request sampai
 * sini. Controller ini menganggap request sudah terverifikasi (signature sah +
 * timestamp dalam jendela + IP diizinkan), lalu meneruskan ke
 * {@link WebhookService} yang menjaga <b>idempotency per event id</b>.
 *
 * <p>Badan request dibaca <b>mentah</b> di sini (bukan {@code @RequestBody}) agar
 * sidik jari payload yang di-hash persis sama dengan byte yang diverifikasi
 * filter — dasar deteksi event id sama dengan isi berbeda.
 */
@RestController
@RequestMapping("api/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;
    private final WebhookProperties properties;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * Terima event webhook dari {@code sumber} (mis. {@code SKOOLIA}).
     *
     * <p>ID event diambil dari header {@code X-Webhook-Id}, atau dari field
     * {@code eventId} di badan bila header kosong. Retry event yang sama
     * dijawab sukses tanpa diproses ulang (idempotency).
     */
    @PostMapping("{sumber}")
    public ResponseEntity<Response<WebhookHasil>> terima(
            @PathVariable String sumber,
            HttpServletRequest request) throws IOException {

        byte[] badan = bacaBadan(request);
        WebhookRequest body = parse(badan);

        String eventId = headerAtauBody(request.getHeader(properties.getHeaderEventId()),
                body == null ? null : body.getEventId());
        String eventType = body == null ? null : body.getEventType();
        Long sekolahId = body == null ? null : body.getSekolahId();
        var data = body == null ? null : body.getData();

        WebhookHasil hasil = webhookService.terima(
                sumber.toUpperCase(), eventId, eventType, sekolahId, data,
                WebhookService.hashPayload(badan));

        String pesan = hasil.isReplay()
                ? "Event sudah pernah diterima (tidak diproses ulang)"
                : "Event diterima";
        return CommonResponse.data(hasil, pesan);
    }

    /** Baca seluruh badan request sebagai byte mentah. */
    private byte[] bacaBadan(HttpServletRequest request) throws IOException {
        try (InputStream in = request.getInputStream()) {
            return in.readAllBytes();
        }
    }

    /** Parse badan JSON; {@code null} bila kosong (event boleh tanpa body). */
    private WebhookRequest parse(byte[] badan) {
        if (badan == null || badan.length == 0) {
            return null;
        }
        String teks = new String(badan, StandardCharsets.UTF_8).trim();
        if (teks.isEmpty()) {
            return null;
        }
        try {
            return jsonMapper.readValue(badan, WebhookRequest.class);
        } catch (RuntimeException e) {
            throw new InvalidOperationException("Badan webhook bukan JSON yang valid");
        }
    }

    private String headerAtauBody(String header, String body) {
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        return body;
    }
}
