package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.TransaksiItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Akses data rincian item transaksi ({@code transaksi_item}).
 *
 * <p>Query agregat dipakai untuk laporan penjualan per item/kategori (PRD §9.5).
 */
@Repository
public interface TransaksiItemRepository extends JpaRepository<TransaksiItem, Long> {

    List<TransaksiItem> findByTransaksiId(Long transaksiId);

    /**
     * Item terlaris pada rentang waktu: {@code [menuId, namaMenu, totalQty,
     * totalNilai]}, hanya dari transaksi SUKSES.
     *
     * <p>JOIN ke {@code Transaksi} untuk memfilter status &amp; waktu.
     */
    @Query("""
            SELECT i.menuId, i.namaMenu, SUM(i.qty), SUM(i.subtotal)
            FROM TransaksiItem i JOIN i.transaksi t
            WHERE t.sekolahId = :sekolahId
              AND t.status = com.asqi.scholia_kantin_be.enums.StatusTransaksi.SUKSES
              AND t.waktu >= :dari AND t.waktu < :sampai
            GROUP BY i.menuId, i.namaMenu
            ORDER BY SUM(i.qty) DESC
            """)
    List<Object[]> itemTerlaris(@Param("sekolahId") Long sekolahId,
                                @Param("dari") OffsetDateTime dari,
                                @Param("sampai") OffsetDateTime sampai,
                                Pageable pageable);

    /** Penjualan per kategori pada rentang waktu: {@code [kategoriId, totalQty, totalNilai]}. */
    @Query("""
            SELECT i.kategoriId, SUM(i.qty), SUM(i.subtotal)
            FROM TransaksiItem i JOIN i.transaksi t
            WHERE t.sekolahId = :sekolahId
              AND t.status = com.asqi.scholia_kantin_be.enums.StatusTransaksi.SUKSES
              AND t.waktu >= :dari AND t.waktu < :sampai
            GROUP BY i.kategoriId
            ORDER BY SUM(i.subtotal) DESC
            """)
    List<Object[]> penjualanPerKategori(@Param("sekolahId") Long sekolahId,
                                        @Param("dari") OffsetDateTime dari,
                                        @Param("sampai") OffsetDateTime sampai);

    /** Σ HPP snapshot item pada rentang waktu (verifikasi laba kotor). */
    @Query("""
            SELECT COALESCE(SUM(i.hppSnapshot * i.qty), 0)
            FROM TransaksiItem i JOIN i.transaksi t
            WHERE t.sekolahId = :sekolahId
              AND t.status = com.asqi.scholia_kantin_be.enums.StatusTransaksi.SUKSES
              AND t.waktu >= :dari AND t.waktu < :sampai
            """)
    Long totalHppItemRentang(@Param("sekolahId") Long sekolahId,
                             @Param("dari") OffsetDateTime dari,
                             @Param("sampai") OffsetDateTime sampai);
}
