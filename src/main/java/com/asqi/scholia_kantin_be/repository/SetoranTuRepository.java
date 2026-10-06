package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.SetoranTu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Akses data setoran kas TU ({@code setoran_tu}) — PRD §9.2, issue #39.
 *
 * <p><b>Append-only (PRD §11.1):</b> sengaja <b>tidak</b> menyediakan method
 * {@code delete}/{@code update}. Hanya {@code save} (INSERT) &amp; query baca;
 * {@link SetoranTu} juga {@code @Immutable} + trigger DB.
 *
 * <p>Semua query menerima {@code sekolahId} eksplisit agar tenant scoping
 * (PRD §11.4) terlihat di setiap pemanggilan.
 */
@Repository
public interface SetoranTuRepository extends JpaRepository<SetoranTu, Long> {

    /**
     * Idempotency <b>tenant-scoped</b> (PRD §11.4): satu nomor berita acara
     * hanya boleh menghasilkan satu setoran per sekolah. Dipakai untuk replay
     * agar double-submit tidak mencatat setoran dua kali.
     */
    Optional<SetoranTu> findBySekolahIdAndReferensiId(Long sekolahId, String referensiId);

    /** Daftar setoran satu sekolah pada tanggal tertentu (tampilan bendahara). */
    List<SetoranTu> findBySekolahIdAndTanggalOrderByPetugasIdAsc(Long sekolahId, LocalDate tanggal);
}
