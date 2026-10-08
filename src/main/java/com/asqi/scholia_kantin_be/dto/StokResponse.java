package com.asqi.scholia_kantin_be.dto;

import lombok.Builder;
import lombok.Data;

/** Ringkasan stok &amp; HPP berjalan satu menu (PRD §7). */
@Data
@Builder
public class StokResponse {

    private Long menuId;
    private int stok;
    private int stokMinimum;
    private long hpp;

    /** Nilai persediaan = stok × HPP (rupiah). */
    private long nilaiPersediaan;

    /** true bila stok ≤ stokMinimum (perlu restock). */
    private boolean menipis;

    /**
     * Penanda eksplisit "Habis" (PRD §6.1/§7.5): {@code true} bila
     * {@link #stok} == 0. Berbeda dari {@link #menipis} (stok ≤ minimum) —
     * item habis tidak dapat ditambahkan ke transaksi.
     */
    private boolean habis;
}
