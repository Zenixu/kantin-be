package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.model.BlokirItem;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/** Baris blokir item/kategori (PRD §8.3, issue #40). */
@Data
@Builder
public class BlokirItemResponse {

    private Long id;
    private String subjekTipe;
    private Long subjekId;
    private Long menuId;
    private Long kategoriId;
    private boolean diblokir;
    private OffsetDateTime diubahPada;

    /** Proyeksi entitas → DTO. */
    public static BlokirItemResponse dari(BlokirItem b) {
        return BlokirItemResponse.builder()
                .id(b.getId())
                .subjekTipe(b.getSubjekTipe() == null ? null : b.getSubjekTipe().name())
                .subjekId(b.getSubjekId())
                .menuId(b.getMenuId())
                .kategoriId(b.getKategoriId())
                .diblokir(b.isDiblokir())
                .diubahPada(b.getDiubahPada())
                .build();
    }
}
