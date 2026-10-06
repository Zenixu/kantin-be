package com.asqi.scholia_kantin_be.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Halaman data generik untuk respons daftar (mis. riwayat stok).
 *
 * <p>Ringkas &amp; stabil untuk FE: {@code items} + metadata halaman. Dipakai
 * alih-alih menyerahkan objek {@code Page} Spring langsung agar kontrak JSON
 * tidak berubah bila internal Spring berubah.
 *
 * @param <T> tipe item
 */
@Data
@Builder
public class HalamanResponse<T> {

    private List<T> items;
    private long total;
    private int halaman;
    private int ukuran;
    private int totalHalaman;

    /** Bangun dari {@code Page} sumber, memetakan entitas → DTO. */
    public static <E, T> HalamanResponse<T> dari(Page<E> page, Function<E, T> mapper) {
        return HalamanResponse.<T>builder()
                .items(page.getContent().stream().map(mapper).toList())
                .total(page.getTotalElements())
                .halaman(page.getNumber())
                .ukuran(page.getSize())
                .totalHalaman(page.getTotalPages())
                .build();
    }
}
