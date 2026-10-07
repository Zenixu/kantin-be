package com.asqi.scholia_kantin_be.service.integrasi;

/**
 * Port cek apakah sebuah {@code rfidUid} <b>sudah dipakai siswa</b> di admin-be
 * (PRD §4.3, §9.4; INTEGRATIONS.md §4.2; OPEN-QUESTIONS <b>Q7</b>; issue <b>#29</b>).
 *
 * <p><b>Kenapa port (interface)?</b> Sama seperti {@link KartuLookupPort} &
 * {@link StatusSiswaPort}: kontrak API internal admin-be belum final (Q7).
 * Anti-tabrakan UID bersifat <b>dua arah</b>:
 * <ul>
 *   <li>sisi kantin-be (sudah ada): tolak UID yang sudah dipakai Kartu Tamu lain
 *       ({@code KartuTamuRepository.existsByRfidUidExcluding});</li>
 *   <li>sisi kantin-be (port ini): tolak UID yang <b>sudah dipakai siswa</b> di
 *       admin-be — melengkapi arah sebaliknya;</li>
 *   <li>sisi admin-be (disediakan endpoint internal kantin-be): admin-be
 *       menanyakan apakah UID dipakai Kartu Tamu, agar {@code SiswaService}
 *       menolaknya.</li>
 * </ul>
 *
 * <p>Dengan memisahkan port, registrasi Kartu Tamu bisa ditulis, diuji (fake),
 * dan <b>tidak terblokir</b>. Saat Q7 terjawab, tambahkan implementasi nyata
 * (mis. {@code SiswaUidClient} REST) &amp; tandai {@code @Primary}.
 *
 * <p><b>Fail-open</b> (pola {@link StatusSiswaFallback}): bila integrasi belum
 * siap, kembalikan {@code null} (&ldquo;tidak diketahui&rdquo;) — kantin
 * <b>tidak</b> menolak registrasi karena integrasi absen; validasi lokal
 * (Kartu Tamu) tetap berjalan.
 */
public interface UidSiswaPort {

    /**
     * Apakah {@code rfidUid} sudah terdaftar sebagai kartu <b>siswa</b> di
     * sekolah ini?
     *
     * @param sekolahId tenant pemanggil (PRD §11.4)
     * @param rfidUid   UID yang mau dicek
     * @return {@code TRUE} dipakai siswa, {@code FALSE} tidak, {@code null} bila
     *         status tidak diketahui (integrasi belum siap — fail-safe)
     */
    Boolean dipakaiSiswa(Long sekolahId, String rfidUid);
}
