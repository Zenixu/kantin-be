package com.asqi.scholia_kantin_be.model;

import com.asqi.scholia_kantin_be.enums.StatusWebhook;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;

/**
 * Jurnal event webhook yang sudah diterima &amp; terverifikasi
 * (SECURITY.md §5, BUGS-DITEMUKAN B34).
 *
 * <p><b>Append-only:</b> {@link Immutable} — baris hanya ditulis sekali saat
 * event pertama diterima. Retry event yang sama <b>tidak</b> menulis baris baru
 * (dijaga UNIQUE {@code (sumber, event_id)}), sehingga efek tidak digandakan
 * (PRD §11.3).
 */
@Entity
@Table(name = "webhook_event")
@Immutable
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookEvent implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    /** Sistem pengirim, mis. {@code SKOOLIA} atau {@code CALLBACK_BE}. */
    @Column(name = "sumber", nullable = false, length = 40)
    private String sumber;

    /** ID event unik dari pengirim (header {@code X-Webhook-Id} atau body). */
    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    /** Jenis event, mis. {@code TOPUP_ONLINE_SUKSES}. */
    @Column(name = "event_type", length = 80)
    private String eventType;

    /** SHA-256 hex badan request — deteksi event_id sama & payload berbeda. */
    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    /** Tenant (bila event memuatnya); nullable karena sebagian event lintas-sekolah. */
    @Column(name = "sekolah_id")
    private Long sekolahId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusWebhook status;

    @Column(name = "keterangan", length = 255)
    private String keterangan;

    @Column(name = "waktu", nullable = false)
    private OffsetDateTime waktu;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void isiWaktu() {
        OffsetDateTime now = OffsetDateTime.now();
        if (waktu == null) {
            waktu = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }

    /** Selalu {@code true}: jurnal hanya INSERT (tidak ada UPDATE baris). */
    @Override
    public boolean isNew() {
        return true;
    }
}
