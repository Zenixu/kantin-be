package com.asqi.scholia_kantin_be.service.integrasi;

/**
 * Port pencatatan Buku Kas ke admin-be (SKOOLIA) — INTEGRATIONS.md §3.
 *
 * <p><b>Kenapa port (interface)?</b> Sama seperti {@link KartuLookupPort}:
 * kontrak final (endpoint &amp; payload) Buku Kas belum pasti
 * (OPEN-QUESTIONS <b>Q3</b>). Dengan memisahkan port, logika posting + idempotency
 * di {@code BukuKasPostingService} bisa ditulis, diuji (dengan fake), dan
 * <b>tidak terblokir</b>. Saat Q3 terjawab, cukup tambahkan implementasi nyata
 * (mis. {@code BukuKasRestClient} memakai {@code RestClient}) dan tandai
 * {@code @Primary} — tanpa menyentuh logika bisnis.
 *
 * <p>Semua panggilan lintas-sistem <b>wajib</b> lewat {@code service/integrasi}
 * (INTEGRATIONS.md pembuka) — tidak ada modul lain yang boleh memanggil admin-be
 * langsung.
 */
public interface BukuKasPort {

    /**
     * Catat satu entri Buku Kas.
     *
     * <p>Implementasi <b>tidak</b> boleh melempar untuk kondisi "belum
     * dikonfigurasi" — kembalikan {@link HasilPostingBukuKas#dilewati}. Lempar
     * exception hanya untuk kegagalan nyata, atau kembalikan
     * {@link HasilPostingBukuKas#gagal}.
     *
     * @param perintah data entri (sudah tenant-scoped oleh pemanggil)
     * @return hasil pencatatan
     */
    HasilPostingBukuKas catat(PerintahBukuKas perintah);
}
