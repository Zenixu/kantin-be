package com.asqi.scholia_kantin_be.dto;

/**
 * Baris laporan stok (PRD §9.5): stok sekarang, nilai persediaan (stok × HPP),
 * dan penanda stok menipis.
 *
 * <p><b>Alias kontrak FE (issue #98):</b> {@code namaMenu} = {@code nama} dan
 * {@code stokBerjalan} = {@code stok} — disertakan agar FE (halaman Laporan
 * Inventaris) bisa memakai {@code GET /api/laporan/stok} tanpa mapping ulang.
 * Field lama tetap ada (aditif / non-breaking).
 */
public record BarisStok(
        Long menuId,
        String nama,
        Long kategoriId,
        int stok,
        int stokMinimum,
        long hpp,
        long nilaiPersediaan,
        boolean menipis,
        String namaMenu,
        int stokBerjalan) {
}
