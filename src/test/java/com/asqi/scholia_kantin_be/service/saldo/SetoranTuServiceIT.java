package com.asqi.scholia_kantin_be.service.saldo;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.dto.RekapSetoranTuItem;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.dto.SetoranTuResponse;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi setoran kas TU harian (PRD §9.2, issue #39) dengan PostgreSQL
 * nyata (Testcontainers).
 *
 * <p>Menegakkan: rekap top-up tunai per petugas per hari, konfirmasi setoran +
 * selisih kas <b>dicatat</b> (append-only), idempotency berita acara, dan
 * tenant scoping.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class SetoranTuServiceIT {

    private static final long SEKOLAH = 1L;
    private static final long PETUGAS_A = 555L;
    private static final long PETUGAS_B = 777L;
    private static final long BENDAHARA = 999L;

    @Autowired
    private SetoranTuService setoranService;

    @Autowired
    private LedgerSaldoService ledger;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JamKantin jam;

    private long subjekBerikut = 10_000L;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE setoran_tu, saldo_ledger, saldo_cache, audit_log CASCADE");
        subjekBerikut = 10_000L;
    }

    /** Catat satu top-up tunai oleh petugas tertentu (aktor_id = petugasId). */
    private void topUpTunai(long petugasId, long nominal) {
        long subjek = subjekBerikut++;
        tx.executeWithoutResult(s -> ledger.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH)
                .subjekTipe(SubjekTipe.SISWA)
                .subjekId(subjek)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI)
                .nominal(nominal)
                .idempotencyKey("topup-" + petugasId + "-" + nominal + "-" + System.nanoTime())
                .referensiTipe("TOPUP")
                .aktorId(petugasId)
                .build()));
    }

    @Test
    @DisplayName("rekap: total top-up tunai per petugas per hari (belum disetor → selisih = total)")
    void rekapPerPetugas() {
        topUpTunai(PETUGAS_A, 50_000);
        topUpTunai(PETUGAS_A, 30_000);
        topUpTunai(PETUGAS_B, 20_000);

        List<RekapSetoranTuItem> rekap = setoranService.rekap(SEKOLAH, jam.hariIni());

        assertThat(rekap).hasSize(2);
        RekapSetoranTuItem a = rekap.stream()
                .filter(r -> r.getPetugasId().equals(PETUGAS_A)).findFirst().orElseThrow();
        assertThat(a.getTotalTopup()).isEqualTo(80_000);
        assertThat(a.getJumlahDisetor()).isZero();
        assertThat(a.getSelisih()).isEqualTo(80_000);

        RekapSetoranTuItem b = rekap.stream()
                .filter(r -> r.getPetugasId().equals(PETUGAS_B)).findFirst().orElseThrow();
        assertThat(b.getTotalTopup()).isEqualTo(20_000);
    }

    @Test
    @DisplayName("konfirmasi setoran sesuai (selisih 0) → tersimpan, total diambil dari ledger")
    void konfirmasiSesuai() {
        topUpTunai(PETUGAS_A, 80_000);

        SetoranTuResponse s = setoranService.konfirmasi(SEKOLAH, jam.hariIni(), PETUGAS_A,
                80_000, "BA-2026-001", null, BENDAHARA);

        assertThat(s.getTotalTopup()).isEqualTo(80_000);
        assertThat(s.getJumlahDisetor()).isEqualTo(80_000);
        assertThat(s.getSelisih()).isZero(); // dihitung kolom GENERATED DB
        assertThat(s.getReferensiId()).isEqualTo("BA-2026-001");

        // Rekap mencerminkan setoran.
        RekapSetoranTuItem a = setoranService.rekap(SEKOLAH, jam.hariIni()).stream()
                .filter(r -> r.getPetugasId().equals(PETUGAS_A)).findFirst().orElseThrow();
        assertThat(a.getJumlahDisetor()).isEqualTo(80_000);
        assertThat(a.getSelisih()).isZero();
        assertThat(a.getReferensiId()).isEqualTo("BA-2026-001");

        // Audit tercatat.
        Integer audit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE aksi = 'KONFIRMASI_SETORAN_TU'", Integer.class);
        assertThat(audit).isEqualTo(1);
    }

    @Test
    @DisplayName("selisih kas DICATAT (kurang setor) — tidak dihapus, wajib catatan")
    void selisihKurangSetorDicatat() {
        topUpTunai(PETUGAS_A, 80_000);

        SetoranTuResponse s = setoranService.konfirmasi(SEKOLAH, jam.hariIni(), PETUGAS_A,
                75_000, "BA-2026-002", "Kurang Rp5.000, uang belum kembali dari siswa", BENDAHARA);

        assertThat(s.getSelisih()).isEqualTo(5_000); // kurang setor = positif
        assertThat(jdbc.queryForObject(
                "SELECT selisih FROM setoran_tu WHERE id = ?", Long.class, s.getId()))
                .isEqualTo(5_000L);
    }

    @Test
    @DisplayName("selisih tanpa catatan → ditolak (PRD §9.2: selisih wajib beralasan)")
    void selisihTanpaCatatanDitolak() {
        topUpTunai(PETUGAS_A, 80_000);

        assertThatThrownBy(() -> setoranService.konfirmasi(SEKOLAH, jam.hariIni(), PETUGAS_A,
                75_000, "BA-2026-003", "  ", BENDAHARA))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("catatan");
    }

    @Test
    @DisplayName("lebih setor (selisih negatif) → dicatat")
    void selisihLebihSetor() {
        topUpTunai(PETUGAS_A, 80_000);

        SetoranTuResponse s = setoranService.konfirmasi(SEKOLAH, jam.hariIni(), PETUGAS_A,
                85_000, "BA-2026-004", "Lebih Rp5.000, kemungkinan salah hitung", BENDAHARA);

        assertThat(s.getSelisih()).isEqualTo(-5_000);
    }

    @Test
    @DisplayName("idempoten: berita acara sama → tidak mencatat setoran dua kali")
    void konfirmasiIdempoten() {
        topUpTunai(PETUGAS_A, 80_000);

        SetoranTuResponse s1 = setoranService.konfirmasi(SEKOLAH, jam.hariIni(), PETUGAS_A,
                80_000, "BA-2026-005", null, BENDAHARA);
        SetoranTuResponse s2 = setoranService.konfirmasi(SEKOLAH, jam.hariIni(), PETUGAS_A,
                80_000, "BA-2026-005", null, BENDAHARA);

        assertThat(s2.getId()).isEqualTo(s1.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM setoran_tu", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("setoran hanya INSERT — UPDATE/DELETE ditolak trigger (append-only)")
    void appendOnly() {
        topUpTunai(PETUGAS_A, 80_000);
        SetoranTuResponse s = setoranService.konfirmasi(SEKOLAH, jam.hariIni(), PETUGAS_A,
                80_000, "BA-2026-006", null, BENDAHARA);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE setoran_tu SET jumlah_disetor = 0 WHERE id = ?", s.getId()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM setoran_tu WHERE id = ?", s.getId()))
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("tenant scoping: rekap sekolah lain tidak bocor")
    void tenantScoped() {
        topUpTunai(PETUGAS_A, 80_000); // sekolah 1

        assertThat(setoranService.rekap(2L, jam.hariIni())).isEmpty();
    }
}
