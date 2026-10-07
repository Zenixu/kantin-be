package com.asqi.scholia_kantin_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Profil perangkat &amp; postur kepatuhan kantin — solusi DEMO untuk pertanyaan
 * terblokir tim luar (#22/Q17, #24/Q15).
 *
 * <p><b>#22 (Q17):</b> spesifikasi reader RFID USB kasir. Asumsi demo: reader
 * kasir = <b>reader Kiosk Presensi</b> (mode keyboard-wedge/HID) sampai tim RFID
 * mengonfirmasi. Nilai bisa diubah via environment tanpa mengubah kode.
 *
 * <p><b>#24 (Q15):</b> postur regulasi — sistem diperlakukan sebagai
 * <b>dana titipan closed-loop</b> (bukan uang elektronik), tanpa tarik tunai /
 * transfer bebas. Tetap wajib konfirmasi legal sebelum rilis produksi.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "kantin.profil")
public class KantinProfilProperties {

    // ── #22 / Q17 — Spesifikasi reader RFID USB kasir ────────────────

    /** Benar bila reader kasir disamakan dengan reader Kiosk Presensi (asumsi demo). */
    private boolean readerSamaDenganKiosk = true;

    /** Mode koneksi reader: {@code KEYBOARD_WEDGE} (HID) / {@code WEBHID} / {@code SERIAL}. */
    private String readerMode = "KEYBOARD_WEDGE";

    /** Panjang UID yang diharapkan (karakter heksadesimal). */
    private int readerPanjangUid = 10;

    /** Target waktu baca UID (milidetik) — selaras SLO tap ≤1 dtk (PRD §11.8). */
    private int readerBacaMs = 150;

    /** Catatan bebas tentang reader (mis. merek/tipe) untuk tim RFID. */
    private String readerCatatan = "Asumsi demo: samakan dengan reader Kiosk Presensi (mode HID keyboard-wedge).";

    // ── #24 / Q15 — Postur regulasi dana titipan ────────────────────

    /** Model dana yang dipakai sistem (mis. {@code DANA_TITIPAN_CLOSED_LOOP}). */
    private String modelDana = "DANA_TITIPAN_CLOSED_LOOP";

    /** Benar bila sistem diklasifikasikan sebagai uang elektronik (harusnya false untuk closed-loop). */
    private boolean uangElektronik = false;

    /** Benar bila siswa/orang tua bisa menarik saldo jadi uang tunai (harusnya false). */
    private boolean tarikTunai = false;

    /** Benar bila saldo bisa ditransfer bebas antar siswa/sekolah (harusnya false). */
    private boolean transferBebas = false;

    /** Benar bila masih butuh konfirmasi legal sebelum rilis produksi. */
    private boolean perluKonfirmasiLegal = true;

    /** Catatan postur regulasi untuk tim legal. */
    private String legalCatatan =
            "Dana titipan closed-loop (hanya untuk pembelian di kantin sekolah). "
                    + "Tidak ada tarik tunai / transfer bebas. Wajib konfirmasi legal (Q15) sebelum produksi.";
}
