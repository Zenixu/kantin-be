package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import com.asqi.scholia_kantin_be.model.MutasiStok;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Specification (Criteria API) untuk filter <b>riwayat mutasi stok</b>
 * (PRD §9.5) — pengganti pola JPQL {@code :x IS NULL OR ...}.
 *
 * <p><b>Kenapa?</b> Regresi issue <b>#198</b>: pola {@code :x IS NULL OR m.x = :x}
 * membuat Hibernate mengirim parameter {@code null} tanpa tipe JDBC eksplisit
 * untuk {@code Long}/{@code enum}/{@code OffsetDateTime}. PostgreSQL menolak
 * menebak tipe parameter ({@code could not determine data type of parameter $n})
 * sehingga {@code GET /api/stok/riwayat} <b>selalu 500</b> — baik filter kosong
 * maupun terisi.
 *
 * <p>Dengan Specification, predicate <b>hanya ditambahkan bila filternya ada</b>
 * sehingga tidak pernah ada parameter {@code null} tanpa tipe di SQL. Aman untuk
 * semua kombinasi filter. Predicate dibangun kondisional (tanpa {@code IS NULL
 * OR}) dan tetap <b>tenant-scoped</b>.
 */
public final class MutasiStokSpesifikasi {

    private MutasiStokSpesifikasi() {
        // kelas util
    }

    /**
     * Filter riwayat mutasi stok satu sekolah. Semua filter opsional
     * ({@code null} = tanpa filter) dan digabung dengan AND.
     */
    public static Specification<MutasiStok> riwayat(Long sekolahId, Long menuId,
                                                    JenisMutasiStok jenis,
                                                    OffsetDateTime dari, OffsetDateTime sampai) {
        return (root, query, cb) -> {
            List<Predicate> predicate = new ArrayList<>();
            predicate.add(cb.equal(root.get("sekolahId"), sekolahId));
            if (menuId != null) {
                predicate.add(cb.equal(root.get("menuId"), menuId));
            }
            if (jenis != null) {
                predicate.add(cb.equal(root.get("jenis"), jenis));
            }
            if (dari != null) {
                predicate.add(cb.greaterThanOrEqualTo(root.get("waktu"), dari));
            }
            if (sampai != null) {
                predicate.add(cb.lessThan(root.get("waktu"), sampai));
            }
            return cb.and(predicate.toArray(Predicate[]::new));
        };
    }
}
