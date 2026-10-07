package com.asqi.scholia_kantin_be.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Profil perangkat &amp; postur kepatuhan kantin (DEMO, #22/Q17 &amp; #24/Q15).
 *
 * <p>Menyatukan dua asumsi demo dalam satu respons:
 * <ul>
 *   <li><b>Reader RFID USB (#22/Q17)</b> — diasumsikan sama dengan reader Kiosk
 *       Presensi (mode keyboard-wedge/HID) sampai tim RFID mengonfirmasi.</li>
 *   <li><b>Postur regulasi (#24/Q15)</b> — dana titipan <b>closed-loop</b>,
 *       bukan uang elektronik, tanpa tarik tunai/transfer bebas; tetap butuh
 *       konfirmasi legal sebelum produksi.</li>
 * </ul>
 */
@Data
@Builder
public class ProfilKantinResponse {

    // #22 / Q17 — reader RFID USB kasir
    private boolean readerSamaDenganKiosk;
    private String readerMode;
    private int readerPanjangUid;
    private int readerBacaMs;
    private String readerCatatan;

    // #24 / Q15 — postur regulasi dana titipan
    private String modelDana;
    private boolean uangElektronik;
    private boolean tarikTunai;
    private boolean transferBebas;
    private boolean perluKonfirmasiLegal;
    private String legalCatatan;
}
