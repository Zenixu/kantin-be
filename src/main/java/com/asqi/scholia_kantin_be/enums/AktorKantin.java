package com.asqi.scholia_kantin_be.enums;

/**
 * Peran aktor kantin (RBAC).
 *
 * <p>Dipetakan dari {@code role} pada token SKOOLIA. Bila SKOOLIA mengirim
 * peran tak dikenal, jatuh ke {@link #PETUGAS_KANTIN} atau {@link #TIDAK_DIKENAL}
 * sesuai keputusan tim (lihat OPEN-QUESTIONS Q1).
 *
 * <p>Nilai disimpan sebagai string di konteks keamanan; jangan mengubah nama
 * tanpa ADR karena FE & koordinasi lintas-tim bergantung padanya.
 */
public enum AktorKantin {
    /** Petugas yang mengoperasikan layar kasir (tap). */
    PETUGAS_KANTIN,
    /** Pengelola kantin: pegang katalog, stok, HPP, laporan. */
    PENGELOLA_KANTIN,
    /** Tata usaha / bendahara sekolah: top-up tunai, setoran, koreksi. */
    TU_SEKOLAH,
    /** Admin sekolah: konfigurasi kantin, titik kasir, blokir. */
    ADMIN_SEKOLAH,
    /**
     * Kepala sekolah: <b>hanya baca</b> sebagian laporan (PRD §9.5). Sengaja
     * dipisah dari {@link #ADMIN_SEKOLAH} agar tidak mewarisi hak CRUD
     * (pengaturan &amp; titik kasir, blokir) — lihat ADR-0012 &amp; issue #123.
     */
    KEPSEK,
    /** Orang tua (dari mobile-be): hanya lihat riwayat anak, atur limit/blokir item. */
    ORANG_TUA,
    /** Fallback untuk peran SKOOLIA yang belum dipetakan. */
    TIDAK_DIKENAL
}
