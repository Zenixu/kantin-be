package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.SaldoCache;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Akses data saldo berjalan ({@code saldo_cache}).
 *
 * <p>Baris ini adalah <b>hot row</b> yang dikunci saat debit agar dua kasir
 * tidak memotong saldo yang sama secara bersamaan (ADR-0003, PRD §11.2).
 */
@Repository
public interface SaldoCacheRepository extends JpaRepository<SaldoCache, SaldoCache.SaldoCacheId> {

    /**
     * Ambil &amp; kunci baris saldo ({@code SELECT ... FOR UPDATE}).
     *
     * <p>Wajib dipanggil di dalam {@code @Transactional}: lock dilepas saat
     * transaksi selesai. Bila baris tidak ada → {@code Optional.empty()}
     * (pemanggil harus membuatnya lewat {@link #pastikanBarisAda}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SaldoCache s WHERE s.subjekTipe = :subjekTipe AND s.subjekId = :subjekId")
    Optional<SaldoCache> kunciUntukUpdate(@Param("subjekTipe") SubjekTipe subjekTipe,
                                          @Param("subjekId") Long subjekId);

    Optional<SaldoCache> findBySubjekTipeAndSubjekId(SubjekTipe subjekTipe, Long subjekId);

    List<SaldoCache> findBySekolahIdAndSubjekTipe(Long sekolahId, SubjekTipe subjekTipe);

    /**
     * Pastikan baris saldo ada <b>tanpa race</b>: {@code INSERT ... ON CONFLICT
     * DO NOTHING}. Bila dua kasir mencoba membuat baris yang sama serentak,
     * hanya satu yang berhasil dan yang lain diam-diam dilewati — lalu keduanya
     * tetap mengunci baris yang sama lewat {@link #kunciUntukUpdate}.
     *
     * <p>Native karena {@code ON CONFLICT} khas PostgreSQL.
     */
    @Modifying
    @Query(value = """
            INSERT INTO saldo_cache (subjek_tipe, subjek_id, sekolah_id, saldo, updated_at)
            VALUES (:subjekTipe, :subjekId, :sekolahId, 0, now())
            ON CONFLICT (subjek_tipe, subjek_id) DO NOTHING
            """, nativeQuery = true)
    int pastikanBarisAda(@Param("subjekTipe") String subjekTipe,
                         @Param("subjekId") Long subjekId,
                         @Param("sekolahId") Long sekolahId);
}
