package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.BlokirKartu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Akses data blokir kartu ({@code blokir_kartu}) — PRD §8.3.
 *
 * <p>Selalu tenant-scoped lewat {@code sekolah_id} (PRD §11.4). Status blokir
 * dibaca tiap tap (tanpa cache, PRD §11.11).
 */
@Repository
public interface BlokirKartuRepository extends JpaRepository<BlokirKartu, Long> {

    Optional<BlokirKartu> findBySekolahIdAndSubjekTipeAndSubjekId(
            Long sekolahId, SubjekTipe subjekTipe, Long subjekId);
}
