package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.model.KartuTamu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Cek apakah RFID UID dipakai kartu tamu mana pun (aktif maupun tidak),
     * lintas sekolah. Dipakai endpoint internal anti-tabrakan (issue #29) agar
     * admin-be dapat menolak {@code rfid_uid} siswa yang sudah dipakai Kartu Tamu.
     *
     * <p>UID bersifat <b>UNIQUE global</b> (bukan per sekolah), jadi pengecekan
     * tidak dibatasi tenant — mencegah dua pemegang kartu dengan UID sama.
     *
     * @param rfidUid UID yang mau dicek
     * @return {@code true} bila UID dipakai kartu tamu mana pun
     */
    boolean existsByRfidUid(String rfidUid);

    /**
     * Nomor urut kartu terakhir (numerik) milik sekolah — untuk generate nomor
     * berikutnya (PRD §9.4, issue #121). Mengembalikan 0 bila belum ada kartu
     * berformat {@code KT-<angka>}. Memakai ekstraksi numerik agar tetap benar
     * saat urutan melewati 999 (KT-1000 &gt; KT-999).
     */
    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(nomor_kartu FROM 4) AS INTEGER)), 0)
            FROM kartu_tamu
            WHERE sekolah_id = :sekolahId AND nomor_kartu ~ '^KT-[0-9]+$'
            """, nativeQuery = true)
    int nomorUrutTerakhir(@Param("sekolahId") Long sekolahId);
}
