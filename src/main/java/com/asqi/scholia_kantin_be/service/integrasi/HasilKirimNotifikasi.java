package com.asqi.scholia_kantin_be.service.integrasi;

/**
 * Hasil satu percobaan pengiriman notifikasi.
 *
 * <p>Tiga kemungkinan: benar-benar terkirim ({@link Status#TERKIRIM}),
 * sengaja dilewati karena integrasi belum dikonfigurasi / subjek bukan siswa
 * ({@link Status#DILEWATI} — mis. {@link NotifikasiFallback} aktif sebelum Q5),
 * atau gagal ({@link Status#GAGAL} — mis. mobile-be menolak/timeout).
 *
 * <p>Notifikasi bersifat <b>best-effort</b> (PRD §8.4): apa pun hasilnya,
 * transaksi/top-up/refund yang sudah tercatat <b>tidak</b> dibatalkan.
 */
public record HasilKirimNotifikasi(Status status, String pesan) {

    public enum Status {
        TERKIRIM,
        DILEWATI,
        GAGAL
    }

    public static HasilKirimNotifikasi terkirim(String pesan) {
        return new HasilKirimNotifikasi(Status.TERKIRIM, pesan);
    }

    public static HasilKirimNotifikasi dilewati(String pesan) {
        return new HasilKirimNotifikasi(Status.DILEWATI, pesan);
    }

    public static HasilKirimNotifikasi gagal(String pesan) {
        return new HasilKirimNotifikasi(Status.GAGAL, pesan);
    }

    public boolean terkirim() {
        return status == Status.TERKIRIM;
    }

    public boolean dilewati() {
        return status == Status.DILEWATI;
    }
}
