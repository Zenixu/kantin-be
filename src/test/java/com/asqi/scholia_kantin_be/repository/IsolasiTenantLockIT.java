package com.asqi.scholia_kantin_be.repository;

import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import com.asqi.scholia_kantin_be.service.stok.LedgerStokService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji isolasi tenant pada kunci ({@code SELECT ... FOR UPDATE}) — <b>B17</b>.
 *
 * <p>Memverifikasi bahwa {@code sekolah_id} ikut di {@code WHERE} penguncian:
 * sekolah pemanggil <b>tidak</b> boleh mengunci / mengakses baris cache sekolah
 * lain, dan operasi atas data sekolah lain dijawab 404 (PRD §11.4), bukan
 * menyentuh baris orang lain.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class IsolasiTenantLockIT {

    private static final long SEKOLAH_A = 1L;
    private static final long SEKOLAH_B = 2L;
    private static final long SUBJEK = 500L;
    private static final long MENU = 900L;

    @Autowired
    private LedgerSaldoService ledgerSaldo;

    @Autowired
    private LedgerStokService ledgerStok;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE saldo_ledger, saldo_cache, mutasi_stok, stok_cache CASCADE");
    }

    private PerintahMutasiSaldo perintah(long sekolah, long nominal, String key) {
        return PerintahMutasiSaldo.builder()
                .sekolahId(sekolah)
                .subjekTipe(SubjekTipe.SISWA)
                .subjekId(SUBJEK)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI)
                .nominal(nominal)
                .idempotencyKey(key)
                .build();
    }

    @Test
    @DisplayName("B17 — sekolah lain TIDAK boleh memotong saldo subjek yang sama")
    void saldoTidakBocorLintasSekolah() {
        // Sekolah A punya saldo untuk subjek 500.
        tx.executeWithoutResult(s -> ledgerSaldo.kredit(perintah(SEKOLAH_A, 50_000, "a-1")));
        assertThat(ledgerSaldo.saldo(SEKOLAH_A, SubjekTipe.SISWA, SUBJEK)).isEqualTo(50_000L);

        // Sekolah B mencoba debit subjek yang sama. Karena kunci tenant-scoped,
        // baris milik A tak ditemukan untuk B → NotFoundEntity (404), baris A utuh.
        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                ledgerSaldo.debit(PerintahMutasiSaldo.builder()
                        .sekolahId(SEKOLAH_B)
                        .subjekTipe(SubjekTipe.SISWA)
                        .subjekId(SUBJEK)
                        .jenis(JenisMutasiSaldo.PENJUALAN)
                        .nominal(10_000)
                        .idempotencyKey("b-1")
                        .build())))
                .isInstanceOf(NotFoundEntity.class);

        // Saldo sekolah A utuh; tidak ada baris DEBIT di ledger A.
        assertThat(ledgerSaldo.saldo(SEKOLAH_A, SubjekTipe.SISWA, SUBJEK)).isEqualTo(50_000L);
        Integer debitA = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saldo_ledger WHERE sekolah_id = ? AND arah = 'DEBIT'",
                Integer.class, SEKOLAH_A);
        assertThat(debitA).isZero();
    }

    @Test
    @DisplayName("B17 — stok menu sekolah lain tidak bisa dijual lewat tenant berbeda")
    void stokTidakBocorLintasSekolah() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH_A, MENU, 10, 5_000, "BARANG_MASUK", "a-1", 1L));
        assertThat(ledgerStok.stok(SEKOLAH_A, MENU)).isEqualTo(10);

        // Sekolah B menjual menu yang sama: karena kunci tenant-scoped, baris
        // milik A tak terlihat oleh B → NotFoundEntity (404); stok A tak tersentuh.
        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                ledgerStok.keluarPenjualan(SEKOLAH_B, MENU, 1, null)))
                .isInstanceOf(NotFoundEntity.class);

        assertThat(ledgerStok.stok(SEKOLAH_A, MENU)).isEqualTo(10);
    }

    @Test
    @DisplayName("B17 — read cache tenant lain tidak membocorkan data (0, bukan nilai A)")
    void readTidakBocor() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH_A, MENU, 10, 5_000, "BARANG_MASUK", "a-1", 1L));

        // Baca sebagai sekolah B harus 0 (bukan 10 milik sekolah A).
        assertThat(ledgerStok.stok(SEKOLAH_B, MENU)).isZero();
        assertThat(ledgerSaldo.saldo(SEKOLAH_B, SubjekTipe.SISWA, SUBJEK)).isZero();
    }
}
