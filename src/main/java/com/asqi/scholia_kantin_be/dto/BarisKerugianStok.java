package com.asqi.scholia_kantin_be.dto;

/**
 * Baris laporan kerugian stok (PRD §9.5): penyesuaian opname keluar &amp; barang
 * rusak, beserta nilainya (qty × HPP snapshot).
 *
 * @param jenis        {@code OPNAME_KELUAR} (selisih audit) / {@code BARANG_RUSAK}
 * @param jumlahBaris  banyak baris mutasi pada jenis tsb
 * @param totalQty     Σ qty yang hilang
 * @param totalNilai   Σ (qty × hpp_snapshot) — nilai kerugian (rupiah)
 */
public record BarisKerugianStok(
        String jenis,
        long jumlahBaris,
        long totalQty,
        long totalNilai) {
}
