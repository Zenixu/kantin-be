package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.KategoriMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Akses data kategori katalog (PRD §7.1). Semua query tenant-scoped. */
public interface KategoriMenuRepository extends JpaRepository<KategoriMenu, Long> {

    /** Kategori satu sekolah, diurutkan untuk tampilan kasir. */
    @Query("SELECT k FROM KategoriMenu k WHERE k.sekolahId = :sekolahId "
            + "AND (:hanyaAktif = FALSE OR k.isActive = TRUE) "
            + "ORDER BY k.urutan ASC, k.nama ASC")
    List<KategoriMenu> daftar(@Param("sekolahId") Long sekolahId,
                              @Param("hanyaAktif") boolean hanyaAktif);

    /** Cari per id <b>dalam tenant</b> — sekolah lain ⇒ kosong (jadi 404). */
    Optional<KategoriMenu> findByIdAndSekolahId(Long id, Long sekolahId);

    /** Deteksi duplikat nama kategori dalam satu sekolah. */
    Optional<KategoriMenu> findBySekolahIdAndNamaIgnoreCase(Long sekolahId, String nama);

    /** Jumlah kategori aktif (dipakai validasi/among lain). */
    long countBySekolahIdAndIsActiveTrue(Long sekolahId);
}
