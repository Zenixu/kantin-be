package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.KartuTamu;
import com.asqi.scholia_kantin_be.repository.KartuTamuRepository;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import com.asqi.scholia_kantin_be.service.integrasi.KartuLookupPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Dasar bersama lookup kartu untuk mode <b>dev/demo</b> &amp; <b>uji beban</b>.
 *
 * <p>Menjawab UID dari tabel {@code kartu_tamu} — data <b>milik kantin-be sendiri</b>
 * (PRD §9.4) — sehingga {@code POST /api/kasir/tap} dapat menembus jalur
 * <b>SUKSES</b> tanpa menunggu kontrak lookup <i>siswa</i> ke admin-be
 * (OPEN-QUESTIONS <b>Q7</b>). Bila memakai {@code KartuLookupFallback}, setiap tap
 * selalu dijawab "kartu tidak dikenal", sehingga alur kasir tak bisa diuji
 * end-to-end (oleh FE maupun demo PKL).
 *
 * <p><b>Kenapa kelas dasar?</b> Agar {@link KartuLookupLoadTest} (profil
 * {@code loadtest}, issue #145) &amp; {@link KartuLookupDemo} (profil {@code demo},
 * issue #163) memakai logika yang <b>sama persis</b> — tidak ada duplikasi yang
 * bisa menyimpang.
 *
 * <p><b>Identitas siswa tetap menunggu Q7:</b> hanya Kartu Tamu yang dikenali di
 * sini; UID siswa dianggap tidak dikenal (bukan diam-diam diloloskan).
 *
 * <p><b>Gerbang keamanan:</b> kelas ini <b>abstrak</b> (tidak pernah jadi bean).
 * Yang hidup hanyalah subkelas yang digerbangi property masing-masing (default
 * <b>false</b>) — lihat {@link KartuLookupDemo} &amp; {@link KartuLookupLoadTest}.
 * <b>JANGAN aktifkan di staging/produksi.</b>
 */
@Slf4j
public abstract class KartuLookupKartuTamu implements KartuLookupPort {

    protected final KartuTamuRepository kartuTamuRepo;

    protected KartuLookupKartuTamu(KartuTamuRepository kartuTamuRepo) {
        this.kartuTamuRepo = kartuTamuRepo;
    }

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
                    log.debug("[DEV-LOOKUP] UID {} tidak ada di kartu_tamu (sekolah={})",
                            rfidUid, sekolahId);
                    return InfoKartu.tidakDikenal();
                });
    }

    private InfoKartu keInfo(KartuTamu k) {
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
