package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.PosBukuKas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Akses data pos Buku Kas kantin ({@code pos_buku_kas}) — DEMO Q8/#21.
 *
 * <p>Semua query menerima {@code sekolahId} eksplisit agar tenant scoping
 * (PRD §11.4) terlihat di setiap pemanggilan.
 */
@Repository
public interface PosBukuKasRepository extends JpaRepository<PosBukuKas, Long> {

    /** Daftar pos satu sekolah (untuk seeding idempoten & tampilan). */
    List<PosBukuKas> findBySekolahIdOrderByNamaAsc(Long sekolahId);

    /** Idempotency seeding: satu nama pos hanya boleh ada sekali per sekolah. */
    Optional<PosBukuKas> findBySekolahIdAndNama(Long sekolahId, String nama);
}
