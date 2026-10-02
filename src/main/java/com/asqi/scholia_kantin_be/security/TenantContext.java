package com.asqi.scholia_kantin_be.security;

/**
 * Konteks tenant (sekolah) untuk request yang sedang diproses.
 *
 * <p>Diisi oleh {@code JwtAuthTokenFilter} di awal request dan <b>wajib</b>
 * dibersihkan di {@code finally} agar tidak bocor ke request lain pada thread
 * yang sama (Tomcat memakai thread pool).
 *
 * <p>Semua repository/query kantin <b>wajib</b> memakai
 * {@link #sekolahIdWajib()} untuk memaksa tenant scoping (PRD §11.4).
 * Bila konteks kosong → lempar {@link IllegalStateException} (fail-closed),
 * bukan diam-diam mengembalikan semua sekolah.
 */
public final class TenantContext {

    private static final ThreadLocal<IdentitasKantin> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    /** Set identitas untuk request ini. */
    public static void set(IdentitasKantin identitas) {
        HOLDER.set(identitas);
    }

    /** Ambil identitas (nullable) tanpa validasi. */
    public static IdentitasKantin get() {
        return HOLDER.get();
    }

    /**
     * ID sekolah wajib ada.
     *
     * @throws IllegalStateException bila konteks kosong atau sekolah tak diketahui
     */
    public static Long sekolahIdWajib() {
        IdentitasKantin id = HOLDER.get();
        if (id == null) {
            throw new IllegalStateException("TenantContext kosong — request belum terautentikasi");
        }
        if (id.getSekolahId() == null) {
            throw new IllegalStateException(
                    "Identitas tidak punya konteks sekolah. Token SKOOLIA belum membawa sekolah_id "
                            + "(lihat OPEN-QUESTIONS Q1/Q2).");
        }
        return id.getSekolahId();
    }

    /** Hapus konteks — SELALU panggil di blok finally filter. */
    public static void clear() {
        HOLDER.remove();
    }
}
