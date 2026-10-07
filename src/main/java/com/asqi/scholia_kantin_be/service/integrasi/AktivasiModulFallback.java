package com.asqi.scholia_kantin_be.service.integrasi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementasi <b>sementara</b> {@link AktivasiModulPort} — fail-open.
 *
 * <p>Kontrak API internal-be untuk aktivasi modul &amp; fee platform belum
 * final (OPEN-QUESTIONS <b>Q6</b>). Sampai itu terjawab, status <b>dianggap
 * aktif</b> ({@code diketahui=false}) sehingga kantin tetap bisa dipakai —
 * memblokir seluruh modul karena integrasi belum siap akan mematikan kantin
 * (pola solusi demo repo: fail-open).
 *
 * <p>Ketika Q6 terjawab, tambahkan implementasi nyata (mis.
 * {@code AktivasiModulRestClient} berbasis {@code RestClient}) dan tandai
 * {@code @Primary}, atau hapus kelas ini.
 */
@Service
@Slf4j
public class AktivasiModulFallback implements AktivasiModulPort {

    @Override
    public AktivasiModulInfo status(Long sekolahId) {
        log.debug("Aktivasi modul belum dikonfigurasi (Q6). sekolah={} dianggap aktif (fail-open).",
                sekolahId);
        return AktivasiModulInfo.tidakDiketahui(
                "Integrasi aktivasi modul internal-be belum dikonfigurasi (OPEN-QUESTIONS Q6)");
    }
}
