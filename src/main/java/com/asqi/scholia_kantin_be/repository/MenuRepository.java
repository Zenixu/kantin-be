package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Akses data item katalog (PRD §7.1). Semua query tenant-scoped. */
public interface MenuRepository extends JpaRepository<Menu, Long> {

    /** Item katalog satu sekolah, opsional filter kategori / hanya aktif. */
    @Query("SELECT m FROM Menu m WHERE m.sekolahId = :sekolahId "
            + "AND (:kategoriId IS NULL OR m.kategoriId = :kategoriId) "
            + "AND (:hanyaAktif = FALSE OR m.isActive = TRUE) "
            + "ORDER BY m.nama ASC")
    List<Menu> daftar(@Param("sekolahId") Long sekolahId,
                      @Param("kategoriId") Long kategoriId,
                      @Param("hanyaAktif") boolean hanyaAktif);

    /** Cari per id <b>dalam tenant</b> — sekolah lain ⇒ kosong (jadi 404). */
    Optional<Menu> findByIdAndSekolahId(Long id, Long sekolahId);

    /** Apakah ada item (aktif atau tidak) yang memakai kategori ini. */
    boolean existsByKategoriId(Long kategoriId);

    /** Jumlah item aktif dalam satu kategori — validasi nonaktifkan kategori. */
    long countByKategoriIdAndIsActiveTrue(Long kategoriId);
}
