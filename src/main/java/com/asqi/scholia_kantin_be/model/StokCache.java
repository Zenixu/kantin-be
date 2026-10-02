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
 * Stok &amp; HPP berjalan per menu — <b>hot row</b> yang dikunci
 * ({@code SELECT ... FOR UPDATE}) saat penjualan (ADR-0003).
 *
 * <p>Turunan yang selalu dapat dihitung ulang dari {@link MutasiStok}.
 * {@code hpp} = HPP rata-rata tertimbang berjalan (PRD §7.4).
 */
@Entity
@Table(name = "stok_cache")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StokCache {

    @Id
    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    /** Stok berjalan (integer). Tidak boleh minus (CHECK di DB). */
    @Column(name = "stok", nullable = false)
    private Integer stok;

    /** HPP rata-rata tertimbang berjalan (rupiah integer). */
    @Column(name = "hpp", nullable = false)
    private Long hpp;

    /** Ambang peringatan "stok menipis" (PRD §7.5). */
    @Column(name = "stok_minimum", nullable = false)
    private Integer stokMinimum;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
