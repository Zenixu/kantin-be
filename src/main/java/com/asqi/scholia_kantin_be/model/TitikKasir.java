package com.asqi.scholia_kantin_be.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Titik kasir — satu perangkat kasir (PRD §6.6). Satu kantin bisa punya banyak.
 */
@Entity
@Table(name = "titik_kasir")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TitikKasir {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    @Column(name = "nama", nullable = false, length = 100)
    private String nama;

    /** Kode unik per sekolah (opsional). */
    @Column(name = "kode", length = 30)
    private String kode;

    @Column(name = "is_active", nullable = false)
    private boolean aktif;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
