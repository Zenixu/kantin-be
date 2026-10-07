package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.LimitHarian;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Akses data limit harian ({@code limit_harian}) — PRD §8.3.
 *
 * <p>Selalu tenant-scoped lewat {@code sekolah_id} (PRD §11.4).
 */
@Repository
public interface LimitHarianRepository extends JpaRepository<LimitHarian, Long> {

    Optional<LimitHarian> findBySekolahIdAndSubjekTipeAndSubjekId(
            Long sekolahId, SubjekTipe subjekTipe, Long subjekId);
}
