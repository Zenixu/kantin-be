package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.KebijakanKantin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Akses data kebijakan kantin ({@code kebijakan_kantin}) — DEMO Q16/#25.
 *
 * <p>PK = {@code sekolah_id} (satu baris per sekolah). Operasi selalu
 * tenant-scoped lewat id sekolah pemanggil (PRD §11.4).
 */
@Repository
public interface KebijakanKantinRepository extends JpaRepository<KebijakanKantin, Long> {
}
