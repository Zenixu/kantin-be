package com.asqi.scholia_kantin_be.dto;

/**
 * Baris laporan stok (PRD §9.5): stok sekarang, nilai persediaan (stok × HPP),
 * dan penanda stok menipis.
 */
public record BarisStok(
        Long menuId,
        String nama,
        Long kategoriId,
        int stok,
        int stokMinimum,
        long hpp,
        long nilaiPersediaan,
        boolean menipis) {
}
