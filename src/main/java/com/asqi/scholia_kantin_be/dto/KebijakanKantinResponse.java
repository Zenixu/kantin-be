package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.model.KebijakanKantin;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Kebijakan kantin per sekolah (DEMO, OPEN-QUESTIONS Q16 / issue #25).
 */
@Data
@Builder
public class KebijakanKantinResponse {

    private Long sekolahId;
    private String kebijakanSaldoMengendap;
    private Integer ambangHari;
    private String catatan;
    private OffsetDateTime updatedAt;

    /** Proyeksi entitas → DTO. */
    public static KebijakanKantinResponse dari(KebijakanKantin k) {
        return KebijakanKantinResponse.builder()
                .sekolahId(k.getSekolahId())
                .kebijakanSaldoMengendap(k.getKebijakanSaldoMengendap())
                .ambangHari(k.getAmbangHari())
                .catatan(k.getCatatan())
                .updatedAt(k.getUpdatedAt())
                .build();
    }
}
