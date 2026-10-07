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
 * Blokir item/kategori per subjek — PRD §6.1 tahap 3, §8.3 (kontrol ortu).
 *
 * <p>Tepat satu dari {@code menuId} atau {@code kategoriId} terisi: blokir
 * bisa per item spesifik <b>atau</b> per kategori (PRD §8.3). Ditegakkan CHECK
 * di database.
 */
@Entity
@Table(name = "blokir_item")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlokirItem implements Persistable<Long> {

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

    /** Menu yang diblokir (atau {@code null} bila blokir per kategori). */
    @Column(name = "menu_id")
    private Long menuId;

    /** Kategori yang diblokir (atau {@code null} bila blokir per item). */
    @Column(name = "kategori_id")
    private Long kategoriId;

    @Column(name = "diblokir", nullable = false)
    private boolean diblokir;

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
