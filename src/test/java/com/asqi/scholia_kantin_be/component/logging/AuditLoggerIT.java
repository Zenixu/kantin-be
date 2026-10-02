package com.asqi.scholia_kantin_be.component.logging;

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
 * Uji audit log nyata (PRD §11.7) — <b>B18</b>.
 *
 * <p>Memverifikasi audit ditulis ke tabel {@code audit_log} (append-only) saat
 * barang masuk &amp; opname, dan UPDATE/DELETE ditolak trigger DB.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class AuditLoggerIT {

    private static final long SEKOLAH = 1L;
    private static final long MENU = 10L;

    @Autowired
    private LedgerStokService ledgerStok;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE audit_log, mutasi_stok, stok_cache CASCADE");
    }

    @Test
    @DisplayName("barang masuk tercatat di audit_log (aksi, nilai_lama, nilai_baru)")
    void barangMasukDiaudit() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU, 10, 5_000, "BARANG_MASUK", "BM-1", 42L));

        Integer jumlah = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE aksi = 'BARANG_MASUK' AND sekolah_id = ?",
                Integer.class, SEKOLAH);
        assertThat(jumlah).isEqualTo(1);

        var baris = jdbc.queryForMap(
                "SELECT aktor_id, entitas, nilai_lama, nilai_baru FROM audit_log "
                        + "WHERE aksi = 'BARANG_MASUK' AND sekolah_id = ?", SEKOLAH);
        assertThat(((Number) baris.get("aktor_id")).longValue()).isEqualTo(42L);
        assertThat(baris.get("entitas")).isEqualTo("Stok");
        assertThat((String) baris.get("nilai_lama")).contains("stok=0");
        assertThat((String) baris.get("nilai_baru")).contains("stok=10").contains("hpp=5000");
    }

    @Test
    @DisplayName("opname mencatat audit + alasan")
    void opnameDiaudit() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU, 10, 5_000, "BARANG_MASUK", "BM-1", 1L));
        tx.executeWithoutResult(s -> ledgerStok.sesuaikanOpname(SEKOLAH, MENU, 7, "HILANG", "OP-1", 42L));

        var baris = jdbc.queryForMap(
                "SELECT alasan, nilai_lama, nilai_baru FROM audit_log "
                        + "WHERE aksi = 'OPNAME_STOK' AND sekolah_id = ?", SEKOLAH);
        assertThat(baris.get("alasan")).isEqualTo("HILANG");
        assertThat((String) baris.get("nilai_lama")).contains("stok=10");
        assertThat((String) baris.get("nilai_baru")).contains("stok=7");
    }

    @Test
    @DisplayName("opname tanpa selisih tidak menulis audit")
    void opnameTanpaSelisihTanpaAudit() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU, 10, 5_000, "BARANG_MASUK", "BM-1", 1L));
        tx.executeWithoutResult(s -> ledgerStok.sesuaikanOpname(SEKOLAH, MENU, 10, "COCOK", "OP-2", 1L));

        Integer jumlah = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE aksi = 'OPNAME_STOK'", Integer.class);
        assertThat(jumlah).isZero();
    }

    @Test
    @DisplayName("append-only — UPDATE & DELETE audit_log ditolak")
    void appendOnlyAudit() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU, 1, 5_000, "BARANG_MASUK", "BM-1", 1L));

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET alasan = 'x'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log"))
                .hasMessageContaining("append-only");
    }
}
