package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.service.integrasi.BukuKasPort;
import com.asqi.scholia_kantin_be.service.integrasi.HasilPostingBukuKas;
import com.asqi.scholia_kantin_be.service.integrasi.PerintahBukuKas;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Adapter <b>demo</b> untuk {@link BukuKasPort} (PRD §5.1, OPEN-QUESTIONS Q3).
 *
 * <p>Meniru admin-be yang menerima entri Buku Kas, sehingga alur
 * <i>tutup kasir → posting Buku Kas → idempotency</i> dapat diuji
 * <b>end-to-end</b> di lokal <b>tanpa</b> kredensial/endpoint admin-be.
 * Mengembalikan {@link HasilPostingBukuKas#sukses} dengan {@code referensi}
 * tiruan, sehingga penanda lokal {@code posting_buku_kas} tetap tertulis dan
 * posting ulang tetap <b>idempoten</b> (perilaku produksi ikut teruji).
 *
 * <p><b>Gerbang keamanan:</b> hanya hidup bila {@code kantin.demo.enabled=true}
 * (default <b>false</b>). {@code @Primary} agar menang atas
 * {@link com.asqi.scholia_kantin_be.service.integrasi.BukuKasFallback}.
 *
 * <p><b>JANGAN aktifkan di staging/production.</b> Jangan aktifkan bersamaan
 * dengan adapter REST nyata ({@code kantin.integrasi.bukukas.enabled=true}) —
 * dua {@code @Primary} → ambigu.
 */
@Service
@Primary
@Slf4j
@ConditionalOnProperty(prefix = "kantin.demo", name = "enabled", havingValue = "true")
public class BukuKasDemo implements BukuKasPort {

    @Override
    public HasilPostingBukuKas catat(PerintahBukuKas perintah) {
        String referensi = "DEMO-BK-" + perintah.getRefId();
        log.info("[DEMO] Buku Kas (Q3) — entri diterima: tipe={} kategori={} jumlah={} refId={} refModul={}",
                perintah.getTipe(), perintah.getKategori(), perintah.getJumlah(),
                perintah.getRefId(), perintah.getRefModul());
        return HasilPostingBukuKas.sukses(referensi,
                "Demo: entri Buku Kas disimulasikan diterima admin-be");
    }
}
