package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Akses data jurnal event webhook ({@code webhook_event}).
 *
 * <p><b>Append-only:</b> tanpa method {@code delete}/{@code update}.
 */
@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    /**
     * Cari event berdasarkan (sumber, event id) — kunci idempotency. Inilah yang
     * membuat retry webhook tidak diproses dua kali (PRD §11.3).
     */
    Optional<WebhookEvent> findBySumberAndEventId(String sumber, String eventId);

    boolean existsBySumberAndEventId(String sumber, String eventId);
}
