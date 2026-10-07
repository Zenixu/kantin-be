package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.model.InsidenOffline;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Insiden offline kasir (DEMO, OPEN-QUESTIONS Q14 / issue #23).
 */
@Data
@Builder
public class InsidenOfflineResponse {

    private Long id;
    private Long titikKasirId;
    private OffsetDateTime mulai;
    private OffsetDateTime selesai;
    private Integer durasiMenit;
    private String keterangan;
    private Long dilaporkanOleh;

    /** Proyeksi entitas → DTO. */
    public static InsidenOfflineResponse dari(InsidenOffline i) {
        return InsidenOfflineResponse.builder()
                .id(i.getId())
                .titikKasirId(i.getTitikKasirId())
                .mulai(i.getMulai())
                .selesai(i.getSelesai())
                .durasiMenit(i.getDurasiMenit())
                .keterangan(i.getKeterangan())
                .dilaporkanOleh(i.getDilaporkanOleh())
                .build();
    }
}
