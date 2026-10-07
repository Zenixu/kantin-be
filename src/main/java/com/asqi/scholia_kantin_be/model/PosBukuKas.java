package com.asqi.scholia_kantin_be.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;

/**
 * Pos Buku Kas yang dipakai kantin-be (DEMO, OPEN-QUESTIONS <b>Q8</b> / issue #21).
 *
 * <p><b>Fallback lokal:</b> sampai admin-be mengonfirmasi apakah pos
 * "Pendapatan Kantin" &amp; "Belanja Stok Kantin" dibuat otomatis saat modul
 * diaktifkan, kantin-be mendaftarkan sendiri pos yang ia butuhkan per sekolah
 * (idempoten lewat UNIQUE {@code (sekolah_id, nama)}). Bila Q8 terjawab,
 * posting tetap lewat {@code BukuKasPort}; tabel ini menjadi rujukan/cache.
 *
 * <p>Baris tidak diubah setelah dibuat (append-style): nilai ditetapkan sekali
 * via builder, tanpa setter publik.
 */
@Entity
@Table(name = "pos_buku_kas")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PosBukuKas implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    /** Tenant (sekolah) pemilik pos — wajib (PRD §11.4). */
    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    /** Nama pos (mis. {@code "Pendapatan Kantin"}). */
    @Column(name = "nama", nullable = false, length = 80)
    private String nama;

    /** Tipe pos: {@code MASUK} atau {@code KELUAR}. */
    @Column(name = "tipe", nullable = false, length = 10)
    private String tipe;

    @Column(name = "keterangan", length = 255)
    private String keterangan;

    @Column(name = "aktif", nullable = false)
    private boolean aktif;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void isiWaktu() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    /** Selalu {@code true}: baris pos hanya di-INSERT (seeding idempoten). */
    @Override
    public boolean isNew() {
        return true;
    }
}
