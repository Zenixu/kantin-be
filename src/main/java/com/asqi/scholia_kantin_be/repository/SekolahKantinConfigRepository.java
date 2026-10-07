package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.SekolahKantinConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Akses data pengaturan kantin ({@code sekolah_kantin_config}) — PRD §9.1.
 *
 * <p>PK = {@code sekolah_id} (satu baris per sekolah); operasi selalu
 * tenant-scoped lewat id sekolah pemanggil (PRD §11.4).
 */
@Repository
public interface SekolahKantinConfigRepository extends JpaRepository<SekolahKantinConfig, Long> {
}
