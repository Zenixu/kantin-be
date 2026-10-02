package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.model.Menu;
import lombok.Builder;
import lombok.Data;

/**
 * Proyeksi item menu untuk respons HTTP — menyembunyikan kolom internal dan
 * menegaskan tipe (rupiah integer, status aktif).
 */
@Data
@Builder
public class MenuResponse {

    private Long id;
    private Long kategoriId;
    private String nama;
    private long hargaJual;
    private String satuan;
    private String fotoUrl;
    private int stokMinimum;
    private boolean aktif;

    /** Proyeksi entitas → DTO. */
    public static MenuResponse dari(Menu m) {
        return MenuResponse.builder()
                .id(m.getId())
                .kategoriId(m.getKategoriId())
                .nama(m.getNama())
                .hargaJual(m.getHargaJual())
                .satuan(m.getSatuan() == null ? null : m.getSatuan().name())
                .fotoUrl(m.getFotoUrl())
                .stokMinimum(m.getStokMinimum() == null ? 0 : m.getStokMinimum())
                .aktif(Boolean.TRUE.equals(m.getIsActive()))
                .build();
    }
}
