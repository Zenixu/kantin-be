package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.model.Menu;
import lombok.Builder;
import lombok.Data;

/**
 * Proyeksi item menu untuk respons HTTP — menyembunyikan kolom internal dan
 * menegaskan tipe (rupiah integer, status aktif).
 *
 * <p><b>stokBerjalan</b> diisi dari {@code stok_cache} (bukan kolom {@code menu})
 * agar FE bisa menampilkan total stok per item pada tabel katalog <b>tanpa</b>
 * memanggil {@code GET /api/stok/{menuId}} satu per satu (hindari N+1).
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

    /** Stok berjalan dari {@code stok_cache} (0 bila belum ada baris stok). */
    private int stokBerjalan;

    private boolean aktif;

    /** Proyeksi entitas → DTO tanpa stok (stok dianggap 0). */
    public static MenuResponse dari(Menu m) {
        return dari(m, 0);
    }

    /** Proyeksi entitas → DTO sekaligus menyertakan stok berjalan. */
    public static MenuResponse dari(Menu m, int stokBerjalan) {
        return MenuResponse.builder()
                .id(m.getId())
                .kategoriId(m.getKategoriId())
                .nama(m.getNama())
                .hargaJual(m.getHargaJual())
                .satuan(m.getSatuan() == null ? null : m.getSatuan().name())
                .fotoUrl(m.getFotoUrl())
                .stokMinimum(m.getStokMinimum() == null ? 0 : m.getStokMinimum())
                .stokBerjalan(stokBerjalan)
                .aktif(Boolean.TRUE.equals(m.getIsActive()))
                .build();
    }
}
