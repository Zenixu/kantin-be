package com.asqi.scholia_kantin_be.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Kategori item katalog (PRD §7.1).
 *
 * <p>Soft delete lewat {@code isActive}: kategori yang masih dipakai item hanya
 * boleh dinonaktifkan, tidak dihapus keras (FK {@code ON DELETE RESTRICT}).
 * Kategori menjadi dasar blokir per kategori oleh orang tua (PRD §8.3).
 *
 * <p>{@code id} di-assign aplikasi ({@code IdGenerator}), jadi memakai
 * {@link Persistable} agar Hibernate tahu INSERT vs UPDATE.
 */
@Entity
@Table(name = "kategori_menu")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KategoriMenu implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    /** Penanda transient: true = baru (INSERT), false = sudah dimuat (UPDATE). */
    @Transient
    @Builder.Default
    private boolean baru = true;

    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    @Column(name = "nama", nullable = false, length = 100)
    private String nama;

    /** Urutan tampil di kasir (kecil lebih dulu). */
    @Builder.Default
    @Column(name = "urutan", nullable = false)
    private Integer urutan = 0;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
