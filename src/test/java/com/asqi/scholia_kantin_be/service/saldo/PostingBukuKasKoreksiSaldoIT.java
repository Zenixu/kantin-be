package com.asqi.scholia_kantin_be.service.saldo;

import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.MetodeBukuKas;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.enums.TipeBukuKas;
import com.asqi.scholia_kantin_be.service.integrasi.BukuKasPort;
import com.asqi.scholia_kantin_be.service.integrasi.HasilPostingBukuKas;
import com.asqi.scholia_kantin_be.service.integrasi.PerintahBukuKas;
import com.asqi.scholia_kantin_be.service.kasir.HasilMutasiSaldo;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji integrasi posting Buku Kas untuk <b>koreksi saldo</b> bendahara —
 * PRD §5.1, §9.2; INTEGRATIONS.md §3.3.
 *
 * <p>Memakai fake {@link BukuKasPort} (bukan jaringan) untuk memverifikasi:
 * arah entri menjaga invariant PRD §5 (KREDIT → KELUAR, DEBIT → MASUK),
 * pos "Penyesuaian Kantin", idempotency (retry tidak menggandakan entri),
 * dan bahwa kegagalan integrasi <b>tidak</b> membatalkan koreksi saldo.
 */
@SpringBootTest
@Import({TestcontainersConfig.class, PostingBukuKasKoreksiSaldoIT.FakeBukuKasConfig.class})
@EnabledIfDockerAvailable
class PostingBukuKasKoreksiSaldoIT {

    private static final long SEKOLAH = 1L;
    private static final long SISWA = 7L;

    @Autowired
    private SaldoTopUpService saldoTopUp;

    @Autowired
    private LedgerSaldoService ledgerSaldo;

    @Autowired
    private FakeBukuKas fake;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bersihkan() {
        fake.reset();
        jdbc.execute("TRUNCATE TABLE saldo_ledger, saldo_cache, posting_buku_kas, audit_log CASCADE");
        // Bekali saldo awal agar koreksi DEBIT (mengurangi) punya cukup saldo.
        tx.executeWithoutResult(s -> ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).subjekId(SISWA)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI).nominal(100_000)
                .idempotencyKey("seed-" + System.nanoTime()).build()));
    }

    private HasilMutasiSaldo koreksi(ArahMutasi arah, long nominal, String referensiId) {
        return saldoTopUp.koreksi(SEKOLAH, SubjekTipe.SISWA, SISWA, arah, nominal,
                "Top-up salah input", referensiId, 99L);
    }

    @Test
    @DisplayName("koreksi KREDIT → 1 entri KELUAR/NON_TUNAI pos 'Penyesuaian Kantin' (invariant PRD §5)")
    void koreksiKreditPostingKeluar() {
        koreksi(ArahMutasi.KREDIT, 20_000, "BA-1");

        assertThat(fake.dipanggil).hasSize(1);
        PerintahBukuKas p = fake.dipanggil.get(0);
        assertThat(p.getSekolahId()).isEqualTo(SEKOLAH);
        assertThat(p.getTipe()).isEqualTo(TipeBukuKas.KELUAR);
        assertThat(p.getMetode()).isEqualTo(MetodeBukuKas.NON_TUNAI);
        assertThat(p.getKategori()).isEqualTo("Penyesuaian Kantin");
        assertThat(p.getJumlah()).isEqualByComparingTo(new BigDecimal("20000"));
        assertThat(p.getRefId()).isEqualTo("KANTIN-KOR-BA-1");
        // Q3 TERJAWAB: refModul="KANTIN" (admin-be migrateBukuKas → default → isOrphan=false).
        assertThat(p.getRefModul()).isEqualTo("KANTIN");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM posting_buku_kas WHERE referensi_id = 'KANTIN-KOR-BA-1' "
                        + "AND entitas = 'KOREKSI_SALDO' AND status = 'SUKSES'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE aksi = 'POSTING_BUKU_KAS'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("koreksi DEBIT → entri MASUK pos 'Penyesuaian Kantin'")
    void koreksiDebitPostingMasuk() {
        koreksi(ArahMutasi.DEBIT, 15_000, "BA-2");

        assertThat(fake.dipanggil).hasSize(1);
        PerintahBukuKas p = fake.dipanggil.get(0);
        assertThat(p.getTipe()).isEqualTo(TipeBukuKas.MASUK);
        assertThat(p.getKategori()).isEqualTo("Penyesuaian Kantin");
        assertThat(p.getJumlah()).isEqualByComparingTo(new BigDecimal("15000"));
        assertThat(p.getRefId()).isEqualTo("KANTIN-KOR-BA-2");
    }

    @Test
    @DisplayName("retry berita acara sama → tetap 1 entri (idempoten)")
    void retryKoreksiTetapSatu() {
        koreksi(ArahMutasi.KREDIT, 20_000, "BA-1");
        koreksi(ArahMutasi.KREDIT, 20_000, "BA-1");

        // Mutasi saldo kedua adalah replay idempotent → posting tidak dipanggil lagi.
        assertThat(fake.dipanggil).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM posting_buku_kas WHERE entitas = 'KOREKSI_SALDO'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("kegagalan posting TIDAK membatalkan koreksi saldo (fail-open); tanpa penanda")
    void gagalPostingTidakMembatalkanKoreksi() {
        fake.melempar = true;

        koreksi(ArahMutasi.KREDIT, 20_000, "BA-1");

        // Koreksi saldo tetap tercatat (saldo bertambah), penanda tidak ditulis.
        assertThat(jdbc.queryForObject(
                "SELECT saldo FROM saldo_cache WHERE subjek_tipe = 'SISWA' AND subjek_id = ?",
                Long.class, SISWA)).isEqualTo(120_000L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM posting_buku_kas", Integer.class)).isZero();
    }

    /** Fake port yang merekam perintah &amp; bisa disetel sukses/gagal. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FakeBukuKasConfig {

        @Bean
        @Primary
        FakeBukuKas bukuKasFake() {
            return new FakeBukuKas();
        }
    }

    static class FakeBukuKas implements BukuKasPort {

        final List<PerintahBukuKas> dipanggil = Collections.synchronizedList(new ArrayList<>());

        volatile boolean melempar = false;

        @Override
        public HasilPostingBukuKas catat(PerintahBukuKas perintah) {
            dipanggil.add(perintah);
            if (melempar) {
                throw new IllegalStateException("admin-be tidak dapat dihubungi");
            }
            return HasilPostingBukuKas.sukses(perintah.getRefId(), "ok");
        }

        void reset() {
            dipanggil.clear();
            melempar = false;
        }
    }
}
