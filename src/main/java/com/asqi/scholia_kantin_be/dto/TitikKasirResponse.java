package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.model.TitikKasir;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/** Titik kasir (PRD §9.1, issue #42). */
@Data
@Builder
public class TitikKasirResponse {

    private Long id;
    private String nama;
    private String kode;
    private boolean aktif;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** Proyeksi entitas → DTO. */
    public static TitikKasirResponse dari(TitikKasir t) {
        return TitikKasirResponse.builder()
                .id(t.getId())
                .nama(t.getNama())
                .kode(t.getKode())
                .aktif(t.isAktif())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
