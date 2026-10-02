package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.StokCache;
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
 * Akses data stok &amp; HPP berjalan ({@code stok_cache}).
 *
 * <p>Baris dikunci ({@code FOR UPDATE}) saat penjualan agar dua kasir tidak
 * menjual stok yang sama (ADR-0003, PRD §11.2).
 */
@Repository
public interface StokCacheRepository extends JpaRepository<StokCache, Long> {

    /**
     * Ambil &amp; kunci baris stok ({@code SELECT ... FOR UPDATE}). Wajib di dalam
     * {@code @Transactional}. Bila tidak ada → pemanggil memakai
     * {@link #pastikanBarisAda} lalu mengunci ulang.
     *
     * <p><b>Tenant-scoped (PRD §11.4, B17):</b> {@code sekolah_id} ikut di
     * {@code WHERE} agar baris sekolah lain tidak terkunci oleh sekolah yang
     * bukan pemiliknya.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM StokCache s
            WHERE s.sekolahId = :sekolahId
              AND s.menuId = :menuId
            """)
    Optional<StokCache> kunciUntukUpdate(@Param("sekolahId") Long sekolahId,
                                         @Param("menuId") Long menuId);

    Optional<StokCache> findByMenuId(Long menuId);

    /** Item "stok menipis": stok ≤ stok_minimum (PRD §7.5). */
    @Query("SELECT s FROM StokCache s WHERE s.sekolahId = :sekolahId AND s.stok <= s.stokMinimum")
    List<StokCache> stokMenipis(@Param("sekolahId") Long sekolahId);

    /**
     * Pastikan baris stok ada tanpa race ({@code INSERT ... ON CONFLICT DO
     * NOTHING}). Baris baru mulai dari stok 0, HPP 0, minimum 0; pengelola
     * menyesuaikan lewat barang masuk/opname.
     */
    @Modifying
    @Query(value = """
            INSERT INTO stok_cache (menu_id, sekolah_id, stok, hpp, stok_minimum, updated_at)
            VALUES (:menuId, :sekolahId, 0, 0, 0, now())
            ON CONFLICT (menu_id) DO NOTHING
            """, nativeQuery = true)
    int pastikanBarisAda(@Param("menuId") Long menuId, @Param("sekolahId") Long sekolahId);
}
