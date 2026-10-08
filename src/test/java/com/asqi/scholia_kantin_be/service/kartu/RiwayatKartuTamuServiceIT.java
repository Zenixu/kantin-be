package com.asqi.scholia_kantin_be.service.kartu;

import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.dto.RiwayatKartuTamuResponse;
import com.asqi.scholia_kantin_be.dto.RiwayatTransaksiKartuItem;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.KartuTamu;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
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
 * Uji integrasi riwayat transaksi per Kartu Tamu (PRD §9.4/§9.5, issue #122)
 * dengan PostgreSQL nyata (Testcontainers).
 *
 * <p>Menegakkan: riwayat memuat identitas + saldo + transaksi (terbaru dulu,
 * berhalaman) + mutasi saldo; item transaksi diproyeksikan; transaksi VOID
 * menampilkan alasan; isolasi tenant (sekolah lain → 404).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
@DisplayName("RiwayatKartuTamuService — riwayat per kartu tamu")
class RiwayatKartuTamuServiceIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;
    private static final long AKTOR = 9L;
    private static final long TITIK = 1L;

    @Autowired
    private RiwayatKartuTamuService riwayatService;

    @Autowired
    private KartuTamuService kartuService;

    @Autowired
    private LedgerSaldoService ledger;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE transaksi_item, transaksi, sesi_kasir, titik_kasir, "
                + "kartu_tamu, saldo_ledger, saldo_cache CASCADE");
        jdbc.update("INSERT INTO titik_kasir (id, sekolah_id, nama, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'Kasir 1', true, now(), now())", TITIK, SEKOLAH);
        jdbc.update("INSERT INTO sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status, "
                + "created_at, updated_at) VALUES (?, ?, ?, CURRENT_DATE, 'TERBUKA', now(), now())",
                1L, SEKOLAH, TITIK);
    }

    private void isiSaldo(long kartuId, long nominal) {
        tx.executeWithoutResult(s -> ledger.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.KARTU_TAMU).subjekId(kartuId)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI).nominal(nominal)
                .idempotencyKey("seed-" + kartuId + "-" + System.nanoTime()).build()));
    }

    /** Seed satu transaksi (header + 1 item) untuk kartu. */
    private void seedTransaksi(long trxId, long kartuId, String status, long total) {
        jdbc.update("INSERT INTO transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, "
                + "titik_kasir_id, subjek_tipe, subjek_id, kartu_uid, petugas_id, total, total_hpp, "
                + "status, alasan_void, void_at, void_oleh, waktu, created_at, updated_at) "
                + "VALUES (?, ?, ?, 1, ?, 'KARTU_TAMU', ?, 'UID-KT', 555, ?, 4000, ?, ?, ?, ?, now(), now(), now())",
                trxId, "idem-" + trxId, SEKOLAH, TITIK, kartuId, total, status,
                "VOID".equals(status) ? "salah" : null,
                "VOID".equals(status) ? java.sql.Timestamp.from(java.time.Instant.now()) : null,
                "VOID".equals(status) ? 555L : null);
        jdbc.update("INSERT INTO transaksi_item (id, transaksi_id, menu_id, nama_menu, kategori_id, "
                + "harga_jual, qty, hpp_snapshot, subtotal, created_at) "
                + "VALUES (?, ?, 10, 'Nasi Uduk', 100, 8000, 1, 4000, ?, now())",
                trxId * 100, trxId, total);
    }

    @Test
    @DisplayName("#122: riwayat memuat identitas, saldo, transaksi (terbaru dulu) & mutasi")
    void riwayatLengkap() {
        KartuTamu k = kartuService.buatKartu(SEKOLAH, null, null, null, "Bu Sari", AKTOR);
        isiSaldo(k.getId(), 50_000);
        seedTransaksi(1001L, k.getId(), "SUKSES", 8_000);
        seedTransaksi(1002L, k.getId(), "SUKSES", 16_000);

        RiwayatKartuTamuResponse r = riwayatService.riwayat(SEKOLAH, k.getId(), 20, 0, 20);

        assertThat(r.getKartuId()).isEqualTo(k.getId());
        assertThat(r.getNomorKartu()).isEqualTo(k.getNomorKartu());
        assertThat(r.getLabelPemegang()).isEqualTo("Bu Sari");
        assertThat(r.isAktif()).isTrue();
        assertThat(r.getSaldo()).isEqualTo(50_000L);
        assertThat(r.getTransaksi().getTotal()).isEqualTo(2);

        // Terbaru dulu (id desc).
        assertThat(r.getTransaksi().getItems())
                .extracting(RiwayatTransaksiKartuItem::getTransaksiId)
                .containsExactly(1002L, 1001L);

        // Item diproyeksikan.
        RiwayatTransaksiKartuItem pertama = r.getTransaksi().getItems().get(0);
        assertThat(pertama.getItems()).hasSize(1);
        assertThat(pertama.getItems().get(0).getNamaMenu()).isEqualTo("Nasi Uduk");
        assertThat(pertama.getItems().get(0).getSubtotal()).isEqualTo(16_000L);

        // Mutasi saldo disertakan.
        assertThat(r.getMutasi()).isNotEmpty();
    }

    @Test
    @DisplayName("#122: transaksi VOID menampilkan status & alasan void")
    void riwayatMenampilkanVoid() {
        KartuTamu k = kartuService.buatKartu(SEKOLAH, null, null, null, "Tamu", AKTOR);
        seedTransaksi(2001L, k.getId(), "VOID", 8_000);

        RiwayatKartuTamuResponse r = riwayatService.riwayat(SEKOLAH, k.getId(), 20, 0, 20);

        RiwayatTransaksiKartuItem item = r.getTransaksi().getItems().get(0);
        assertThat(item.getStatus()).isEqualTo(StatusTransaksi.VOID);
        assertThat(item.getAlasanVoid()).isEqualTo("salah");
        assertThat(item.getVoidAt()).isNotNull();
    }

    @Test
    @DisplayName("#122: hanya transaksi kartu ini yang muncul (bukan kartu lain)")
    void riwayatTerisolasiPerKartu() {
        KartuTamu a = kartuService.buatKartu(SEKOLAH, null, null, null, "A", AKTOR);
        KartuTamu b = kartuService.buatKartu(SEKOLAH, null, null, null, "B", AKTOR);
        seedTransaksi(3001L, a.getId(), "SUKSES", 8_000);
        seedTransaksi(3002L, b.getId(), "SUKSES", 16_000);

        RiwayatKartuTamuResponse r = riwayatService.riwayat(SEKOLAH, a.getId(), 20, 0, 20);

        assertThat(r.getTransaksi().getItems())
                .extracting(RiwayatTransaksiKartuItem::getTransaksiId)
                .containsExactly(3001L);
    }

    @Test
    @DisplayName("#122: paginasi transaksi")
    void riwayatBerhalaman() {
        KartuTamu k = kartuService.buatKartu(SEKOLAH, null, null, null, "Tamu", AKTOR);
        for (int i = 0; i < 5; i++) {
            seedTransaksi(4000L + i, k.getId(), "SUKSES", 8_000);
        }

        RiwayatKartuTamuResponse hal0 = riwayatService.riwayat(SEKOLAH, k.getId(), 20, 0, 2);
        assertThat(hal0.getTransaksi().getItems()).hasSize(2);
        assertThat(hal0.getTransaksi().getTotal()).isEqualTo(5);
        assertThat(hal0.getTransaksi().getTotalHalaman()).isEqualTo(3);
        assertThat(hal0.getTransaksi().getItems())
                .extracting(RiwayatTransaksiKartuItem::getTransaksiId)
                .containsExactly(4004L, 4003L);
    }

    @Test
    @DisplayName("#122: kartu sekolah lain → 404")
    void riwayatTenantLain404() {
        KartuTamu k = kartuService.buatKartu(SEKOLAH, null, null, null, "Tamu", AKTOR);

        assertThatThrownBy(() -> riwayatService.riwayat(SEKOLAH_LAIN, k.getId(), 20, 0, 20))
                .isInstanceOf(NotFoundEntity.class);
    }
}
