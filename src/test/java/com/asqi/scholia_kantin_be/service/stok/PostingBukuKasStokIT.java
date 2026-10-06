package com.asqi.scholia_kantin_be.service.stok;

import com.asqi.scholia_kantin_be.enums.MetodeBukuKas;
import com.asqi.scholia_kantin_be.enums.TipeBukuKas;
import com.asqi.scholia_kantin_be.service.integrasi.BukuKasPort;
import com.asqi.scholia_kantin_be.service.integrasi.HasilPostingBukuKas;
import com.asqi.scholia_kantin_be.service.integrasi.PerintahBukuKas;
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
 * Uji integrasi posting Buku Kas <b>belanja stok</b> saat barang masuk
 * (+ pembalik) — PRD §5.1, §7.2; INTEGRATIONS.md §3.3.
 *
 * <p>Memakai fake {@link BukuKasPort} (bukan jaringan) untuk memverifikasi:
 * kontrak perintah (KELUAR/TUNAI pos "Belanja Stok Kantin"), idempotency
 * (retry tidak menggandakan entri), entri koreksi saat pembalik, dan bahwa
 * kegagalan integrasi <b>tidak</b> membatalkan mutasi stok.
 */
@SpringBootTest
@Import({TestcontainersConfig.class, PostingBukuKasStokIT.FakeBukuKasConfig.class})
@EnabledIfDockerAvailable
class PostingBukuKasStokIT {

    private static final long SEKOLAH = 1L;
    private static final long MENU = 10L;

    @Autowired
    private StokOperasiService operasi;

    @Autowired
    private FakeBukuKas fake;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bersihkan() {
        fake.reset();
        jdbc.execute("TRUNCATE TABLE mutasi_stok, stok_cache, posting_buku_kas, audit_log CASCADE");
        jdbc.execute("TRUNCATE TABLE menu CASCADE");
        jdbc.update("INSERT INTO menu (id, sekolah_id, nama, harga_jual) VALUES (?, ?, ?, ?)",
                MENU, SEKOLAH, "Nasi Uduk", 8_000L);
    }

    @Test
    @DisplayName("barang masuk → 1 entri KELUAR/TUNAI pos 'Belanja Stok Kantin', refId deterministik, refModul null")
    void barangMasukDipostingSekali() {
        operasi.masukBarang(SEKOLAH, MENU, 10, 5_000, "BM-1", 1L);

        assertThat(fake.dipanggil).hasSize(1);
        PerintahBukuKas p = fake.dipanggil.get(0);
        assertThat(p.getSekolahId()).isEqualTo(SEKOLAH);
        assertThat(p.getTipe()).isEqualTo(TipeBukuKas.KELUAR);
        assertThat(p.getMetode()).isEqualTo(MetodeBukuKas.TUNAI);
        assertThat(p.getKategori()).isEqualTo("Belanja Stok Kantin");
        assertThat(p.getJumlah()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(p.getRefId()).isEqualTo("KANTIN-BM-BM-1-10");
        // Mitigasi Q3: refModul = null sampai admin-be menambah case kantin.
        assertThat(p.getRefModul()).isNull();

        // Penanda idempotency tersimpan + audit tercatat.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM posting_buku_kas WHERE referensi_id = 'KANTIN-BM-BM-1-10' "
                        + "AND entitas = 'BARANG_MASUK' AND status = 'SUKSES'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE aksi = 'POSTING_BUKU_KAS'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("retry nomor bukti sama → tetap 1 entri (idempoten)")
    void retryBarangMasukTetapSatu() {
        operasi.masukBarang(SEKOLAH, MENU, 10, 5_000, "BM-1", 1L);
        operasi.masukBarang(SEKOLAH, MENU, 10, 5_000, "BM-1", 1L);

        // Replay mutasi stok + penanda sudah ada → port TIDAK dipanggil dua kali.
        assertThat(fake.dipanggil).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM posting_buku_kas WHERE entitas = 'BARANG_MASUK'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("pembalik → entri koreksi MASUK pos 'Penyesuaian Kantin' (entri lama tetap)")
    void pembalikPostingEntriKoreksi() {
        Long asalId = operasi.masukBarang(SEKOLAH, MENU, 10, 5_000, "BM-1", 1L)
                .getMutasi().getId();
        assertThat(fake.dipanggil).hasSize(1);

        operasi.pembalikBarangMasuk(SEKOLAH, asalId, 4, "Salah input", "PB-1", 2L);

        assertThat(fake.dipanggil).hasSize(2);
        PerintahBukuKas koreksi = fake.dipanggil.get(1);
        assertThat(koreksi.getTipe()).isEqualTo(TipeBukuKas.MASUK);
        assertThat(koreksi.getKategori()).isEqualTo("Penyesuaian Kantin");
        assertThat(koreksi.getJumlah()).isEqualByComparingTo(new BigDecimal("20000")); // 4 × 5.000
        assertThat(koreksi.getRefId()).isEqualTo("KANTIN-BMP-PB-1");

        // Entri belanja lama TIDAK dihapus + penanda koreksi tercatat.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM posting_buku_kas WHERE entitas = 'BARANG_MASUK'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM posting_buku_kas WHERE entitas = 'BARANG_MASUK_PEMBALIK'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("kegagalan posting TIDAK membatalkan mutasi stok (fail-open); tanpa penanda")
    void gagalPostingTidakMembatalkanMutasi() {
        fake.melempar = true;

        operasi.masukBarang(SEKOLAH, MENU, 10, 5_000, "BM-1", 1L);

        // Mutasi stok tetap tercatat (stok bertambah), penanda tidak ditulis.
        assertThat(jdbc.queryForObject(
                "SELECT stok FROM stok_cache WHERE menu_id = ?", Integer.class, MENU))
                .isEqualTo(10);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM posting_buku_kas", Integer.class)).isZero();
    }

    /** Fake port yang merekam perintah & bisa disetel sukses/gagal. */
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
