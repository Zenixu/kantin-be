package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.enums.ArahStok;
import com.asqi.scholia_kantin_be.model.MutasiStok;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Akses data ledger stok ({@code mutasi_stok}).
 *
 * <p><b>Append-only (PRD §11.1):</b> tanpa method {@code delete}/{@code update}.
 * {@link MutasiStok} juga {@code @Immutable}.
 */
@Repository
public interface MutasiStokRepository extends JpaRepository<MutasiStok, Long> {

    /** Mutasi stok yang lahir dari sebuah transaksi (void/rekonsiliasi). */
    List<MutasiStok> findBySekolahIdAndTransaksiId(Long sekolahId, Long transaksiId);

    /**
     * Stok berjalan = Σ MASUK − Σ KELUAR untuk satu menu (PRD §11.1) — untuk
     * verifikasi terhadap {@code stok_cache}.
     */
    @Query("""
            SELECT COALESCE(SUM(CASE WHEN m.arah = :masuk THEN m.qty ELSE -m.qty END), 0)
            FROM MutasiStok m
            WHERE m.sekolahId = :sekolahId
              AND m.menuId = :menuId
            """)
    Long hitungStokDariLedger(@Param("sekolahId") Long sekolahId,
                              @Param("menuId") Long menuId,
                              @Param("masuk") ArahStok masuk);

    /** Kartu stok per item: riwayat mutasi (PRD §9.5). */
    @Query("""
            SELECT m FROM MutasiStok m
            WHERE m.sekolahId = :sekolahId
              AND m.menuId = :menuId
            ORDER BY m.id DESC
            """)
    List<MutasiStok> kartuStok(@Param("sekolahId") Long sekolahId,
                               @Param("menuId") Long menuId,
                               Pageable pageable);

    /** Mutasi stok pada rentang waktu (laporan stok &amp; kerugian, PRD §9.5). */
    @Query("""
            SELECT m FROM MutasiStok m
            WHERE m.sekolahId = :sekolahId
              AND m.waktu >= :dari
              AND m.waktu < :sampai
            ORDER BY m.id ASC
            """)
    List<MutasiStok> padaRentang(@Param("sekolahId") Long sekolahId,
                                 @Param("dari") OffsetDateTime dari,
                                 @Param("sampai") OffsetDateTime sampai);
}
