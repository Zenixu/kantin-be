package com.asqi.scholia_kantin_be.service.integrasi;

/**
 * Hasil satu percobaan pencatatan Buku Kas.
 *
 * <p>Tiga kemungkinan: benar-benar tercatat ({@link Status#SUKSES}), sengaja
 * dilewati karena integrasi belum dikonfigurasi ({@link Status#DILEWATI} — mis.
 * {@link BukuKasFallback} aktif sebelum Q3), atau gagal ({@link Status#GAGAL} —
 * mis. admin-be menolak/timeout; pemanggil boleh mencoba lagi nanti).
 */
public record HasilPostingBukuKas(Status status, String referensi, String pesan) {

    public enum Status {
        SUKSES,
        DILEWATI,
        GAGAL
    }

    public static HasilPostingBukuKas sukses(String referensi, String pesan) {
        return new HasilPostingBukuKas(Status.SUKSES, referensi, pesan);
    }

    public static HasilPostingBukuKas dilewati(String pesan) {
        return new HasilPostingBukuKas(Status.DILEWATI, null, pesan);
    }

    public static HasilPostingBukuKas gagal(String pesan) {
        return new HasilPostingBukuKas(Status.GAGAL, null, pesan);
    }

    public boolean sukses() {
        return status == Status.SUKSES;
    }

    public boolean dilewati() {
        return status == Status.DILEWATI;
    }
}
