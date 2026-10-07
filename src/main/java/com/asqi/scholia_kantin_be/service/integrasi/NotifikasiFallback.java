package com.asqi.scholia_kantin_be.service.integrasi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementasi <b>sementara</b> {@link NotifikasiPort} — fail-open (tidak crash).
 *
 * <p>Kontrak push notification mobile-be belum final (OPEN-QUESTIONS <b>Q5</b>).
 * Sampai itu terjawab, pengiriman <b>dilewati</b>
 * ({@link HasilKirimNotifikasi#dilewati}): transaksi, top-up, dan refund tetap
 * berhasil — hanya notifikasi ortu yang belum terkirim. Ini <b>bukan</b>
 * kegagalan.
 *
 * <p>Ketika Q5 terjawab, tambahkan implementasi nyata (mis.
 * {@code NotifikasiRestClient} berbasis {@code RestClient}) dan tandai
 * {@code @Primary}, atau hapus kelas ini. Implementasi nyata <b>wajib</b>
 * menangkap exception jaringan dan mengembalikan
 * {@link HasilKirimNotifikasi#gagal} agar kegagalan integrasi tidak membatalkan
 * operasi kantin (INTEGRATIONS.md §6).
 */
@Service
@Slf4j
public class NotifikasiFallback implements NotifikasiPort {

    @Override
    public HasilKirimNotifikasi kirim(PerintahNotifikasi perintah) {
        log.info("Notifikasi {} belum dikonfigurasi (Q5). sekolah={} subjek={}:{} dilewati.",
                perintah.getJenis(), perintah.getSekolahId(),
                perintah.getSubjekTipe(), perintah.getSubjekId());
        return HasilKirimNotifikasi.dilewati(
                "Integrasi notifikasi mobile-be belum dikonfigurasi (OPEN-QUESTIONS Q5)");
    }
}
