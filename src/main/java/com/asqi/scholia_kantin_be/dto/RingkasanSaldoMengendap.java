package com.asqi.scholia_kantin_be.dto;

/**
 * Ringkasan dana titipan (kewajiban sekolah) — laporan "Saldo mengendap"
 * (PRD §9.5): total saldo siswa + Kartu Tamu yang belum dibelanjakan.
 *
 * @param saldoSiswa     Σ saldo semua siswa (uang titipan ortu)
 * @param saldoKartuTamu Σ saldo semua Kartu Tamu
 * @param total          saldoSiswa + saldoKartuTamu (kewajiban sekolah)
 * @param jumlahSiswa    banyak subjek siswa yang bersaldo (≥ 0 baris cache)
 * @param jumlahKartuTamu banyak Kartu Tamu bersaldo
 */
public record RingkasanSaldoMengendap(
        long saldoSiswa,
        long saldoKartuTamu,
        long total,
        long jumlahSiswa,
        long jumlahKartuTamu) {
}
