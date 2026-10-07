package com.asqi.scholia_kantin_be.service.integrasi;

import lombok.Builder;
import lombok.Getter;

/**
 * Objek nilai status aktivasi modul kantin &amp; fee platform (PRD §10, Q6).
 *
 * <p>Menyembunyikan ketergantungan pada kontrak final internal-be: service
 * kantin tidak peduli dari mana data berasal.
 */
@Getter
@Builder
public class AktivasiModulInfo {

    /** Modul kantin aktif untuk sekolah ini? (fail-open: {@code true} bila tak diketahui). */
    private final boolean aktif;

    /** Apakah status benar-benar diketahui (kontrak Q6 sudah terjawab)? */
    private final boolean diketahui;

    /** Fee platform per top-up (rupiah), bila diketahui; {@code null} = tak diketahui. */
    private final Long feeTopup;

    /** Catatan/diagnostik. */
    private final String catatan;

    /** Fallback: status tak diketahui → modul dianggap AKTIF (fail-open). */
    public static AktivasiModulInfo tidakDiketahui(String catatan) {
        return AktivasiModulInfo.builder()
                .aktif(true)
                .diketahui(false)
                .catatan(catatan)
                .build();
    }
}
