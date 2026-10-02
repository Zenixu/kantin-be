package com.asqi.scholia_kantin_be.service.integrasi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementasi <b>sementara</b> {@link KartuLookupPort} — fail-closed.
 *
 * <p>Kontrak API internal admin-be belum final (OPEN-QUESTIONS <b>Q7</b>).
 * Sampai itu terjawab, lookup mengembalikan "kartu tidak dikenal" sehingga
 * tap ditolak dengan pesan yang benar (PRD §6.1 tahap 1) — <b>bukan</b> crash
 * atau diam-diam meloloskan transaksi.
 *
 * <p>Ketika Q7 terjawab, tambahkan implementasi nyata (mis.
 * {@code SiswaKartuClient} berbasis REST) dan tandai sebagai {@code @Primary},
 * atau hapus kelas ini. Ingat: status blokir <b>tidak boleh</b> di-cache
 * (PRD §11.11).
 */
@Service
@Slf4j
public class KartuLookupFallback implements KartuLookupPort {

    @Override
    public InfoKartu cariBerdasarkanUid(Long sekolahId, String rfidUid) {
        log.warn("Lookup kartu belum dikonfigurasi (Q7). UID={} dianggap tidak dikenal.", rfidUid);
        return InfoKartu.tidakDikenal();
    }
}
