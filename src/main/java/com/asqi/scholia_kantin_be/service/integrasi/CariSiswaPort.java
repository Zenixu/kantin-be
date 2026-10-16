package com.asqi.scholia_kantin_be.service.integrasi;

import java.util.List;

/**
 * Port pencarian siswa (PRD §9.2) — jembatan ke data siswa milik {@code admin-be}.
 *
 * <p>Kantin-be <b>tidak menyimpan master siswa</b>; yang dimiliki hanya ledger
 * saldo (per {@code subjekId}). Pencarian nama/NIS harus bertanya ke SKOOLIA.
 * Karena kontrak nyatanya belum final (Q7, lihat #164), port ini punya:
 * <ul>
 *   <li>{@link CariSiswaFallback} — default aman (daftar kosong) saat belum dikonfigurasi;</li>
 *   <li>{@code CariSiswaDemo} — data tiruan saat profil {@code demo} aktif, agar
 *       FE/alur TU bisa diuji tanpa data SKOOLIA.</li>
 * </ul>
 *
 * <p>Implementasi REST nyata ditambahkan saat kontrak Q7 admin-be tersedia
 * (pola sama seperti {@link KartuLookupPort}).
 */
public interface CariSiswaPort {

    /**
     * Cari siswa dalam satu sekolah berdasarkan kata kunci (nama atau NIS).
     *
     * @param sekolahId tenant dari JWT (wajib — data lintas sekolah tidak boleh bocor)
     * @param kataKunci teks pencarian (nama/NIS); boleh kosong → daftar awal
     * @param hanyaTidakAktif bila {@code true}, hanya siswa nonaktif (untuk refund §9.3)
     * @param batas     jumlah maksimum hasil
     * @return daftar siswa (bisa kosong; tidak pernah {@code null})
     */
    List<InfoSiswa> cari(Long sekolahId, String kataKunci, boolean hanyaTidakAktif, int batas);
}
