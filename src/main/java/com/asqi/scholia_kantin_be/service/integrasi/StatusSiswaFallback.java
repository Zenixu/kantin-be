package com.asqi.scholia_kantin_be.service.integrasi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementasi <b>sementara</b> {@link StatusSiswaPort} — fail-safe.
 *
 * <p>Kontrak API internal admin-be belum final (OPEN-QUESTIONS <b>Q7</b>).
 * Sampai itu terjawab:
 * <ul>
 *   <li>{@link #tidakAktif} mengembalikan {@code null} (&ldquo;tidak
 *       diketahui&rdquo;) — daftar kandidat refund tetap tampil (tanpa filter
 *       status), dan pemindahan ke saudara tidak diblokir keliru.</li>
 *   <li>{@link #blokirKartu} hanya mencatat log (dilewati) — saldo tetap
 *       tercatat benar di ledger; pemblokiran kartu fisik menyusul setelah Q7.</li>
 * </ul>
 * Ini <b>bukan</b> kegagalan: refund/pindah saldo tetap sah &amp; idempoten.
 *
 * <p>Ketika Q7 terjawab, tambahkan implementasi nyata (mis.
 * {@code SiswaKartuClient} berbasis {@code RestClient}) dan tandai
 * {@code @Primary}, atau hapus kelas ini.
 */
@Service
@Slf4j
public class StatusSiswaFallback implements StatusSiswaPort {

    @Override
    public Boolean tidakAktif(Long sekolahId, Long siswaId) {
        log.warn("Status siswa belum dikonfigurasi (Q7). siswaId={} dianggap tidak diketahui.", siswaId);
        return null;
    }

    @Override
    public void blokirKartu(Long sekolahId, Long siswaId, String alasan) {
        log.warn("Blokir kartu siswa belum dikonfigurasi (Q7). siswaId={} dilewati. alasan={}",
                siswaId, alasan);
    }
}
