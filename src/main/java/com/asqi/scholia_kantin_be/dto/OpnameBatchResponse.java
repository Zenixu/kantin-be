package com.asqi.scholia_kantin_be.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Hasil lengkap <b>opname batch</b> (PRD §7.3): nomor berita acara, jumlah item
 * yang benar-benar mencatat mutasi, jumlah item tanpa selisih, dan rincian per
 * item.
 */
@Data
@Builder
public class OpnameBatchResponse {

    private String referensiId;

    /** Jumlah item yang menghasilkan mutasi (selisih ≠ 0). */
    private int jumlahBerubah;

    /** Jumlah item tanpa selisih (stok fisik = sistem, tidak ada mutasi). */
    private int jumlahTanpaSelisih;

    private List<OpnameBatchHasilItem> items;
}
