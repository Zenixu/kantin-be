package com.asqi.scholia_kantin_be.service;

import com.asqi.scholia_kantin_be.service.integrasi.CariSiswaPort;
import com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Pencarian siswa untuk layar TU/kantin (PRD §9.2) — issue #214.
 *
 * <p>Delegasi ke {@link CariSiswaPort}. Data siswa milik {@code admin-be};
 * kantin-be hanya memperkaya dengan saldo dari ledger lokal (dilakukan di
 * adapter port, lihat {@code CariSiswaDemo}).
 *
 * <p>Dipakai untuk: top-up tunai (memilih siswa), kontrol siswa (§9.6), dan
 * daftar kandidat refund siswa keluar (§9.3, {@code hanyaTidakAktif=true}).
 */
@Service
@RequiredArgsConstructor
public class CariSiswaService {

    /** Batas aman jumlah hasil agar satu permintaan tidak menarik ribuan baris. */
    public static final int BATAS_MAKS = 50;

    private final CariSiswaPort cariSiswaPort;

    /**
     * Cari siswa berdasarkan nama/NIS.
     *
     * @param sekolahId      tenant dari JWT
     * @param kataKunci      nama/NIS (boleh kosong)
     * @param hanyaTidakAktif hanya siswa nonaktif (untuk refund §9.3)
     * @param batas          jumlah maksimum (dikunci ke [1, {@value #BATAS_MAKS}])
     */
    public List<InfoSiswa> cari(Long sekolahId, String kataKunci, boolean hanyaTidakAktif, int batas) {
        int batasAman = Math.min(Math.max(batas, 1), BATAS_MAKS);
        return cariSiswaPort.cari(sekolahId, kataKunci, hanyaTidakAktif, batasAman);
    }
}
