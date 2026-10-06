package com.asqi.scholia_kantin_be.service.webhook;

import com.asqi.scholia_kantin_be.component.exception.IdempotencyConflictException;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.config.webhook.TandaTanganWebhook;
import com.asqi.scholia_kantin_be.dto.WebhookHasil;
import com.asqi.scholia_kantin_be.enums.StatusWebhook;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.WebhookEvent;
import com.asqi.scholia_kantin_be.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Penerimaan event webhook dengan <b>idempotency per (sumber, eventId)</b>
 * (SECURITY.md §5, PRD §11.3, BUGS-DITEMUKAN B34).
 *
 * <p>Webhook sering di-retry oleh pengirim (jaringan timeout, ack hilang).
 * Service ini memastikan event yang sama <b>tidak diproses dua kali</b>:
 * <ol>
 *   <li>Event sudah ada dengan payload <b>sama</b> → kembalikan hasil lama
 *       ({@code replay=true}), handler <b>tidak</b> dijalankan lagi.</li>
 *   <li>Event sudah ada dengan payload <b>berbeda</b> → {@code 409}
 *       ({@link IdempotencyConflictException}) — indikasi penyalahgunaan/bug.</li>
 *   <li>Event baru → jalankan handler, catat baris jurnal. Balapan dua request
 *       dengan eventId sama dijaga UNIQUE DB: yang kalah membaca ulang &amp;
 *       menjawab sebagai replay (bukan error).</li>
 * </ol>
 *
 * <p>Semua di dalam satu transaksi: bila handler gagal, baris jurnal ikut
 * di-rollback sehingga event bisa dicoba ulang dan tidak ada efek setengah jalan.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final WebhookEventRepository eventRepo;
    private final IdGenerator idGenerator;
    private final JamKantin jam;

    /**
     * Handler event (boleh <b>kosong</b> untuk sekarang — kontrak payload
     * callback-be/admin-be belum final, Q4/Q7). Dengan {@link ObjectProvider},
     * pipa keamanan webhook tetap bisa dibangun &amp; diuji walau belum ada
     * handler; event yang jenisnya belum ditangani tetap dicatat sebagai
     * {@link StatusWebhook#DIABAIKAN}.
     */
    private final ObjectProvider<WebhookHandlerPort> handlersProvider;

    /**
     * Terima satu event webhook (sudah lolos verifikasi signature).
     *
     * @param sumber      sistem pengirim (mis. {@code SKOOLIA})
     * @param eventId     id unik event (kunci idempotency; wajib)
     * @param eventType   jenis event (boleh {@code null})
     * @param sekolahId   tenant terkait (boleh {@code null})
     * @param data        isi event
     * @param payloadHash SHA-256 hex badan request
     */
    @Transactional
    public WebhookHasil terima(String sumber, String eventId, String eventType,
                               Long sekolahId, Map<String, Object> data, String payloadHash) {
        if (sumber == null || sumber.isBlank()) {
            throw new InvalidOperationException("Sumber webhook wajib diisi");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new InvalidOperationException(
                    "ID event webhook wajib diisi (header X-Webhook-Id atau field eventId)");
        }
        String eventIdBersih = eventId.trim();

        // 1) Replay / konflik payload — dicek lebih dulu.
        Optional<WebhookEvent> lama = eventRepo.findBySumberAndEventId(sumber, eventIdBersih);
        if (lama.isPresent()) {
            return tanganiSudahAda(lama.get(), payloadHash);
        }

        // 2) Event baru: jalankan handler yang mendukung jenis ini.
        KonteksWebhook konteks = new KonteksWebhook(sumber, eventIdBersih, eventType, sekolahId, data);
        WebhookHandlerPort handler = handlersProvider.orderedStream()
                .filter(h -> h.mendukung(eventType))
                .findFirst()
                .orElse(null);

        StatusWebhook status;
        String keterangan;
        if (handler == null) {
            status = StatusWebhook.DIABAIKAN;
            keterangan = "Jenis event belum ditangani: " + eventType;
            log.info("Webhook {} event={} diabaikan (jenis belum ditangani).",
                    sumber, eventIdBersih);
        } else {
            handler.tangani(konteks);
            status = StatusWebhook.DIPROSES;
            keterangan = "Ditangani";
        }

        OffsetDateTime now = jam.sekarang();
        WebhookEvent baris = WebhookEvent.builder()
                .id(idGenerator.berikutnyaLong())
                .sumber(sumber)
                .eventId(eventIdBersih)
                .eventType(eventType)
                .payloadHash(payloadHash)
                .sekolahId(sekolahId)
                .status(status)
                .keterangan(keterangan)
                .waktu(now)
                .createdAt(now)
                .build();

        try {
            eventRepo.save(baris);
        } catch (DataIntegrityViolationException e) {
            // Balapan: request lain mencatat (sumber, eventId) yang sama nyaris
            // bersamaan. Yang kalah memakai baris pemenang → jawab sebagai replay
            // (idempotent), bukan error.
            WebhookEvent pemenang = eventRepo.findBySumberAndEventId(sumber, eventIdBersih)
                    .orElseThrow(() -> e);
            return tanganiSudahAda(pemenang, payloadHash);
        }

        return WebhookHasil.dari(baris, false);
    }

    /** Bila event sudah ada: replay bila payload sama, konflik bila berbeda. */
    private WebhookHasil tanganiSudahAda(WebhookEvent ada, String payloadHash) {
        if (ada.getPayloadHash() != null && payloadHash != null
                && !ada.getPayloadHash().equals(payloadHash)) {
            throw new IdempotencyConflictException(
                    "Event webhook " + ada.getEventId()
                            + " sudah pernah diterima dengan isi berbeda");
        }
        log.info("Webhook replay (sumber={}, event={}) — tidak diproses ulang.",
                ada.getSumber(), ada.getEventId());
        return WebhookHasil.dari(ada, true);
    }

    /** Sidik jari payload untuk mendeteksi event id sama dengan isi berbeda. */
    public static String hashPayload(byte[] badan) {
        return TandaTanganWebhook.hashPayload(badan);
    }
}
