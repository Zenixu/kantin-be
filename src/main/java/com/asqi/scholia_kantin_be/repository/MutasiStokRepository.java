package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.enums.ArahStok;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import com.asqi.scholia_kantin_be.model.MutasiStok;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    /** Baris ledger satu menu dalam tenant (sekolah lain ⇒ kosong). */
    Optional<MutasiStok> findByIdAndSekolahId(Long id, Long sekolahId);

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

    /**
     * Riwayat mutasi stok dengan filter (PRD §9.5) — terbaru dulu, halaman.
     *
     * <p>Dipakai {@code GET /api/stok/riwayat} agar FE dapat menampilkan daftar
     * restock sebelum memilih baris yang akan dibalik. Semua filter opsional:
     * {@code menuId}, {@code jenis}, rentang waktu. <b>Tenant-scoped.</b>
     */
    @Query("""
            SELECT m FROM MutasiStok m
            WHERE m.sekolahId = :sekolahId
              AND (:menuId IS NULL OR m.menuId = :menuId)
              AND (:jenis IS NULL OR m.jenis = :jenis)
              AND (:dari IS NULL OR m.waktu >= :dari)
              AND (:sampai IS NULL OR m.waktu < :sampai)
            ORDER BY m.id DESC
            """)
    Page<MutasiStok> riwayat(@Param("sekolahId") Long sekolahId,
                             @Param("menuId") Long menuId,
                             @Param("jenis") JenisMutasiStok jenis,
                             @Param("dari") OffsetDateTime dari,
                             @Param("sampai") OffsetDateTime sampai,
                             Pageable pageable);

    /**
     * Barang masuk berdasarkan nomor bukti (untuk pembalik/riwayat) — terbaru
     * dulu. {@link Pageable} untuk membatasi (mis. ambil satu teratas).
     */
    @Query("""
            SELECT m FROM MutasiStok m
            WHERE m.sekolahId = :sekolahId
              AND m.jenis = :jenis
              AND m.referensiId = :referensiId
            ORDER BY m.id DESC
            """)
    List<MutasiStok> cariByReferensi(@Param("sekolahId") Long sekolahId,
                                     @Param("jenis") JenisMutasiStok jenis,
                                     @Param("referensiId") String referensiId,
                                     Pageable pageable);

    /**
     * Barang masuk berdasarkan nomor bukti <b>+ menu</b> (idempotency) —
     * terbaru dulu. Satu bukti penerimaan boleh memuat beberapa baris item,
     * jadi kunci idempotency adalah {@code (sekolah, referensi, menu)}.
     */
    @Query("""
            SELECT m FROM MutasiStok m
            WHERE m.sekolahId = :sekolahId
              AND m.jenis = :jenis
              AND m.referensiId = :referensiId
              AND m.menuId = :menuId
            ORDER BY m.id DESC
            """)
    List<MutasiStok> cariByReferensiDanMenu(@Param("sekolahId") Long sekolahId,
                                            @Param("jenis") JenisMutasiStok jenis,
                                            @Param("referensiId") String referensiId,
                                            @Param("menuId") Long menuId,
                                            Pageable pageable);

    /**
     * Mutasi berdasarkan <b>referensi_tipe + nomor bukti</b> (idempotency batch
     * opname). Satu berita acara batch mencatat beberapa baris (per menu), jadi
     * dipakai untuk memeriksa replay batch secara utuh.
     */
    @Query("""
            SELECT m FROM MutasiStok m
            WHERE m.sekolahId = :sekolahId
              AND m.referensiTipe = :referensiTipe
              AND m.referensiId = :referensiId
            ORDER BY m.id ASC
            """)
    List<MutasiStok> cariByReferensiTipeDanId(@Param("sekolahId") Long sekolahId,
                                              @Param("referensiTipe") String referensiTipe,
                                              @Param("referensiId") String referensiId);

    /**
     * Total qty yang sudah dibalik untuk sebuah barang masuk asal — dasar
     * penentuan sisa yang masih dapat dibalik (partial reversal).
     */
    @Query("""
            SELECT COALESCE(SUM(m.qty), 0)
            FROM MutasiStok m
            WHERE m.sekolahId = :sekolahId
              AND m.mutasiAsalId = :asalId
            """)
    long totalDibalik(@Param("sekolahId") Long sekolahId, @Param("asalId") Long asalId);

    /**
     * Total dibalik untuk sekumpulan asal dalam <b>satu</b> query — dipakai
     * mengisi {@code sisaDapatDibalik} pada daftar riwayat (hindari N+1).
     */
    @Query("""
            SELECT m.mutasiAsalId, COALESCE(SUM(m.qty), 0)
            FROM MutasiStok m
            WHERE m.sekolahId = :sekolahId
              AND m.mutasiAsalId IN :asalIds
            GROUP BY m.mutasiAsalId
            """)
    List<Object[]> totalDibalikPerAsal(@Param("sekolahId") Long sekolahId,
                                       @Param("asalIds") Collection<Long> asalIds);
}
