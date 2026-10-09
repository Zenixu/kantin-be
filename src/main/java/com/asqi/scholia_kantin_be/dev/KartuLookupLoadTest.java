package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.repository.KartuTamuRepository;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import com.asqi.scholia_kantin_be.service.integrasi.KartuLookupPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Implementasi {@link KartuLookupPort} khusus <b>uji beban</b> (issue #145).
 *
 * <p>Menjawab kebutuhan skenario k6 "N kasir tap bersamaan": agar
 * {@code POST /api/kasir/tap} benar-benar menembus jalur sukses (bukan langsung
 * ditolak "kartu tidak dikenal" oleh {@code KartuLookupFallback}), lookup kartu
 * harus mengenali UID yang ditap.
 *
 * <p><b>Kenapa Kartu Tamu (bukan siswa)?</b> Data <i>Kartu Tamu</i> adalah milik
 * kantin-be sendiri (tabel {@code kartu_tamu}, PRD §9.4), sehingga lookup ini
 * <b>tidak</b> terblokir kontrak Q7 (identitas siswa dari admin-be). Identitas
 * <b>siswa</b> tetap menunggu Q7 → UID siswa tetap dianggap tidak dikenal di sini.
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
@RequiredArgsConstructor
@Slf4j
public class KartuLookupLoadTest implements KartuLookupPort {

    private final KartuTamuRepository kartuTamuRepo;

    @Override
    public InfoKartu cariBerdasarkanUid(Long sekolahId, String rfidUid) {
        if (rfidUid == null || rfidUid.isBlank()) {
            return InfoKartu.tidakDikenal();
        }
        return kartuTamuRepo.findByRfidUid(rfidUid)
                .filter(k -> k.getSekolahId() != null && k.getSekolahId().equals(sekolahId))
                .filter(k -> Boolean.TRUE.equals(k.getAktif()))
                .map(this::keInfo)
                .orElseGet(() -> {
                    log.debug("[LOADTEST] UID {} tidak ada di kartu_tamu (sekolah={})", rfidUid, sekolahId);
                    return InfoKartu.tidakDikenal();
                });
    }

    private InfoKartu keInfo(com.asqi.scholia_kantin_be.model.KartuTamu k) {
        String nama = (k.getLabelPemegang() != null && !k.getLabelPemegang().isBlank())
                ? k.getLabelPemegang()
                : k.getNomorKartu();
        return InfoKartu.builder()
                .dikenal(true)
                .diblokir(false)
                .subjekTipe(SubjekTipe.KARTU_TAMU)
                .subjekId(k.getId())
                .nama(nama)
                .build();
    }
}
