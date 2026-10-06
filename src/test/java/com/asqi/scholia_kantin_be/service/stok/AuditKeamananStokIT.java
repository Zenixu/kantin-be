package com.asqi.scholia_kantin_be.service.stok;

import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AUDIT KEAMANAN — uji regresi untuk cacat integritas alur stok.
 *
 * <p>Menegakkan:
 * <ul>
 *   <li><b>A</b> barang masuk <b>idempoten</b> — retry nomor bukti sama tidak
 *       menggandakan stok (kontrak {@code BarangMasukRequest.referensiId}).</li>
 *   <li><b>B</b> pembalik dengan bukti sama dijalankan paralel → tepat satu baris.</li>
 *   <li><b>C</b> barang masuk untuk menu tak dikenal / sekolah lain <b>ditolak</b>
 *       (cegah "stok hantu" & polusi lintas-tenant, PRD §11.4).</li>
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class AuditKeamananStokIT {

    private static final long SEKOLAH = 1L;
    private static final long MENU = 10L;

    @Autowired
    private LedgerStokService ledger;

    @Autowired
    private StokOperasiService operasi;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE mutasi_stok, stok_cache CASCADE");
        jdbc.execute("TRUNCATE TABLE menu CASCADE");
        // Menu milik sekolah 1 — prasyarat sah untuk barang masuk.
        jdbc.update("INSERT INTO menu (id, sekolah_id, nama, harga_jual) VALUES (?, ?, ?, ?)",
                MENU, SEKOLAH, "Nasi Uduk", 8_000L);
    }

    // ────────────────────────────────────────────────────────────────
    // TEMUAN A — barang masuk TIDAK idempoten (kontrak DTO mengklaim idempoten)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AUDIT-A: barang masuk nomor bukti SAMA tidak menggandakan stok")
    void barangMasukHarusIdempoten() {
        // Klien kirim bukti BM-1; jaringan timeout; klien retry bukti SAMA.
        operasi.masukBarang(SEKOLAH, MENU, 10, 5_000, "BM-1", 1L);
        operasi.masukBarang(SEKOLAH, MENU, 10, 5_000, "BM-1", 1L);

        Integer baris = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_stok WHERE jenis = 'BARANG_MASUK' AND referensi_id = 'BM-1'",
                Integer.class);

        assertThat(baris)
                .as("retry nomor bukti sama harus di-replay, bukan jadi mutasi kedua")
                .isEqualTo(1);
        assertThat(ledger.stok(SEKOLAH, MENU))
                .as("stok tidak boleh bertambah dua kali untuk bukti yang sama")
                .isEqualTo(10);
    }

    // ────────────────────────────────────────────────────────────────
    // TEMUAN B — idempotency pembalik harus tetap tepat satu baris saat balapan
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AUDIT-B: pembalik bukti SAMA dijalankan paralel → tepat satu baris")
    void pembalikBuktiSamaBersamaan() throws Exception {
        Long asalId = operasi.masukBarang(SEKOLAH, MENU, 20, 5_000, "BM-1", 1L)
                .getMutasi().getId();

        int paralel = 6;
        ExecutorService pool = Executors.newFixedThreadPool(paralel);
        CountDownLatch start = new CountDownLatch(1);

        @SuppressWarnings("unchecked")
        Future<Boolean>[] futures = new Future[paralel];
        for (int i = 0; i < paralel; i++) {
            futures[i] = pool.submit(() -> {
                start.await();
                try {
                    operasi.pembalikBarangMasuk(SEKOLAH, asalId, 5, "KOREKSI", "PB-SAMA", 2L);
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            });
        }
        start.countDown();
        for (Future<Boolean> f : futures) {
            f.get();
        }
        pool.shutdown();

        Integer jumlahPembalik = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_stok WHERE jenis = 'BARANG_MASUK_PEMBALIK' AND referensi_id = 'PB-SAMA'",
                Integer.class);

        assertThat(jumlahPembalik)
                .as("bukti pembalik sama harus menghasilkan TEPAT satu mutasi (idempotent)")
                .isEqualTo(1);
        assertThat(ledger.stok(SEKOLAH, MENU))
                .as("stok harus 15 (20 - 5), bukan berkurang berkali-kali")
                .isEqualTo(15);
    }

    // ────────────────────────────────────────────────────────────────
    // TEMUAN C — barang masuk tidak memverifikasi menu milik tenant
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AUDIT-C: barang masuk menu tak dikenal / sekolah lain ditolak")
    void barangMasukMenuTidakDikenal() {
        assertThatThrownBy(() -> operasi.masukBarang(SEKOLAH, 999_999L, 5, 1_000, "BM-X", 1L))
                .as("menu tak dikenal harus 404, bukan membuat stok hantu")
                .isInstanceOf(NotFoundEntity.class);

        Integer barisStok = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stok_cache WHERE menu_id = 999999", Integer.class);
        assertThat(barisStok)
                .as("tidak boleh ada baris stok untuk menu tak dikenal")
                .isZero();
    }
}
