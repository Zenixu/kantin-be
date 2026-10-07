package com.asqi.scholia_kantin_be.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Status aktivasi modul kantin per sekolah (PRD §10, issue #42).
 *
 * <p>Milik <b>internal-be</b> (OPEN-QUESTIONS <b>Q6</b>). Kantin-be mengaksesnya
 * lewat {@code AktivasiModulPort}; sampai kontrak final, fallback mengembalikan
 * {@code diketahui=false} (fail-open: modul dianggap aktif agar kantin tetap
 * jalan — pola solusi demo repo).
 */
@Data
@Builder
public class AktivasiModulResponse {

    private Long sekolahId;

    /** Modul kantin aktif untuk sekolah ini? (fail-open: {@code true} bila tak diketahui). */
    private boolean aktif;

    /** Apakah status benar-benar diketahui (kontrak Q6 sudah terjawab)? */
    private boolean diketahui;

    /** Catatan/diagnostik (mis. alasan fallback). */
    private String catatan;

    /** Fee platform per top-up (rupiah), bila diketahui; {@code null} = tak diketahui. */
    private Long feeTopup;
}
