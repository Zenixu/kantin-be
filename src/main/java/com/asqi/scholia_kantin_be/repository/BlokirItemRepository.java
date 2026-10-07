package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.BlokirItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Akses data blokir item/kategori ({@code blokir_item}) — PRD §8.3.
 *
 * <p>Selalu tenant-scoped lewat {@code sekolah_id} (PRD §11.4).
 */
@Repository
public interface BlokirItemRepository extends JpaRepository<BlokirItem, Long> {

    List<BlokirItem> findBySekolahIdAndSubjekTipeAndSubjekId(
            Long sekolahId, SubjekTipe subjekTipe, Long subjekId);

    Optional<BlokirItem> findBySekolahIdAndSubjekTipeAndSubjekIdAndMenuId(
            Long sekolahId, SubjekTipe subjekTipe, Long subjekId, Long menuId);

    Optional<BlokirItem> findBySekolahIdAndSubjekTipeAndSubjekIdAndKategoriId(
            Long sekolahId, SubjekTipe subjekTipe, Long subjekId, Long kategoriId);

    /** Hanya baris yang benar-benar aktif diblokir (untuk validasi tap). */
    @Query("SELECT b FROM BlokirItem b WHERE b.sekolahId = :sekolahId "
            + "AND b.subjekTipe = :subjekTipe AND b.subjekId = :subjekId AND b.diblokir = true")
    List<BlokirItem> cariAktif(@Param("sekolahId") Long sekolahId,
                               @Param("subjekTipe") SubjekTipe subjekTipe,
                               @Param("subjekId") Long subjekId);
}
