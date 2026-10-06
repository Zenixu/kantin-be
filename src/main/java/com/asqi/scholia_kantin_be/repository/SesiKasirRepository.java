package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.enums.StatusSesiKasir;
import com.asqi.scholia_kantin_be.model.SesiKasir;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Akses data sesi kasir ({@code sesi_kasir}) — PRD §6.4.
 */
@Repository
public interface SesiKasirRepository extends JpaRepository<SesiKasir, Long> {

    /** Satu titik kasir hanya punya satu sesi per tanggal (UNIQUE di DB). */
    Optional<SesiKasir> findByTitikKasirIdAndTanggal(Long titikKasirId, LocalDate tanggal);

    Optional<SesiKasir> findBySekolahIdAndTitikKasirIdAndTanggal(Long sekolahId, Long titikKasirId, LocalDate tanggal);

    List<SesiKasir> findBySekolahIdAndTanggal(Long sekolahId, LocalDate tanggal);

    /** Sesi yang masih terbuka untuk suatu sekolah (mis. untuk auto-tutup). */
    List<SesiKasir> findBySekolahIdAndStatus(Long sekolahId, StatusSesiKasir status);

    /**
     * Daftar {@code sekolah_id} unik yang masih punya sesi berstatus tertentu.
     *
     * <p>Dipakai auto-tutup lintas-tenant: kantin-be tidak menyimpan tabel
     * {@code sekolah} (milik admin-be), jadi daftar tenant yang perlu diproses
     * diturunkan dari data sesi itu sendiri. <b>Tidak</b> tenant-scoped karena
     * tugas terjadwal ini memang bekerja untuk SEMUA tenant.
     */
    @Query("""
            SELECT DISTINCT s.sekolahId FROM SesiKasir s
            WHERE s.status = :status
            """)
    List<Long> daftarSekolahIdDenganStatus(@Param("status") StatusSesiKasir status);

    /** Sesi terbuka yang tanggalnya SEBELUM tanggal tertentu (sesi tertinggal). */
    List<SesiKasir> findByStatusAndTanggalBefore(StatusSesiKasir status, LocalDate tanggal);

    /** Sesi terbuka milik satu sekolah yang tanggalnya sebelum tanggal tertentu. */
    List<SesiKasir> findBySekolahIdAndStatusAndTanggalBefore(Long sekolahId,
                                                             StatusSesiKasir status,
                                                             LocalDate tanggal);

    /**
     * Sesi yang sudah DITUTUP namun belum terposting ke Buku Kas, dan memang
     * punya pendapatan untuk diposting (PRD §5.1, §6.4; issue #33).
     *
     * <p>Posting saat tutup kasir bersifat <b>fail-open</b>: bila integrasi
     * admin-be gagal, sesi tetap DITUTUP dengan {@code posting_buku_kas=false}.
     * Penjadwal retry memakai daftar ini untuk mencoba ulang. Sesi dengan
     * {@code total_bersih = 0} dikecualikan — memang tidak ada yang perlu
     * diposting, jadi tak boleh dianggap "tertunggak".
     *
     * <p><b>Tidak tenant-scoped</b> — tugas terjadwal ini bekerja untuk SEMUA
     * tenant (pola sama seperti {@link #daftarSekolahIdDenganStatus}); tiap baris
     * membawa {@code sekolah_id}-nya sendiri agar retry tetap tenant-safe.
     */
    @Query("""
            SELECT s FROM SesiKasir s
            WHERE s.status = :status
              AND s.postingBukuKas = false
              AND s.totalBersih > 0
            ORDER BY s.sekolahId, s.id
            """)
    List<SesiKasir> cariBelumTerposting(@Param("status") StatusSesiKasir status);

    /**
     * Ambil &amp; kunci sesi ({@code FOR UPDATE}) agar buka/tutup kasir tidak
     * balapan pada titik kasir yang sama.
     *
     * <p><b>Tenant-scoped (PRD §11.4, B17):</b> {@code sekolah_id} ikut di
     * {@code WHERE} sehingga sesi sekolah lain tidak pernah terkunci.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SesiKasir s WHERE s.sekolahId = :sekolahId AND s.id = :id")
    Optional<SesiKasir> kunciUntukUpdate(@Param("sekolahId") Long sekolahId,
                                         @Param("id") Long id);

    /**
     * Sesi terbuka pada satu titik kasir (untuk validasi transaksi).
     *
     * <p><b>Tenant-scoped (PRD §11.4, B17):</b> {@code sekolah_id} ikut di
     * {@code WHERE}. Karena UNIQUE di DB adalah {@code (titik_kasir_id, tanggal)},
     * menambahkan tenant di sini <b>tidak</b> melemahkan jaminan satu-sesi-per-hari.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM SesiKasir s
            WHERE s.sekolahId = :sekolahId
              AND s.titikKasirId = :titikKasirId
              AND s.tanggal = :tanggal
            """)
    Optional<SesiKasir> kunciBerdasarkanTitikTanggal(@Param("sekolahId") Long sekolahId,
                                                     @Param("titikKasirId") Long titikKasirId,
                                                     @Param("tanggal") LocalDate tanggal);
}
