package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.InsidenOffline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Akses data insiden offline ({@code insiden_offline}) — DEMO Q14/#23.
 *
 * <p><b>Append-only (PRD §11.1):</b> sengaja <b>tidak</b> menyediakan method
 * {@code delete}/{@code update}. Hanya {@code save} (INSERT) &amp; query baca;
 * {@link InsidenOffline} juga {@code @Immutable} + trigger DB.
 */
@Repository
public interface InsidenOfflineRepository extends JpaRepository<InsidenOffline, Long> {

    /** Insiden satu sekolah, terbaru dulu (tenant-scoped, PRD §11.4). */
    List<InsidenOffline> findBySekolahIdOrderByMulaiDesc(Long sekolahId);
}
