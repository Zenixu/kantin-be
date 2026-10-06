package com.asqi.scholia_kantin_be.service.saldo;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.dto.HasilRefundResponse;
import com.asqi.scholia_kantin_be.dto.KandidatRefundItem;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.integrasi.StatusSiswaPort;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi refund saldo siswa keluar &amp; pindah ke saudara
 * (PRD §9.3, issue #38) dengan PostgreSQL nyata (Testcontainers).
 *
 * <p>Memakai <b>fake</b> {@link StatusSiswaPort} (menyembunyikan ketergantungan
 * Q7), tetapi ledger, saldo, idempotency, append-only, &amp; tenant scoping
 * berjalan nyata.
 */
@SpringBootTest
@Import({TestcontainersConfig.class, RefundSaldoServiceIT.FakeStatusConfig.class})
@EnabledIfDockerAvailable
class RefundSaldoServiceIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;
    private static final long SISWA_KELUAR = 100L;
    private static final long SISWA_SAUDARA = 200L;
    private static final long SISWA_LAIN = 300L;

    @Autowired
    private RefundSaldoService service;

    @Autowired
    private LedgerSaldoService ledger;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    /** Fake status siswa: siswa yang "nonaktif" bisa diatur per uji. */
    static class FakeStatusSiswa implements StatusSiswaPort {
        final Set<Long> nonaktif = ConcurrentHashMap.newKeySet();
        final Set<Long> diblokir = ConcurrentHashMap.newKeySet();

        @Override
        public Boolean tidakAktif(Long sekolahId, Long siswaId) {
            return nonaktif.contains(siswaId);
        }

        @Override
        public void blokirKartu(Long sekolahId, Long siswaId, String alasan) {
            diblokir.add(siswaId);
        }
    }

    @TestConfiguration
    static class FakeStatusConfig {
        @Bean
        @Primary
        FakeStatusSiswa fakeStatusSiswa() {
            return new FakeStatusSiswa();
        }
    }

    @Autowired
    private FakeStatusSiswa status;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE saldo_ledger, saldo_cache CASCADE");
        status.nonaktif.clear();
        status.diblokir.clear();
        status.nonaktif.add(SISWA_KELUAR);
    }

    private void isiSaldo(long siswaId, long nominal) {
        tx.executeWithoutResult(s -> ledger.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).subjekId(siswaId)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI).nominal(nominal)
                .idempotencyKey("seed-" + siswaId + "-" + System.nanoTime()).build()));
    }

    // ────────────────────────────────────────────────────────────────
    // REFUND
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("refund mengembalikan SELURUH sisa saldo → saldo 0 & kartu diblokir")
    void refundSeluruhSisa() {
        isiSaldo(SISWA_KELUAR, 35_000);

        HasilRefundResponse hasil = service.refund(
                SEKOLAH, SISWA_KELUAR, "REF-1", "siswa lulus", 9L);

        assertThat(hasil.getNominal()).isEqualTo(35_000L);
        assertThat(hasil.getSaldoSetelah()).isZero();
        assertThat(hasil.isKartuDiblokir()).isTrue();
        assertThat(ledger.saldo(SEKOLAH, SubjekTipe.SISWA, SISWA_KELUAR)).isZero();
        assertThat(status.diblokir).contains(SISWA_KELUAR);

        String jenis = jdbc.queryForObject(
                "SELECT jenis FROM saldo_ledger WHERE subjek_id = ? AND arah = 'DEBIT'",
                String.class, SISWA_KELUAR);
        assertThat(jenis).isEqualTo("REFUND");
    }

    @Test
    @DisplayName("refund idempoten — bukti sama tidak mengembalikan dua kali")
    void refundIdempoten() {
        isiSaldo(SISWA_KELUAR, 20_000);

        HasilRefundResponse pertama = service.refund(SEKOLAH, SISWA_KELUAR, "REF-2", null, 9L);
        HasilRefundResponse kedua = service.refund(SEKOLAH, SISWA_KELUAR, "REF-2", null, 9L);

        assertThat(pertama.isIdempoten()).isFalse();
        assertThat(kedua.isIdempoten()).isTrue();
        assertThat(kedua.getNominal()).isEqualTo(20_000L);
        assertThat(ledger.saldo(SEKOLAH, SubjekTipe.SISWA, SISWA_KELUAR)).isZero();

        Integer baris = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saldo_ledger WHERE subjek_id = ? AND jenis = 'REFUND'",
                Integer.class, SISWA_KELUAR);
        assertThat(baris).isEqualTo(1);
    }

    @Test
    @DisplayName("refund saldo kosong → ConflictException, tanpa mutasi")
    void refundSaldoKosongDitolak() {
        assertThatThrownBy(() -> service.refund(SEKOLAH, SISWA_LAIN, "REF-3", null, 9L))
                .isInstanceOf(ConflictException.class);
    }

    // ────────────────────────────────────────────────────────────────
    // PINDAH KE SAUDARA
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pindah ke saudara — sumber 0, tujuan bertambah, dua kaki ledger")
    void pindahKeSaudara() {
        isiSaldo(SISWA_KELUAR, 12_000);
        isiSaldo(SISWA_SAUDARA, 5_000);

        HasilRefundResponse hasil = service.pindahKeSaudara(
                SEKOLAH, SISWA_KELUAR, SISWA_SAUDARA, "BA-1", "pindah ke adik", 9L);

        assertThat(hasil.getNominal()).isEqualTo(12_000L);
        assertThat(hasil.getSaldoSetelah()).isZero();
        assertThat(hasil.getTujuanSubjekId()).isEqualTo(SISWA_SAUDARA);
        assertThat(ledger.saldo(SEKOLAH, SubjekTipe.SISWA, SISWA_KELUAR)).isZero();
        assertThat(ledger.saldo(SEKOLAH, SubjekTipe.SISWA, SISWA_SAUDARA)).isEqualTo(17_000L);
        assertThat(status.diblokir).contains(SISWA_KELUAR);

        Integer transferRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saldo_ledger WHERE jenis = 'TRANSFER'", Integer.class);
        assertThat(transferRows).isEqualTo(2); // satu DEBIT + satu KREDIT
    }

    @Test
    @DisplayName("pindah idempoten — berita acara sama tidak memindah dua kali")
    void pindahIdempoten() {
        isiSaldo(SISWA_KELUAR, 8_000);

        service.pindahKeSaudara(SEKOLAH, SISWA_KELUAR, SISWA_SAUDARA, "BA-2", null, 9L);
        service.pindahKeSaudara(SEKOLAH, SISWA_KELUAR, SISWA_SAUDARA, "BA-2", null, 9L);

        assertThat(ledger.saldo(SEKOLAH, SubjekTipe.SISWA, SISWA_SAUDARA)).isEqualTo(8_000L);

        Integer transferRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saldo_ledger WHERE jenis = 'TRANSFER'", Integer.class);
        assertThat(transferRows).isEqualTo(2); // tetap 2, bukan 4
    }

    @Test
    @DisplayName("pindah ke tujuan nonaktif ditolak")
    void pindahTujuanNonaktifDitolak() {
        isiSaldo(SISWA_KELUAR, 10_000);
        status.nonaktif.add(SISWA_SAUDARA);

        assertThatThrownBy(() -> service.pindahKeSaudara(
                SEKOLAH, SISWA_KELUAR, SISWA_SAUDARA, "BA-3", null, 9L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("tidak aktif");
    }

    @Test
    @DisplayName("pindah ke diri sendiri ditolak")
    void pindahKeDiriSendiriDitolak() {
        isiSaldo(SISWA_KELUAR, 10_000);

        assertThatThrownBy(() -> service.pindahKeSaudara(
                SEKOLAH, SISWA_KELUAR, SISWA_KELUAR, "BA-4", null, 9L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("tidak boleh sama");
    }

    // ────────────────────────────────────────────────────────────────
    // DAFTAR KANDIDAT & TENANT SCOPING
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("daftar kandidat hanya siswa bersisa saldo; filter nonaktif")
    void daftarKandidat() {
        isiSaldo(SISWA_KELUAR, 10_000);   // nonaktif, bersisa
        isiSaldo(SISWA_SAUDARA, 5_000);   // aktif, bersisa
        // SISWA_LAIN tidak diisi → tak ada baris saldo (tidak muncul).

        List<KandidatRefundItem> semua = service.daftarKandidat(SEKOLAH, false);
        assertThat(semua).extracting(KandidatRefundItem::getSubjekId)
                .containsExactlyInAnyOrder(SISWA_KELUAR, SISWA_SAUDARA);

        List<KandidatRefundItem> hanyaNonaktif = service.daftarKandidat(SEKOLAH, true);
        assertThat(hanyaNonaktif).extracting(KandidatRefundItem::getSubjekId)
                .containsExactly(SISWA_KELUAR);
        assertThat(hanyaNonaktif.get(0).getTidakAktif()).isTrue();
    }

    @Test
    @DisplayName("tenant scoping — sekolah lain tidak melihat saldo sekolah ini")
    void tenantScoping() {
        isiSaldo(SISWA_KELUAR, 10_000);

        List<KandidatRefundItem> kandidatLain = service.daftarKandidat(SEKOLAH_LAIN, false);
        assertThat(kandidatLain).isEmpty();
    }
}
