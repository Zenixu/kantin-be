package com.asqi.scholia_kantin_be.service.integrasi;

/**
 * Port status <b>aktivasi modul kantin &amp; fee platform</b> ke internal-be
 * (PRD §10, OPEN-QUESTIONS <b>Q6</b>).
 *
 * <p><b>Kenapa port (interface)?</b> Sama seperti {@link BukuKasPort}: kontrak
 * API internal-be (endpoint &amp; payload) belum final. Toggle aktivasi modul
 * &amp; konfigurasi fee platform adalah <b>milik internal-be</b> — kantin-be
 * tidak menyimpan/menduplikasinya. Dengan memisahkan port, kantin-be bisa
 * menanyakan status aktivasi &amp; fee tanpa terblokir; saat Q6 terjawab, cukup
 * tambahkan implementasi nyata (mis. {@code AktivasiModulRestClient}) dan tandai
 * {@code @Primary}.
 *
 * <p>Semua panggilan lintas-sistem <b>wajib</b> lewat {@code service/integrasi}
 * (INTEGRATIONS.md pembuka).
 */
public interface AktivasiModulPort {

    /**
     * Status aktivasi modul kantin &amp; fee platform satu sekolah.
     *
     * <p>Implementasi <b>tidak</b> boleh melempar untuk kondisi "belum
     * dikonfigurasi" — kembalikan hasil dengan {@code diketahui=false}. Lempar
     * exception hanya untuk kegagalan nyata; pemanggil (fail-open) tetap
     * menganggap modul aktif agar kantin tidak mati karena integrasi.
     */
    AktivasiModulInfo status(Long sekolahId);
}
