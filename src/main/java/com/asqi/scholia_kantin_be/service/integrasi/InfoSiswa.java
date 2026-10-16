package com.asqi.scholia_kantin_be.service.integrasi;

import lombok.Builder;
import lombok.Getter;

/**
 * Ringkasan satu siswa hasil pencarian (PRD §9.2 — "cari siswa (nama/NIS)").
 *
 * <p>Data siswa <b>milik {@code admin-be}</b> (kantin-be tidak menyimpan master
 * siswa). Karena itu pencarian dilakukan lewat port integrasi
 * {@link CariSiswaPort} — bukan query DB lokal.
 *
 * <p>{@code saldo} diisi dari ledger lokal kantin-be (bukan dari admin-be),
 * agar layar TU bisa langsung menampilkan sisa saldo kandidat.
 */
@Getter
@Builder
public class InfoSiswa {

    /** ID siswa (lokal kantin-be = id siswa di admin-be, dipakai sebagai subjek ledger). */
    private final Long siswaId;

    private final String nis;

    private final String nama;

    private final String kelas;

    /** Saldo berjalan (rupiah) dari ledger kantin-be. */
    private final long saldo;

    /** Siswa sudah tidak aktif (lulus/pindah/keluar) — untuk alur refund (§9.3). */
    private final Boolean tidakAktif;
}
