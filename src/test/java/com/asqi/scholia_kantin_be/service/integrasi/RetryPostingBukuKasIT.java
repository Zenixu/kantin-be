package com.asqi.scholia_kantin_be.service.integrasi;

import com.asqi.scholia_kantin_be.enums.MetodeBukuKas;
import com.asqi.scholia_kantin_be.enums.TipeBukuKas;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji integrasi retry otomatis posting Buku Kas yang tertunggak (issue #33).
 *
 * <p>Mensimulasikan kondisi fail-open: sesi DITUTUP dengan
 * {@code posting_buku_kas=false} (mis. admin-be sempat gangguan). Penjadwal
 * retry menyapunya dan memposting ulang — idempoten, tenant-safe, dan tahan
 * terhadap kegagalan sebagian.
 */
@SpringBootTest
@Import({TestcontainersConfig.class, RetryPostingBukuKasIT.FakeBukuKasConfig.class})
@EnabledIfDockerAvailable
class RetryPostingBukuKasIT {

    private static final long SEKOLAH_A = 1L;
    private static final long SEKOLAH_B = 2L;
    private static final long TITIK = 1L;

    @Autowired
    private RetryPostingBukuKasService retryService;

    @Autowired
    private FakeBukuKas fake;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bersihkan() {
        fake.reset();
        jdbc.execute("TRUNCATE TABLE posting_buku_kas, sesi_kasir, titik_kasir, audit_log CASCADE");
        jdbc.update("INSERT INTO titik_kasir (id, sekolah_id, nama, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'Kasir 1', true, now(), now())", TITIK, SEKOLAH_A);
        jdbc.update("INSERT INTO titik_kasir (id, sekolah_id, nama, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'Kasir 1', true, now(), now())", 2L, SEKOLAH_B);
    }

    /** Sisipkan sesi DITUTUP belum terposting dengan total bersih tertentu. */
    private void sesiTertunggak(long id, long sekolahId, long titikId, long bersih, boolean terposting) {
        jdbc.update("INSERT INTO sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status, "
                + "total_bruto, total_void, total_bersih, dibuka_at, ditutup_at, auto_tutup, "
                + "posting_buku_kas, created_at, updated_at) "
                + "VALUES (?, ?, ?, CURRENT_DATE, 'DITUTUP', ?, 0, ?, now(), now(), true, ?, now(), now())",
                id, sekolahId, titikId, bersih, bersih, terposting);
    }

    @Test
    @DisplayName("sweep memposting sesi tertunggak → entri MASUK/NON_TUNAI, flag terisi, refId deterministik")
    void sweepMempostingTertunggak() {
        sesiTertunggak(9001, SEKOLAH_A, TITIK, 8_000, false);

        RetryPostingBukuKasService.HasilSweep hasil = retryService.postingTertunggak();

        assertThat(hasil.tertunggak()).isEqualTo(1);
        assertThat(hasil.sukses()).isEqualTo(1);
        assertThat(fake.dipanggil).hasSize(1);
        PerintahBukuKas p = fake.dipanggil.get(0);
        assertThat(p.getSekolahId()).isEqualTo(SEKOLAH_A);
        assertThat(p.getTipe()).isEqualTo(TipeBukuKas.MASUK);
        assertThat(p.getMetode()).isEqualTo(MetodeBukuKas.NON_TUNAI);
        assertThat(p.getKategori()).isEqualTo("Pendapatan Kantin");
        assertThat(p.getJumlah()).isEqualByComparingTo(new BigDecimal("8000"));
        assertThat(p.getRefId()).isEqualTo("KANTIN-SESI-9001");

        // Flag idempotency terisi.
        assertThat(jdbc.queryForObject(
                "SELECT posting_buku_kas FROM sesi_kasir WHERE id = 9001", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("sweep ulang → tidak menggandakan entri (idempoten)")
    void sweepIdempoten() {
        sesiTertunggak(9001, SEKOLAH_A, TITIK, 8_000, false);
        retryService.postingTertunggak();
        assertThat(fake.dipanggil).hasSize(1);

        // Sweep kedua: sesi sudah terposting → tidak lagi tertunggak.
        RetryPostingBukuKasService.HasilSweep hasil2 = retryService.postingTertunggak();

        assertThat(hasil2.tertunggak()).isZero();
        assertThat(fake.dipanggil).hasSize(1); // tidak ada entri ganda
    }

    @Test
    @DisplayName("sesi sudah terposting → tidak ikut sweep")
    void sesiTerpostingDilewati() {
        sesiTertunggak(9001, SEKOLAH_A, TITIK, 8_000, true);

        RetryPostingBukuKasService.HasilSweep hasil = retryService.postingTertunggak();

        assertThat(hasil.tertunggak()).isZero();
        assertThat(fake.dipanggil).isEmpty();
    }

    @Test
    @DisplayName("sesi total bersih 0 → bukan tertunggak (tak ada yang perlu diposting)")
    void sesiNolBukanTertunggak() {
        sesiTertunggak(9001, SEKOLAH_A, TITIK, 0, false);

        RetryPostingBukuKasService.HasilSweep hasil = retryService.postingTertunggak();

        assertThat(hasil.tertunggak()).isZero();
        assertThat(fake.dipanggil).isEmpty();
    }

    @Test
    @DisplayName("kegagalan integrasi → dicatat gagal, TIDAK melempar, & bisa di-retry sukses")
    void gagalLaluRetrySukses() {
        sesiTertunggak(9001, SEKOLAH_A, TITIK, 8_000, false);
        fake.melempar = true;

        RetryPostingBukuKasService.HasilSweep gagal = retryService.postingTertunggak();

        assertThat(gagal.tertunggak()).isEqualTo(1);
        assertThat(gagal.gagal()).isEqualTo(1);
        assertThat(gagal.sukses()).isZero();
        // Flag belum terisi → tetap tertunggak untuk sweep berikutnya.
        assertThat(jdbc.queryForObject(
                "SELECT posting_buku_kas FROM sesi_kasir WHERE id = 9001", Boolean.class)).isFalse();

        // Integrasi pulih → sweep berikutnya sukses.
        fake.melempar = false;
        RetryPostingBukuKasService.HasilSweep sukses = retryService.postingTertunggak();

        assertThat(sukses.sukses()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT posting_buku_kas FROM sesi_kasir WHERE id = 9001", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("lintas-tenant: tiap sesi diposting dengan sekolah_id-nya sendiri")
    void sweepLintasTenant() {
        sesiTertunggak(9001, SEKOLAH_A, TITIK, 8_000, false);
        sesiTertunggak(9002, SEKOLAH_B, 2L, 12_000, false);

        RetryPostingBukuKasService.HasilSweep hasil = retryService.postingTertunggak();

        assertThat(hasil.tertunggak()).isEqualTo(2);
        assertThat(hasil.sukses()).isEqualTo(2);
        assertThat(fake.dipanggil).hasSize(2);
        assertThat(fake.dipanggil)
                .extracting(PerintahBukuKas::getSekolahId)
                .containsExactlyInAnyOrder(SEKOLAH_A, SEKOLAH_B);
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
