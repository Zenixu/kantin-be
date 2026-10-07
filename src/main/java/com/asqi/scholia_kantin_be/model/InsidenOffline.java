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
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;

/**
 * Insiden offline kasir (DEMO, OPEN-QUESTIONS <b>Q14</b> / issue #23).
 *
 * <p>Bagian dari <b>prosedur darurat</b> (ADR-0006): saat internet/server mati,
 * kasir menampilkan banner offline &amp; petugas mencatat insiden (waktu, titik,
 * durasi, kronologi). Data ini dipakai sekolah untuk memutuskan apakah mode
 * offline penuh perlu dimajukan.
 *
 * <p>{@link Immutable} + trigger DB ({@code trg_insiden_offline_append_only})
 * memastikan Hibernate tidak pernah menerbitkan {@code UPDATE}/{@code DELETE}
 * (append-only, PRD §11.1).
 */
@Entity
@Table(name = "insiden_offline")
@Immutable
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InsidenOffline implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    /** Tenant (sekolah) pemilik baris — wajib (PRD §11.4). */
    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    /** Titik kasir terdampak (boleh null bila seluruh kantin). */
    @Column(name = "titik_kasir_id")
    private Long titikKasirId;

    /** Waktu mulai gangguan. */
    @Column(name = "mulai", nullable = false)
    private OffsetDateTime mulai;

    /** Waktu pulih; {@code null} bila masih berlangsung. */
    @Column(name = "selesai")
    private OffsetDateTime selesai;

    /** Durasi gangguan (menit); diisi saat {@link #selesai} terisi. */
    @Column(name = "durasi_menit")
    private Integer durasiMenit;

    /** Kronologi singkat gangguan. */
    @Column(name = "keterangan", nullable = false, length = 500)
    private String keterangan;

    /** Aktor pelapor (audit). */
    @Column(name = "dilaporkan_oleh")
    private Long dilaporkanOleh;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void isiWaktu() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    /** Selalu {@code true}: insiden hanya INSERT (append-only). */
    @Override
    public boolean isNew() {
        return true;
    }
}
