package com.asqi.scholia_kantin_be.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
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
 * Rincian item per transaksi — <b>snapshot</b> nama, harga jual, kategori &amp; HPP
 * saat transaksi (PRD §6.2, §7.4).
 *
 * <p>Snapshot penting agar laba &amp; riwayat lama tidak berubah ketika pengelola
 * mengubah harga/HPP di kemudian hari.
 */
@Entity
@Table(name = "transaksi_item")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransaksiItem implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    /** Penanda transient: id di-assign aplikasi → cegah merge() mencoba SELECT. */
    @Transient
    @Builder.Default
    private boolean baru = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaksi_id", nullable = false)
    private Transaksi transaksi;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    /** Snapshot nama menu saat transaksi. */
    @Column(name = "nama_menu", nullable = false, length = 150)
    private String namaMenu;

    /** Snapshot kategori saat transaksi (bisa null). */
    @Column(name = "kategori_id")
    private Long kategoriId;

    /** Snapshot harga jual per unit. */
    @Column(name = "harga_jual", nullable = false)
    private Long hargaJual;

    @Column(name = "qty", nullable = false)
    private Integer qty;

    /** Snapshot HPP per unit saat transaksi (PRD §7.4). */
    @Column(name = "hpp_snapshot", nullable = false)
    private Long hppSnapshot;

    /** {@code harga_jual × qty}. */
    @Column(name = "subtotal", nullable = false)
    private Long subtotal;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void isiWaktu() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    /** Hanya INSERT saat baru; setelah dimuat/di-persist tidak ada update. */
    @Override
    public boolean isNew() {
        return baru;
    }

    @PostPersist
    @PostLoad
    void tandaiSudahTersimpan() {
        this.baru = false;
    }
}
