package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
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

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi ledger saldo dengan <b>PostgreSQL nyata</b> (Testcontainers).
 *
 * <p>Menegakkan jaminan paling kritis (ADR-0003, PRD §11.1–11.3):
 * <ul>
 *   <li>append-only (UPDATE/DELETE ditolak trigger DB),</li>
 *   <li>atomik &amp; bebas race (debit bersamaan),</li>
 *   <li>saldo tak pernah minus,</li>
 *   <li>idempotency (key sama ≠ potong dua kali).</li>
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class LedgerSaldoServiceIT {

    private static final long SEKOLAH = 1L;

    @Autowired
    private LedgerSaldoService ledger;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    private Long subjekId;

    @BeforeEach
    void bersihkan() {
        // Urutan penting: hapus anak dulu (trigger append-only melarang DELETE
        // pada ledger, jadi test ini memakai skema bersih per-kelas).
        jdbc.execute("TRUNCATE TABLE saldo_ledger, saldo_cache CASCADE");
        subjekId = 1000L;
    }

    private PerintahMutasiSaldo perintah(JenisMutasiSaldo jenis, long nominal, String key) {
        return PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH)
                .subjekTipe(SubjekTipe.SISWA)
                .subjekId(subjekId)
                .jenis(jenis)
                .nominal(nominal)
                .idempotencyKey(key)
                .build();
    }

    private void isiSaldo(long nominal) {
        tx.executeWithoutResult(s -> ledger.kredit(
                perintah(JenisMutasiSaldo.TOPUP_TUNAI, nominal, "topup-" + nominal + "-" + System.nanoTime())));
    }

    @Test
    @DisplayName("kredit & debit mengubah saldo dan menambah baris ledger")
    void kreditDebit() {
        tx.executeWithoutResult(s -> ledger.kredit(perintah(JenisMutasiSaldo.TOPUP_TUNAI, 50_000, "k1")));
        var hasil = tx.execute(s -> ledger.debit(perintah(JenisMutasiSaldo.PENJUALAN, 8_000, "k2")));

        assertThat(hasil.getSaldoSetelah()).isEqualTo(42_000L);
        assertThat(ledger.saldo(SEKOLAH, SubjekTipe.SISWA, subjekId)).isEqualTo(42_000L);
        assertThat(ledger.hitungUlangDariLedger(SEKOLAH, SubjekTipe.SISWA, subjekId)).isEqualTo(42_000L);

        Integer baris = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saldo_ledger WHERE sekolah_id = ?", Integer.class, SEKOLAH);
        assertThat(baris).isEqualTo(2);
    }

    @Test
    @DisplayName("idempotency — key sama tidak memotong dua kali")
    void idempotencyKeySama() {
        tx.executeWithoutResult(s -> ledger.kredit(perintah(JenisMutasiSaldo.TOPUP_TUNAI, 50_000, "seed")));

        var pertama = tx.execute(s -> ledger.debit(perintah(JenisMutasiSaldo.PENJUALAN, 10_000, "tap-abc")));
        var kedua = tx.execute(s -> ledger.debit(perintah(JenisMutasiSaldo.PENJUALAN, 10_000, "tap-abc")));

        assertThat(pertama.isIdempotentReplay()).isFalse();
        assertThat(kedua.isIdempotentReplay()).isTrue();
        assertThat(kedua.getSaldoSetelah()).isEqualTo(40_000L);
        assertThat(ledger.saldo(SEKOLAH, SubjekTipe.SISWA, subjekId)).isEqualTo(40_000L);

        Integer barisDebit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saldo_ledger WHERE idempotency_key = 'tap-abc'", Integer.class);
        assertThat(barisDebit).isEqualTo(1);
    }

    @Test
    @DisplayName("saldo kurang → ConflictException, tanpa mutasi, saldo tetap")
    void saldoKurangDitolak() {
        isiSaldo(5_000);

        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                ledger.debit(perintah(JenisMutasiSaldo.PENJUALAN, 8_000, "kurang"))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Saldo kurang");

        assertThat(ledger.saldo(SEKOLAH, SubjekTipe.SISWA, subjekId)).isEqualTo(5_000L);
        Integer barisDebit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saldo_ledger WHERE arah = 'DEBIT'", Integer.class);
        assertThat(barisDebit).isZero();
    }

    @Test
    @DisplayName("debit bersamaan — saldo tak pernah minus (race)")
    void debitBersamaanTidakMinus() throws Exception {
        isiSaldo(100_000);

        int paralel = 8;
        long potong = 20_000; // hanya 5 yang boleh lolos (100k / 20k)
        ExecutorService pool = Executors.newFixedThreadPool(paralel);
        CountDownLatch mulai = new CountDownLatch(1);
        AtomicInteger sukses = new AtomicInteger();
        AtomicInteger gagal = new AtomicInteger();

        Callable<Void> tugas = () -> {
            mulai.await();
            try {
                tx.executeWithoutResult(s ->
                        ledger.debit(perintah(JenisMutasiSaldo.PENJUALAN, potong, "race-" + System.nanoTime())));
                sukses.incrementAndGet();
            } catch (ConflictException e) {
                gagal.incrementAndGet();
            }
            return null;
        };

        var futures = new java.util.ArrayList<Future<Void>>();
        for (int i = 0; i < paralel; i++) {
            futures.add(pool.submit(tugas));
        }
        mulai.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        long saldoAkhir = ledger.saldo(SEKOLAH, SubjekTipe.SISWA, subjekId);
        assertThat(sukses.get()).isEqualTo(5);
        assertThat(gagal.get()).isEqualTo(3);
        assertThat(saldoAkhir).isZero();
        assertThat(saldoAkhir).isGreaterThanOrEqualTo(0);
        // Jumlah debit yang tercatat = jumlah sukses (tidak ada double-spend).
        Integer debitRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saldo_ledger WHERE arah = 'DEBIT'", Integer.class);
        assertThat(debitRows).isEqualTo(5);
    }

    @Test
    @DisplayName("append-only — UPDATE & DELETE pada ledger ditolak trigger DB")
    void appendOnlyDitolak() {
        isiSaldo(10_000);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE saldo_ledger SET nominal = 1 WHERE sekolah_id = ?", SEKOLAH))
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM saldo_ledger WHERE sekolah_id = ?", SEKOLAH))
                .hasMessageContaining("append-only");
    }
}
