package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.KartuTamu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository untuk {@link KartuTamu}.
 */
@Repository
public interface KartuTamuRepository extends JpaRepository<KartuTamu, Long> {

    /**
     * Cari kartu berdasarkan nomor kartu + sekolah.
     * 
     * @return {@link Optional#empty()} jika tidak ada atau beda sekolah
     */
    Optional<KartuTamu> findBySekolahIdAndNomorKartu(Long sekolahId, String nomorKartu);

    /**
     * Cari kartu berdasarkan RFID UID.
     * Dipakai saat tap untuk lookup kartu.
     * 
     * @return {@link Optional#empty()} jika UID tidak terdaftar
     */
    Optional<KartuTamu> findByRfidUid(String rfidUid);

    /**
     * Daftar semua kartu tamu milik sekolah.
     * 
     * @param hanyaAktif jika {@code true}, hanya kartu aktif
     */
    @Query("""
            SELECT k FROM KartuTamu k
            WHERE k.sekolahId = :sekolahId
            AND (:hanyaAktif = false OR k.aktif = true)
            ORDER BY k.nomorKartu
            """)
    List<KartuTamu> findAllBySekolah(Long sekolahId, boolean hanyaAktif);

    /**
     * Cek apakah RFID UID sudah dipakai kartu tamu lain.
     * Untuk anti-tabrakan saat bind/update UID.
     * 
     * @param rfidUid UID yang mau dicek
     * @param excludeId ID kartu yang boleh diabaikan (untuk update)
     * @return {@code true} jika UID sudah dipakai kartu lain
     */
    @Query("""
            SELECT CASE WHEN COUNT(k) > 0 THEN true ELSE false END
            FROM KartuTamu k
            WHERE k.rfidUid = :rfidUid
            AND (:excludeId IS NULL OR k.id != :excludeId)
            """)
    boolean existsByRfidUidExcluding(String rfidUid, Long excludeId);
}
