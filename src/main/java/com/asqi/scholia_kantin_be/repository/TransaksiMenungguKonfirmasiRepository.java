package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.enums.StatusPendingTap;
import com.asqi.scholia_kantin_be.model.TransaksiMenungguKonfirmasi;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Akses data tap menunggu konfirmasi manual (PRD §6.1) — selalu tenant-scoped
 * (PRD §11.4).
 */
@Repository
public interface TransaksiMenungguKonfirmasiRepository
        extends JpaRepository<TransaksiMenungguKonfirmasi, Long> {

    /** Idempotency: key sama (per sekolah) → satu baris pending (PRD §11.3). */
    Optional<TransaksiMenungguKonfirmasi> findBySekolahIdAndIdempotencyKey(
            Long sekolahId, String idempotencyKey);

    /** Daftar pending yang masih menunggu untuk satu sekolah. */
    List<TransaksiMenungguKonfirmasi> findBySekolahIdAndStatusOrderByIdAsc(
            Long sekolahId, StatusPendingTap status);

    /**
     * Ambil &amp; kunci baris pending (FOR UPDATE) agar konfirmasi/batal tidak
     * balapan — tenant-scoped (PRD §11.4).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM TransaksiMenungguKonfirmasi p "
            + "WHERE p.sekolahId = :sekolahId AND p.id = :id")
    Optional<TransaksiMenungguKonfirmasi> kunciUntukUpdate(@Param("sekolahId") Long sekolahId,
                                                           @Param("id") Long id);
}
