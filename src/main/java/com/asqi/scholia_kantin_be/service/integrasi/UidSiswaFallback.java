package com.asqi.scholia_kantin_be.service.integrasi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementasi <b>sementara</b> {@link UidSiswaPort} — fail-safe.
 *
 * <p>Kontrak API internal admin-be belum final (OPEN-QUESTIONS <b>Q7</b>).
 * Sampai itu terjawab, {@link #dipakaiSiswa} mengembalikan {@code null}
 * (&ldquo;tidak diketahui&rdquo;) sehingga registrasi Kartu Tamu <b>tidak</b>
 * diblokir keliru. Validasi lokal (UID unik antar Kartu Tamu) tetap berjalan,
 * dan sisi admin-be dilindungi endpoint internal kantin-be
 * ({@code GET /api/internal/kartu-tamu/cek-uid}, issue #29).
 *
 * <p>Ketika Q7 terjawab, tambahkan implementasi nyata (mis.
 * {@code SiswaUidClient} berbasis {@code RestClient}) dan tandai
 * {@code @Primary}, atau hapus kelas ini.
 */
@Service
@Slf4j
public class UidSiswaFallback implements UidSiswaPort {

    @Override
    public Boolean dipakaiSiswa(Long sekolahId, String rfidUid) {
        log.debug("Cek UID siswa belum dikonfigurasi (Q7). UID={} dianggap tidak diketahui.",
                rfidUid);
        return null;
    }
}
