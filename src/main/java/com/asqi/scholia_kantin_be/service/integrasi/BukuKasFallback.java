package com.asqi.scholia_kantin_be.service.integrasi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementasi <b>sementara</b> {@link BukuKasPort} — fail-safe (tidak crash).
 *
 * <p>Kontrak final Buku Kas admin-be belum pasti (OPEN-QUESTIONS <b>Q3</b>).
 * Sampai itu terjawab, posting <b>dilewati</b> ({@link HasilPostingBukuKas#dilewati}):
 * penutupan sesi kasir tetap berhasil &amp; idempoten, hanya entri Buku Kas yang
 * belum dibuat. Ini <b>bukan</b> kegagalan — pemanggil menyimpan flag
 * {@code posting_buku_kas=false} sehingga bisa di-<i>retry</i> setelah Q3
 * terjawab (mis. lewat endpoint posting ulang).
 *
 * <p><b>Penting:</b> status blokir/kartu &amp; data ledger tetap di kantin-be;
 * kelas ini hanya menyangkut pencatatan ke Buku Kas sekolah.
 *
 * <p>Ketika Q3 terjawab, tambahkan implementasi nyata (mis.
 * {@code BukuKasRestClient} berbasis {@code RestClient}) dan tandai
 * {@code @Primary}, atau hapus kelas ini. Implementasi nyata <b>wajib</b>
 * menangkap exception jaringan dan mengembalikan {@link HasilPostingBukuKas#gagal}
 * agar kegagalan integrasi tidak membatalkan operasi kasir (INTEGRATIONS.md §3.4).
 */
@Service
@Slf4j
public class BukuKasFallback implements BukuKasPort {

    @Override
    public HasilPostingBukuKas catat(PerintahBukuKas perintah) {
        log.warn("Posting Buku Kas belum dikonfigurasi (Q3). refId={} (jumlah={}) dilewati.",
                perintah.getRefId(), perintah.getJumlah());
        return HasilPostingBukuKas.dilewati("Integrasi Buku Kas belum dikonfigurasi (OPEN-QUESTIONS Q3)");
    }
}
