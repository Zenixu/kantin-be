package com.asqi.scholia_kantin_be.model;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;

/**
 * Limit belanja harian per subjek — PRD §6.1 tahap 5, §8.3.
 *
 * <p>{@code nominal} = batas belanja per hari (rupiah integer). {@code null}
 * berarti <b>tanpa limit</b>. Hari direset pukul 00:00 zona sekolah
 * (PRD §11.9) — perhitungan "belanja hari ini" dilakukan ledger, bukan di sini.
 *
 * <p>Perubahan limit berlaku <b>seketika</b> (PRD §8.3) karena dibaca server
 * tiap tap.
 */
@Entity
@Table(name = "limit_harian")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LimitHarian implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Transient
    @Builder.Default
    private boolean baru = true;

    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subjek_tipe", nullable = false, length = 20)
    private SubjekTipe subjekTipe;

    @Column(name = "subjek_id", nullable = false)
    private Long subjekId;

    /** Batas belanja harian (rupiah); {@code null} = tanpa limit. */
    @Column(name = "nominal")
    private Long nominal;

    @Column(name = "diubah_oleh")
    private Long diubahOleh;

    @Column(name = "diubah_pada", nullable = false)
    private OffsetDateTime diubahPada;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return baru;
    }

    @PostLoad
    @PostPersist
    void tandaiLama() {
        this.baru = false;
    }
}
