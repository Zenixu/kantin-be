package com.asqi.scholia_kantin_be.service.integrasi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fallback default {@link CariSiswaPort} saat integrasi data siswa (Q7) belum
 * dikonfigurasi — mengembalikan daftar <b>kosong</b> (fail-open, tidak error).
 *
 * <p>Dipakai bersama {@code CariSiswaDemo} (profil demo) yang menandai
 * {@code @Primary} saat aktif; lihat pola {@code KartuLookupFallback}.
 */
@Service
@Slf4j
public class CariSiswaFallback implements CariSiswaPort {

    @Override
    public List<InfoSiswa> cari(Long sekolahId, String kataKunci, boolean hanyaTidakAktif, int batas) {
        log.debug("Pencarian siswa belum dikonfigurasi (Q7). sekolah={} q={} → daftar kosong.",
                sekolahId, kataKunci);
        return List.of();
    }
}
