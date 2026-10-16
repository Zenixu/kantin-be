package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.PerluPeran;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.CariSiswaService;
import com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Pencarian siswa untuk layar TU/kantin (PRD §9.2) — issue <b>#214</b>.
 *
 * <p>Data siswa <b>milik admin-be</b> (kantin-be tidak menyimpan master siswa);
 * pencarian ini menjembatani lewat {@link CariSiswaService} →
 * {@code CariSiswaPort}. Saldo tiap hasil diambil dari ledger lokal kantin-be,
 * sehingga layar TU bisa langsung menampilkan sisa saldo.
 *
 * <p>Dipakai untuk: <b>top-up tunai</b> (memilih siswa), <b>kontrol siswa</b>
 * (§9.6), dan <b>kandidat refund</b> siswa keluar (§9.3,
 * {@code hanyaTidakAktif=true}).
 */
@RestController
@RequestMapping("api/siswa")
@RequiredArgsConstructor
public class SiswaController {

    private final CariSiswaService cariSiswaService;

    /**
     * Cari siswa berdasarkan nama/NIS dalam sekolah pemanggil (tenant dari JWT).
     *
     * @param q              kata kunci nama/NIS (boleh kosong → daftar awal)
     * @param hanyaTidakAktif {@code true} → hanya siswa nonaktif (refund §9.3)
     * @param batas          jumlah maksimum hasil (default 20, maks 50)
     */
    @PerluPeran({AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH,
            AktorKantin.PENGELOLA_KANTIN, AktorKantin.KEPSEK})
    @GetMapping
    public ResponseEntity<Response<List<InfoSiswa>>> cari(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean hanyaTidakAktif,
            @RequestParam(defaultValue = "20") int batas) {

        return CommonResponse.data(
                cariSiswaService.cari(TenantContext.sekolahIdWajib(), q, hanyaTidakAktif, batas));
    }
}
