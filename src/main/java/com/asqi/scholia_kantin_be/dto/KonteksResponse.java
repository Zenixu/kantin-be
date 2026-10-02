package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.enums.SumberToken;
import lombok.Builder;
import lombok.Data;

/**
 * Konteks pemanggil — untuk FE mengetahui siapa yang login & haknya.
 *
 * <p>Berguna saat pengembangan: FE kasir memakai ini untuk memutuskan layar
 * mana yang boleh dibuka (lapisan kenyamanan). <b>Keamanan tetap di backend</b>
 * (PRD §11.5) — jangan andalkan ini sebagai pengaman.
 */
@Data
@Builder
public class KonteksResponse {

    private String userId;
    private String nama;
    private Long sekolahId;
    private AktorKantin peran;
    private SumberToken sumber;
    private Long siswaId;

    /** Apakah token sudah membawa konteks sekolah (false = modul tenant tertutup). */
    private boolean punyaSekolah;
}
