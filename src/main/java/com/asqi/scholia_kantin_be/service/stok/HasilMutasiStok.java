package com.asqi.scholia_kantin_be.service.stok;

import com.asqi.scholia_kantin_be.model.MutasiStok;
import lombok.Builder;
import lombok.Getter;

/**
 * Hasil satu mutasi stok.
 *
 * <p>{@code hppSetelah} mengembalikan HPP rata-rata berjalan setelah mutasi
 * (relevan untuk barang masuk yang memperbarui HPP, PRD §7.4).
 */
@Getter
@Builder
public class HasilMutasiStok {

    private final MutasiStok mutasi;

    /** Stok setelah mutasi. */
    private final int stokSetelah;

    /** HPP rata-rata berjalan setelah mutasi. */
    private final long hppSetelah;

    public static HasilMutasiStok baru(MutasiStok mutasi, int stokSetelah, long hppSetelah) {
        return HasilMutasiStok.builder()
                .mutasi(mutasi)
                .stokSetelah(stokSetelah)
                .hppSetelah(hppSetelah)
                .build();
    }
}
