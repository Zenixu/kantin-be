package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.KartuTamu;
import com.asqi.scholia_kantin_be.repository.KartuTamuRepository;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uji jalur lookup kartu DEMO (issue #163) — memastikan alur kasir bisa
 * menembus jalur SUKSES memakai data Kartu Tamu (milik kantin-be), dan tetap
 * <b>aman</b>: UID tak dikenal / beda sekolah / kartu nonaktif → tidak dikenal.
 *
 * <p>Logika diuji lewat {@link KartuLookupKartuTamu} (kelas dasar bersama
 * {@link KartuLookupLoadTest}); {@link KartuLookupDemo} hanya gerbang property.
 */
class KartuLookupDemoTest {

    private final KartuTamuRepository repo = mock(KartuTamuRepository.class);
    private final KartuLookupDemo lookup = new KartuLookupDemo(repo);

    private KartuTamu kartu(Long id, Long sekolahId, boolean aktif, String label) {
        return KartuTamu.builder()
                .id(id)
                .sekolahId(sekolahId)
                .nomorKartu("KT-DEMO-00" + id)
                .rfidUid("DE00000" + id)
                .aktif(aktif)
                .labelPemegang(label)
                .build();
    }

    @Test
    @DisplayName("#163: Kartu Tamu aktif di sekolah yang sama → dikenal (jalur sukses)")
    void kartuTamuDikenal() {
        when(repo.findByRfidUid("DE000001"))
                .thenReturn(Optional.of(kartu(1L, 1L, true, "Budi (Guru)")));

        InfoKartu info = lookup.cariBerdasarkanUid(1L, "DE000001");

        assertThat(info.isDikenal()).isTrue();
        assertThat(info.getSubjekTipe()).isEqualTo(SubjekTipe.KARTU_TAMU);
        assertThat(info.getSubjekId()).isEqualTo(1L);
        assertThat(info.getNama()).isEqualTo("Budi (Guru)");
        // Kartu Tamu tidak punya limit harian (PRD §9.4).
        assertThat(info.getLimitHarian()).isNull();
    }

    @Test
    @DisplayName("#163: label pemegang kosong → fallback ke nomor kartu")
    void labelKosongPakaiNomorKartu() {
        when(repo.findByRfidUid("DE000002"))
                .thenReturn(Optional.of(kartu(2L, 1L, true, "  ")));

        InfoKartu info = lookup.cariBerdasarkanUid(1L, "DE000002");

        assertThat(info.isDikenal()).isTrue();
        assertThat(info.getNama()).isEqualTo("KT-DEMO-002");
    }

    @Test
    @DisplayName("#163: kartu sekolah lain → TIDAK dikenal (tenant-scoped, PRD §11.4)")
    void kartuSekolahLainTidakDikenal() {
        when(repo.findByRfidUid("DE000003"))
                .thenReturn(Optional.of(kartu(3L, 99L, true, "Tamu")));

        InfoKartu info = lookup.cariBerdasarkanUid(1L, "DE000003");

        assertThat(info.isDikenal()).isFalse();
    }

    @Test
    @DisplayName("#163: kartu nonaktif → TIDAK dikenal")
    void kartuNonaktifTidakDikenal() {
        when(repo.findByRfidUid("DE000004"))
                .thenReturn(Optional.of(kartu(4L, 1L, false, "Rina")));

        InfoKartu info = lookup.cariBerdasarkanUid(1L, "DE000004");

        assertThat(info.isDikenal()).isFalse();
    }

    @Test
    @DisplayName("#163: UID tidak terdaftar / kosong → TIDAK dikenal (fail-closed)")
    void uidTidakAdaTidakDikenal() {
        when(repo.findByRfidUid("DE009999")).thenReturn(Optional.empty());

        assertThat(lookup.cariBerdasarkanUid(1L, "DE009999").isDikenal()).isFalse();
        assertThat(lookup.cariBerdasarkanUid(1L, null).isDikenal()).isFalse();
        assertThat(lookup.cariBerdasarkanUid(1L, "  ").isDikenal()).isFalse();
    }

    @Test
    @DisplayName("#163: loadtest & demo berbagi logika yang sama (satu kelas dasar)")
    void loadtestDanDemoBerbagiLogika() {
        when(repo.findByRfidUid("DE000005"))
                .thenReturn(Optional.of(kartu(5L, 1L, true, "Tamu Umum")));

        InfoKartu viaDemo = lookup.cariBerdasarkanUid(1L, "DE000005");
        InfoKartu viaLoadtest = new KartuLookupLoadTest(repo).cariBerdasarkanUid(1L, "DE000005");

        assertThat(viaDemo.getSubjekId()).isEqualTo(viaLoadtest.getSubjekId());
        assertThat(viaDemo.getSubjekTipe()).isEqualTo(viaLoadtest.getSubjekTipe());
    }
}
