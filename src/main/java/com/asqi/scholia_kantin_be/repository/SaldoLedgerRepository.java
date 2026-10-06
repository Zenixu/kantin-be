package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.SaldoLedger;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Akses data ledger saldo ({@code saldo_ledger}).
 *
 * <p><b>Append-only (PRD §11.1):</b> repository ini sengaja <b>tidak</b>
 * menyediakan method {@code delete}/{@code update}. Hanya {@code save} (INSERT)
 * dan query baca. {@link SaldoLedger} juga {@code @Immutable}.
 *
 * <p>Semua query menerima {@code sekolahId} eksplisit agar tenant scoping
 * (PRD §11.4) terlihat jelas di setiap pemanggilan.
 */
@Repository
public interface SaldoLedgerRepository extends JpaRepository<SaldoLedger, Long> {

    /**
     * Idempotency: satu key hanya boleh menghasilkan satu mutasi (PRD §11.3).
     *
     * @deprecated <b>BUKAN tenant-scoped</b> — bisa mencampur sekolah berbeda
     *     (audit keamanan). Pakai {@link #findBySekolahIdAndIdempotencyKey}.
     */
    @Deprecated(since = "audit-keamanan", forRemoval = true)
    Optional<SaldoLedger> findByIdempotencyKey(String idempotencyKey);

    /**
     * @deprecated <b>BUKAN tenant-scoped</b> — pakai
     *     {@link #findBySekolahIdAndIdempotencyKey}.
     */
    @Deprecated(since = "audit-keamanan", forRemoval = true)
    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Idempotency <b>tenant-scoped</b> (PRD §11.4): key yang sama di sekolah
     * berbeda adalah mutasi berbeda. Wajib dipakai untuk replay agar nomor
     * bukti antar sekolah tidak saling menelan.
     */
    Optional<SaldoLedger> findBySekolahIdAndIdempotencyKey(Long sekolahId, String idempotencyKey);

    /** Mutasi yang lahir dari sebuah transaksi (untuk audit/rekonsiliasi). */
    List<SaldoLedger> findBySekolahIdAndTransaksiId(Long sekolahId, Long transaksiId);

    /**
     * Saldo berjalan = Σ KREDIT − Σ DEBIT untuk satu subjek (PRD §11.1).
     *
     * <p>Dipakai untuk <b>verifikasi</b> terhadap {@code saldo_cache} dan
     * penghitungan ulang bila cache dicurigai drift.
     */
    @Query("""
            SELECT COALESCE(SUM(CASE WHEN l.arah = :kredit THEN l.nominal ELSE -l.nominal END), 0)
            FROM SaldoLedger l
            WHERE l.sekolahId = :sekolahId
              AND l.subjekTipe = :subjekTipe
              AND l.subjekId = :subjekId
            """)
    Long hitungSaldoDariLedger(@Param("sekolahId") Long sekolahId,
                               @Param("subjekTipe") SubjekTipe subjekTipe,
                               @Param("subjekId") Long subjekId,
                               @Param("kredit") ArahMutasi kredit);

    /**
     * Belanja bersih hari ini untuk satu subjek — dasar pemeriksaan <b>limit
     * harian</b> (PRD §6.1 tahap 5, reset 00:00 waktu sekolah).
     *
     * <p>Dihitung <b>net</b>: Σ DEBIT PENJUALAN − Σ KREDIT VOID_PENJUALAN sejak
     * {@code sejak}. Dengan begitu transaksi yang sudah di-void tidak lagi
     * memakan jatah limit (PRD §6.3: "belanja hari ini ikut berkurang").
     * Koreksi bendahara tidak dihitung (bukan belanja).
     */
    @Query("""
            SELECT COALESCE(SUM(CASE WHEN l.arah = :debit THEN l.nominal ELSE -l.nominal END), 0)
            FROM SaldoLedger l
            WHERE l.sekolahId = :sekolahId
              AND l.subjekTipe = :subjekTipe
              AND l.subjekId = :subjekId
              AND l.jenis IN :jenis
              AND l.waktu >= :sejak
            """)
    Long hitungBelanjaBersihSejak(@Param("sekolahId") Long sekolahId,
                                  @Param("subjekTipe") SubjekTipe subjekTipe,
                                  @Param("subjekId") Long subjekId,
                                  @Param("debit") ArahMutasi debit,
                                  @Param("jenis") java.util.Collection<JenisMutasiSaldo> jenis,
                                  @Param("sejak") OffsetDateTime sejak);

    /**
     * Riwayat mutasi terbaru satu subjek (PRD §8.4) — urut id turun (id
     * sortable ≈ urut waktu). {@link Pageable} untuk membatasi jumlah.
     */
    @Query("""
            SELECT l FROM SaldoLedger l
            WHERE l.sekolahId = :sekolahId
              AND l.subjekTipe = :subjekTipe
              AND l.subjekId = :subjekId
            ORDER BY l.id DESC
            """)
    List<SaldoLedger> riwayatTerbaru(@Param("sekolahId") Long sekolahId,
                                     @Param("subjekTipe") SubjekTipe subjekTipe,
                                     @Param("subjekId") Long subjekId,
                                     Pageable pageable);

    /**
     * Arus saldo per (arah, jenis) pada rentang waktu — laporan rekonsiliasi
     * harian (PRD §9.5). Mengembalikan baris {@code [arah, jenis, Σ nominal]}.
     */
    @Query("""
            SELECT l.arah, l.jenis, COALESCE(SUM(l.nominal), 0)
            FROM SaldoLedger l
            WHERE l.sekolahId = :sekolahId
              AND l.waktu >= :dari AND l.waktu < :sampai
            GROUP BY l.arah, l.jenis
            """)
    List<Object[]> rekapArusRentang(@Param("sekolahId") Long sekolahId,
                                    @Param("dari") OffsetDateTime dari,
                                    @Param("sampai") OffsetDateTime sampai);

    /**
     * Σ nominal per arah untuk <b>seluruh waktu</b> (tanpa batas periode) —
     * dasar pemeriksaan invariant rekonsiliasi (PRD §5). Mengembalikan baris
     * {@code [arah, Σ nominal]}.
     */
    @Query("""
            SELECT l.arah, COALESCE(SUM(l.nominal), 0)
            FROM SaldoLedger l
            WHERE l.sekolahId = :sekolahId
            GROUP BY l.arah
            """)
    List<Object[]> totalPerArah(@Param("sekolahId") Long sekolahId);

    /** Mutasi saldo pada rentang waktu (laporan per siswa / ekspor, PRD §9.5). */
    @Query("""
            SELECT l FROM SaldoLedger l
            WHERE l.sekolahId = :sekolahId
              AND l.waktu >= :dari AND l.waktu < :sampai
            ORDER BY l.id ASC
            """)
    List<SaldoLedger> padaRentang(@Param("sekolahId") Long sekolahId,
                                  @Param("dari") OffsetDateTime dari,
                                  @Param("sampai") OffsetDateTime sampai);
}
