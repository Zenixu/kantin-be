package com.asqi.scholia_kantin_be.security;

import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import org.springframework.stereotype.Component;

/**
 * Penjaga tenant — memastikan operasi hanya menyentuh data sekolah pemanggil.
 *
 * <p><b>Aturan emas (PRD §11.4):</b> data milik sekolah lain dijawab
 * <b>404</b>, bukan 403. Tujuannya agar keberadaan data sekolah lain tidak
 * bocor (tidak bisa ditebak via perbedaan status).
 *
 * <p>Semua service yang menerima ID milik tenant (mis. {@code siswaId},
 * {@code transaksiId}) <b>wajib</b> memanggil {@link #pastikanMilikSekolah}
 * setelah memuat entitas — sebelum melakukan apa pun.
 */
@Component
public class SekolahGuard {

    /**
     * Pastikan {@code sekolahIdEntitas} sama dengan tenant pemanggil.
     *
     * @param sekolahIdEntitas sekolah pemilik data
     * @param entitas          nama entitas untuk pesan (mis. "Transaksi")
     * @throws NotFoundEntity bila berbeda (sengaja 404, bukan 403)
     */
    public void pastikanMilikSekolah(Long sekolahIdEntitas, String entitas) {
        Long tenant = TenantContext.sekolahIdWajib();
        if (sekolahIdEntitas == null || !tenant.equals(sekolahIdEntitas)) {
            throw new NotFoundEntity(entitas + " tidak ditemukan");
        }
    }

    /** Sekolah pemanggil (wajib ada). */
    public Long sekolahPemanggil() {
        return TenantContext.sekolahIdWajib();
    }
}
