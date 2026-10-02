package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.model.Transaksi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Akses data transaksi kasir ({@code transaksi}).
 *
 * <p>Idempotency tap dijaga kolom {@code idempotency_key} UNIQUE. Query agregat
 * di sini dipakai untuk rekap sesi kasir &amp; laporan (PRD §6.4, §9.5).
 */
@Repository
public interface TransaksiRepository extends JpaRepository<Transaksi, Long> {

    /** Idempotency: cari transaksi berdasarkan key dari klien kasir (PRD §11.3). */
    Optional<Transaksi> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    /** Transaksi satu sesi, terfilter status (rekap tutup kasir). */
    Page<Transaksi> findBySesiKasirIdAndStatus(Long sesiKasirId, StatusTransaksi status, Pageable pageable);

    /** Riwayat transaksi per subjek (siswa / kartu tamu) untuk ortu &amp; laporan. */
    Page<Transaksi> findBySekolahIdAndSubjekTipeAndSubjekIdOrderByIdDesc(
            Long sekolahId, com.asqi.scholia_kantin_be.enums.SubjekTipe subjekTipe, Long subjekId, Pageable pageable);

    /**
     * Rekap sesi: jumlah transaksi &amp; total per status.
     *
     * <p>Mengembalikan baris {@code [status, jumlah, total]}. Dipakai
     * {@code SesiKasirService} menghitung total bruto/void/bersih (PRD §6.4).
     */
    @Query("""
            SELECT t.status, COUNT(t), COALESCE(SUM(t.total), 0)
            FROM Transaksi t
            WHERE t.sekolahId = :sekolahId AND t.sesiKasirId = :sesiKasirId
            GROUP BY t.status
            """)
    java.util.List<Object[]> rekapSesi(@Param("sekolahId") Long sekolahId,
                                       @Param("sesiKasirId") Long sesiKasirId);

    /** Total penjualan bersih (SUKSES) pada rentang waktu — untuk laporan. */
    @Query("""
            SELECT COALESCE(SUM(t.total), 0)
            FROM Transaksi t
            WHERE t.sekolahId = :sekolahId
              AND t.status = :status
              AND t.waktu >= :dari AND t.waktu < :sampai
            """)
    Long totalPenjualanRentang(@Param("sekolahId") Long sekolahId,
                               @Param("status") StatusTransaksi status,
                               @Param("dari") OffsetDateTime dari,
                               @Param("sampai") OffsetDateTime sampai);

    /** Σ HPP transaksi SUKSES pada rentang waktu — dasar laba kotor (PRD §5). */
    @Query("""
            SELECT COALESCE(SUM(t.totalHpp), 0)
            FROM Transaksi t
            WHERE t.sekolahId = :sekolahId
              AND t.status = :status
              AND t.waktu >= :dari AND t.waktu < :sampai
            """)
    Long totalHppRentang(@Param("sekolahId") Long sekolahId,
                         @Param("status") StatusTransaksi status,
                         @Param("dari") OffsetDateTime dari,
                         @Param("sampai") OffsetDateTime sampai);
}
