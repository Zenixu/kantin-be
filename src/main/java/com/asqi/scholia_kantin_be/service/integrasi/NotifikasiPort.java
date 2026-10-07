package com.asqi.scholia_kantin_be.service.integrasi;

/**
 * Port pengiriman notifikasi ke orang tua via mobile-be — INTEGRATIONS.md §6.
 *
 * <p><b>Kenapa port (interface)?</b> Sama seperti {@link BukuKasPort}: kontrak
 * (endpoint &amp; format) push notification mobile-be belum final
 * (OPEN-QUESTIONS <b>Q5</b>; repo mobile-be belum ada di clone). Dengan
 * memisahkan port, seluruh alur transaksi/top-up/refund bisa menembakkan
 * notifikasi, diuji (dengan fake), dan <b>tidak terblokir</b>. Saat Q5 terjawab,
 * cukup tambahkan implementasi nyata (mis. {@code NotifikasiRestClient} memakai
 * {@code RestClient}) dan tandai {@code @Primary} — tanpa menyentuh logika
 * bisnis.
 *
 * <p>Semua panggilan lintas-sistem <b>wajib</b> lewat {@code service/integrasi}
 * (INTEGRATIONS.md pembuka) — tidak ada modul lain yang boleh memanggil
 * mobile-be langsung.
 *
 * <p><b>Kontrak fail-open (PRD §8.4):</b> implementasi <b>tidak</b> boleh
 * melempar untuk kondisi "belum dikonfigurasi" — kembalikan
 * {@link HasilKirimNotifikasi#dilewati}. Lempar exception hanya untuk kegagalan
 * nyata, atau kembalikan {@link HasilKirimNotifikasi#gagal}; pemanggil
 * ({@link NotifikasiService}) tetap menangkapnya agar transaksi tidak batal.
 */
public interface NotifikasiPort {

    /**
     * Kirim satu notifikasi.
     *
     * @param perintah data notifikasi (sudah tenant-scoped oleh pemanggil)
     * @return hasil pengiriman
     */
    HasilKirimNotifikasi kirim(PerintahNotifikasi perintah);
}
