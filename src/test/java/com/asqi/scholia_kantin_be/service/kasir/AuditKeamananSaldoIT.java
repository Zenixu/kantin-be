package com.asqi.scholia_kantin_be.service.kasir;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AUDIT KEAMANAN — idempotency key saldo harus <b>tenant-scoped</b> (PRD §11.4).
 *
 * <p>Nomor bukti top-up (mis. {@code TU-2026-0001}) dibuat manusia dan
 * <b>berulang tiap sekolah</b>. Selama {@code idempotency_key} UNIQUE global +
 * lookup global, top-up sekolah B dengan nomor yang sama tertelan sebagai
 * "replay" milik sekolah A: saldo B tidak bertambah dan respons B membocorkan
 * saldo sekolah A. Uji ini menjaga perbaikan V10.
 *
 * <p>Catatan: {@code subjekId} memang global unik (satu siswa satu sekolah) —
 * lihat {@code IsolasiTenantLockIT} B17 — jadi {@code saldo_cache} tetap
 * berkunci {@code (subjek_tipe, subjek_id)}.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class AuditKeamananSaldoIT {

    private static final long SEKOLAH_A = 1L;
    private static final long SEKOLAH_B = 2L;

    @Autowired
    private LedgerSaldoService ledger;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE saldo_ledger, saldo_cache CASCADE");
    }

    private PerintahMutasiSaldo topup(long sekolahId, long subjekId, long nominal, String referensiId) {
        return PerintahMutasiSaldo.builder()
                .sekolahId(sekolahId)
                .subjekTipe(SubjekTipe.SISWA)
                .subjekId(subjekId)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI)
                .nominal(nominal)
                .idempotencyKey("TOPUP-TUNAI-" + referensiId)
                .referensiTipe("TOPUP")
                .referensiId(referensiId)
                .aktorId(9L)
                .build();
    }

    @Test
    @DisplayName("AUDIT-D: nomor bukti sama di dua sekolah tetap top-up independen")
    void idempotencyKeyTerpisahPerSekolah() {
        long subjekA = 1000L;
        long subjekB = 2000L;

        // Dua sekolah memakai nomor bukti yang sama (format berulang).
        var hasilA = tx.execute(s -> ledger.kredit(topup(SEKOLAH_A, subjekA, 50_000, "TU-2026-0001")));
        var hasilB = tx.execute(s -> ledger.kredit(topup(SEKOLAH_B, subjekB, 30_000, "TU-2026-0001")));

        assertThat(hasilA.isIdempotentReplay()).isFalse();
        assertThat(hasilB.isIdempotentReplay())
                .as("top-up sekolah B harus mutasi BARU, bukan replay milik sekolah A")
                .isFalse();

        assertThat(ledger.saldo(SEKOLAH_B, SubjekTipe.SISWA, subjekB))
                .as("saldo sekolah B harus 30000 (top-up-nya sendiri)")
                .isEqualTo(30_000L);
        assertThat(ledger.saldo(SEKOLAH_A, SubjekTipe.SISWA, subjekA))
                .as("saldo sekolah A tidak boleh berubah oleh aksi sekolah B")
                .isEqualTo(50_000L);
        assertThat(hasilB.getSaldoSetelah())
                .as("respons sekolah B tidak boleh memuat saldo sekolah A (bocor lintas-tenant)")
                .isEqualTo(30_000L);
    }
}
