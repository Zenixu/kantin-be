package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.model.SetoranTu;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Hasil konfirmasi setoran kas TU (PRD §9.2, issue #39).
 *
 * <p>Menyertakan {@code selisih} ({@code totalTopup − jumlahDisetor}). Nilai
 * ini juga dihitung database sebagai kolom GENERATED — DTO memuat nilai yang
 * sama agar pemanggil langsung menerimanya tanpa membaca ulang.
 */
@Data
@Builder
public class SetoranTuResponse {

    private Long id;
    private LocalDate tanggal;
    private Long petugasId;
    private long totalTopup;
    private long jumlahDisetor;

    /** {@code totalTopup − jumlahDisetor}. Positif = kurang setor. */
    private long selisih;

    private String referensiId;
    private String catatan;
    private Long dikonfirmasiOleh;
    private OffsetDateTime waktu;

    /** Proyeksi entitas → DTO; {@code selisih} dihitung dari komponen. */
    public static SetoranTuResponse dari(SetoranTu s) {
        long total = s.getTotalTopup() == null ? 0L : s.getTotalTopup();
        long disetor = s.getJumlahDisetor() == null ? 0L : s.getJumlahDisetor();
        return SetoranTuResponse.builder()
                .id(s.getId())
                .tanggal(s.getTanggal())
                .petugasId(s.getPetugasId())
                .totalTopup(total)
                .jumlahDisetor(disetor)
                .selisih(total - disetor)
                .referensiId(s.getReferensiId())
                .catatan(s.getCatatan())
                .dikonfirmasiOleh(s.getDikonfirmasiOleh())
                .waktu(s.getWaktu())
                .build();
    }
}
