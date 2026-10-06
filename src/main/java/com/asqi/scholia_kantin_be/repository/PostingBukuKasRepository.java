package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.PostingBukuKas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Akses data penanda posting Buku Kas ({@code posting_buku_kas}, PRD §5.1).
 *
 * <p>Semua pencarian {@code sekolah_id}-scoped (PRD §11.4) agar idempotency
 * tidak bocor antar-tenant (pola sama seperti V10 untuk saldo).
 */
@Repository
public interface PostingBukuKasRepository extends JpaRepository<PostingBukuKas, Long> {

    /** Penanda posting berdasarkan refId deterministik (tenant-scoped). */
    Optional<PostingBukuKas> findBySekolahIdAndReferensiId(Long sekolahId, String referensiId);

    /** Cek cepat idempotency — apakah refId ini sudah pernah diposting. */
    boolean existsBySekolahIdAndReferensiId(Long sekolahId, String referensiId);
}
