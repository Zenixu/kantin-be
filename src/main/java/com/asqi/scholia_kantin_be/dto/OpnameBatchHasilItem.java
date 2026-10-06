package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import lombok.Builder;
import lombok.Data;

/**
 * Ringkasan hasil <b>opname batch</b> (PRD §7.3) — satu baris per item yang
 * diproses. {@code jenis} {@code null} berarti item <b>tanpa selisih</b>
 * (stok fisik = stok sistem) sehingga tidak ada mutasi yang dicatat.
 */
@Data
@Builder
public class OpnameBatchHasilItem {

    private Long menuId;

    /** Stok sistem sebelum penyesuaian. */
    private int stokSebelum;

    /** Stok fisik (target setelah penyesuaian). */
    private int stokFisik;

    /** {@code stokFisik − stokSebelum} (negatif = berkurang). */
    private int selisih;

    /**
     * Jenis mutasi yang dicatat; {@code null} bila selisih = 0 (tak ada mutasi).
     */
    private JenisMutasiStok jenis;

    /** ID baris ledger yang dibuat; {@code null} bila tidak ada mutasi. */
    private Long mutasiId;

    /** Stok setelah penyesuaian (= stokFisik). */
    private int stokSetelah;
}
