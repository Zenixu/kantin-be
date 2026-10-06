package com.asqi.scholia_kantin_be.dto;

/**
 * Baris laporan penjualan per item atau per kategori (PRD §9.5).
 *
 * <p>{@code kunciId} = {@code menuId} (laporan per item) atau {@code kategoriId}
 * (laporan per kategori). {@code nilai} = Σ subtotal jual.
 */
public record BarisPenjualan(
        Long kunciId,
        String nama,
        long qty,
        long nilai) {
}
