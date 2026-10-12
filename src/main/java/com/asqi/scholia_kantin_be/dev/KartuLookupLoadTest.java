package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.repository.KartuTamuRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Implementasi {@link com.asqi.scholia_kantin_be.service.integrasi.KartuLookupPort}
 * khusus <b>uji beban</b> (issue #145).
 *
 * <p>Menjawab kebutuhan skenario k6 "N kasir tap bersamaan": agar
 * {@code POST /api/kasir/tap} benar-benar menembus jalur sukses (bukan langsung
 * ditolak "kartu tidak dikenal" oleh {@code KartuLookupFallback}), lookup kartu
 * harus mengenali UID yang ditap. Logika lookup ada di
 * {@link KartuLookupKartuTamu} (dipakai bersama {@link KartuLookupDemo}).
 *
 * <p><b>Gerbang keamanan:</b> hanya hidup bila
 * {@code kantin.loadtest.enabled=true} (default <b>false</b>). Karena itu ia
 * <b>tidak</b> pernah dimuat pada test otomatis maupun produksi — test yang
 * menyediakan fake {@code @Primary} sendiri tetap menang. {@code @Primary} dipakai
 * agar menang atas {@link com.asqi.scholia_kantin_be.service.integrasi.KartuLookupFallback}
 * ketika diaktifkan.
 *
 * <p><b>JANGAN aktifkan di staging/production.</b>
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "kantin.loadtest", name = "enabled", havingValue = "true")
public class KartuLookupLoadTest extends KartuLookupKartuTamu {

    public KartuLookupLoadTest(KartuTamuRepository kartuTamuRepo) {
        super(kartuTamuRepo);
    }
}
