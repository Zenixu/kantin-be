package com.asqi.scholia_kantin_be.enums;

/**
 * Cara identitas kartu dikenali pada satu tap.
 *
 * <p>Berguna untuk audit & diagnostik reader RFID (mis. mendeteksi reader yang
 * mengirim UID terbalik/byte-reversed).
 */
public enum MetodeRequestKartu {
    /** UID kartu dikirim langsung dari bridge pembaca RFID. */
    UID,
    /** Kartu ditemukan lewat lookup nomor kartu (fallback bila UID gagal). */
    NOMOR_KARTU
}
