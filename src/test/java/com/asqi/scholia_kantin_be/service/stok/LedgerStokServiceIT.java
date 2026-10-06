package com.asqi.scholia_kantin_be.service.stok;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.enums.ArahStok;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi ledger stok &amp; HPP dengan PostgreSQL nyata (Testcontainers).
 *
 * <p>Menegakkan: stok tak minus saat penjualan bersamaan, HPP rata-rata
 * tertimbang, void mengembalikan stok dengan HPP snapshot, append-only.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class LedgerStokServiceIT {

    private static final long SEKOLAH = 1L;
    private static final long MENU = 10L;

    @Autowired
    private LedgerStokService ledger;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE mutasi_stok, stok_cache CASCADE");
    }

    @Test
    @DisplayName("barang masuk memperbarui stok & HPP rata-rata tertimbang")
    void barangMasukHpp() {
        tx.executeWithoutResult(s -> ledger.masukBarang(SEKOLAH, MENU, 10, 5_000, "BARANG_MASUK", "BM-1", 1L));
        var hasil = tx.execute(s -> ledger.masukBarang(SEKOLAH, MENU, 10, 6_000, "BARANG_MASUK", "BM-2", 1L));

        assertThat(hasil.getStokSetelah()).isEqualTo(20);
        assertThat(hasil.getHppSetelah()).isEqualTo(5_500L); // (10×5000 + 10×6000)/20
        assertThat(ledger.hitungUlangDariLedger(SEKOLAH, MENU)).isEqualTo(20L);
    }

    @Test
    @DisplayName("penjualan memakai HPP snapshot & mengurangi stok")
    void penjualanPakaiSnapshot() {
        tx.executeWithoutResult(s -> ledger.masukBarang(SEKOLAH, MENU, 10, 5_000, "BARANG_MASUK", "BM-1", 1L));

        var hasil = tx.execute(s -> ledger.keluarPenjualan(SEKOLAH, MENU, 3, 999L));
        assertThat(hasil.getStokSetelah()).isEqualTo(7);
        assertThat(hasil.getHppSetelah()).isEqualTo(5_000L); // snapshot = HPP berjalan
    }

    @Test
    @DisplayName("stok tidak cukup → ConflictException")
    void stokTidakCukup() {
        tx.executeWithoutResult(s -> ledger.masukBarang(SEKOLAH, MENU, 2, 5_000, "BARANG_MASUK", "BM-1", 1L));

        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                ledger.keluarPenjualan(SEKOLAH, MENU, 5, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Stok tidak cukup");

        assertThat(ledger.stok(SEKOLAH, MENU)).isEqualTo(2);
    }

    @Test
    @DisplayName("penjualan bersamaan — stok tak pernah minus")
    void penjualanBersamaan() throws Exception {
        tx.executeWithoutResult(s -> ledger.masukBarang(SEKOLAH, MENU, 10, 5_000, "BARANG_MASUK", "BM-1", 1L));

        int paralel = 6;
        ExecutorService pool = Executors.newFixedThreadPool(paralel);
        CountDownLatch mulai = new CountDownLatch(1);
        AtomicInteger sukses = new AtomicInteger();
        AtomicInteger gagal = new AtomicInteger();

        var futures = new java.util.ArrayList<Future<Void>>();
        for (int i = 0; i < paralel; i++) {
            futures.add(pool.submit(() -> {
                mulai.await();
                try {
                    tx.executeWithoutResult(s -> ledger.keluarPenjualan(SEKOLAH, MENU, 3, null));
                    sukses.incrementAndGet();
                } catch (ConflictException e) {
                    gagal.incrementAndGet();
                }
                return null;
            }));
        }
        mulai.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(sukses.get()).isEqualTo(3); // 10 / 3 → 3 sukses
        assertThat(gagal.get()).isEqualTo(3);
        assertThat(ledger.stok(SEKOLAH, MENU)).isEqualTo(1);
    }

    @Test
    @DisplayName("opname tanpa selisih tidak membuat mutasi")
    void opnameTanpaSelisih() {
        tx.executeWithoutResult(s -> ledger.masukBarang(SEKOLAH, MENU, 5, 5_000, "BARANG_MASUK", "BM-1", 1L));
        var hasil = tx.execute(s -> ledger.sesuaikanOpname(SEKOLAH, MENU, 5, "SALAH HITUNG", null, 1L));
        assertThat(hasil.getMutasi()).isNull();

        Integer baris = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_stok WHERE jenis LIKE 'OPNAME%'", Integer.class);
        assertThat(baris).isZero();
    }

    @Test
    @DisplayName("append-only — UPDATE/DELETE mutasi_stok ditolak")
    void appendOnly() {
        tx.executeWithoutResult(s -> ledger.masukBarang(SEKOLAH, MENU, 5, 5_000, "BARANG_MASUK", "BM-1", 1L));

        assertThatThrownBy(() -> jdbc.update("UPDATE mutasi_stok SET qty = 1"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM mutasi_stok"))
                .hasMessageContaining("append-only");
    }

    // ────────────────────────────────────────────────────────────────
    // PEMBALIK BARANG MASUK (PRD §7.2)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pembalik penuh mengembalikan stok & HPP ke kondisi sebelum barang masuk")
    void pembalikPenuh() {
        tx.executeWithoutResult(s -> ledger.masukBarang(SEKOLAH, MENU, 10, 5_000, "BARANG_MASUK", "BM-1", 1L));
        Long asalId = tx.execute(s -> ledger.masukBarang(SEKOLAH, MENU, 10, 6_000, "BARANG_MASUK", "BM-2", 1L)
                .getMutasi().getId());

        // Stok 20 @ HPP 5500. Balik seluruh batch kedua (10 @ 6000) → stok 10 @ HPP 5000.
        var hasil = tx.execute(s -> ledger.pembalikBarangMasuk(SEKOLAH, asalId, null, "SALAH INPUT", "PB-1", 2L));

        assertThat(hasil.getStokSetelah()).isEqualTo(10);
        assertThat(hasil.getHppSetelah()).isEqualTo(5_000L);
        assertThat(hasil.getMutasi().getJenis()).isEqualTo(JenisMutasiStok.BARANG_MASUK_PEMBALIK);
        assertThat(hasil.getMutasi().getArah()).isEqualTo(ArahStok.KELUAR);
        assertThat(hasil.getMutasi().getMutasiAsalId()).isEqualTo(asalId);
        assertThat(ledger.hitungUlangDariLedger(SEKOLAH, MENU)).isEqualTo(10L);
    }

    @Test
    @DisplayName("pembalik sebagian hanya mengurangi qty yang diminta")
    void pembalikSebagian() {
        Long asalId = tx.execute(s -> ledger.masukBarang(SEKOLAH, MENU, 10, 5_000, "BARANG_MASUK", "BM-1", 1L)
                .getMutasi().getId());

        var hasil = tx.execute(s -> ledger.pembalikBarangMasuk(SEKOLAH, asalId, 4, "SALAH HITUNG", "PB-1", 2L));

        assertThat(hasil.getStokSetelah()).isEqualTo(6);
        assertThat(ledger.totalDibalik(SEKOLAH, asalId)).isEqualTo(4L);
    }

    @Test
    @DisplayName("pembalik melebihi sisa yang belum dibalik ditolak")
    void pembalikMelebihiSisa() {
        Long asalId = tx.execute(s -> ledger.masukBarang(SEKOLAH, MENU, 5, 5_000, "BARANG_MASUK", "BM-1", 1L)
                .getMutasi().getId());
        tx.executeWithoutResult(s -> ledger.pembalikBarangMasuk(SEKOLAH, asalId, 3, "KOREKSI", "PB-1", 2L));

        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                ledger.pembalikBarangMasuk(SEKOLAH, asalId, 3, "KOREKSI", "PB-2", 2L)))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("melebihi sisa");
    }

    @Test
    @DisplayName("pembalik barang masuk yang sudah dibalik seluruhnya ditolak")
    void pembalikSudahHabis() {
        Long asalId = tx.execute(s -> ledger.masukBarang(SEKOLAH, MENU, 5, 5_000, "BARANG_MASUK", "BM-1", 1L)
                .getMutasi().getId());
        tx.executeWithoutResult(s -> ledger.pembalikBarangMasuk(SEKOLAH, asalId, null, "KOREKSI", "PB-1", 2L));

        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                ledger.pembalikBarangMasuk(SEKOLAH, asalId, null, "KOREKSI", "PB-2", 2L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("sudah dibalik seluruhnya");
    }

    @Test
    @DisplayName("pembalik idempoten: bukti sama tidak membalik dua kali")
    void pembalikIdempoten() {
        Long asalId = tx.execute(s -> ledger.masukBarang(SEKOLAH, MENU, 10, 5_000, "BARANG_MASUK", "BM-1", 1L)
                .getMutasi().getId());

        var pertama = tx.execute(s -> ledger.pembalikBarangMasuk(SEKOLAH, asalId, 4, "KOREKSI", "PB-1", 2L));
        var kedua = tx.execute(s -> ledger.pembalikBarangMasuk(SEKOLAH, asalId, 4, "KOREKSI", "PB-1", 2L));

        assertThat(kedua.getMutasi().getId()).isEqualTo(pertama.getMutasi().getId());
        assertThat(ledger.stok(SEKOLAH, MENU)).isEqualTo(6); // tidak berkurang dua kali

        Integer jumlah = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mutasi_stok WHERE jenis = 'BARANG_MASUK_PEMBALIK'", Integer.class);
        assertThat(jumlah).isEqualTo(1);
    }

    @Test
    @DisplayName("pembalik menolak baris bukan BARANG_MASUK (mis. penjualan)")
    void pembalikHanyaBarangMasuk() {
        tx.executeWithoutResult(s -> ledger.masukBarang(SEKOLAH, MENU, 10, 5_000, "BARANG_MASUK", "BM-1", 1L));
        Long penjualanId = tx.execute(s -> ledger.keluarPenjualan(SEKOLAH, MENU, 2, 999L).getMutasi().getId());

        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                ledger.pembalikBarangMasuk(SEKOLAH, penjualanId, null, "KOREKSI", "PB-1", 2L)))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Hanya barang masuk");
    }

    @Test
    @DisplayName("pembalik wajib alasan & bukti")
    void pembalikWajibAlasanDanBukti() {
        Long asalId = tx.execute(s -> ledger.masukBarang(SEKOLAH, MENU, 5, 5_000, "BARANG_MASUK", "BM-1", 1L)
                .getMutasi().getId());

        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                ledger.pembalikBarangMasuk(SEKOLAH, asalId, null, "  ", "PB-1", 2L)))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Alasan");
        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                ledger.pembalikBarangMasuk(SEKOLAH, asalId, null, "KOREKSI", "  ", 2L)))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("bukti");
    }
}
