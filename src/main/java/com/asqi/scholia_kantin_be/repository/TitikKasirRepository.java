package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.TitikKasir;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Akses data titik kasir ({@code titik_kasir}) — PRD §6.6.
 */
@Repository
public interface TitikKasirRepository extends JpaRepository<TitikKasir, Long> {

    List<TitikKasir> findBySekolahIdAndAktifTrue(Long sekolahId);

    List<TitikKasir> findBySekolahId(Long sekolahId);

    Optional<TitikKasir> findByIdAndSekolahId(Long id, Long sekolahId);

    boolean existsBySekolahIdAndKode(Long sekolahId, String kode);
}
