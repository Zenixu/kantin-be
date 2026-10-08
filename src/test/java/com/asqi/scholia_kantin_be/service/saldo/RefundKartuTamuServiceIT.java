package com.asqi.scholia_kantin_be.service.saldo;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.dto.HasilRefundResponse;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.KartuTamu;
import com.asqi.scholia_kantin_be.service.kartu.KartuTamuService;
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
 * Uji integrasi refund tunai pengembalian Kartu Tamu &amp; pindah saldo kartu
 * hilang (PRD §9.4, issue #120) dengan PostgreSQL nyata (Testcontainers).
 *
 * <p>Menegakkan: refund menunai mengosongkan saldo &amp; label pemegang (kartu
 * tetap aktif); pindah saldo memblokir kartu lama &amp; memindah ke kartu baru;
 * idempotency; tenant scoping; validasi.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
@DisplayName("RefundKartuTamuService — refund tunai & pindah saldo kartu hilang")
class RefundKartuTamuServiceIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;
    private static final long AKTOR = 9L;

    @Autowired
    private RefundKartuTamuService service;

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
        jdbc.execute("TRUNCATE TABLE kartu_tamu, saldo_ledger, saldo_cache, blokir_kartu, audit_log CASCADE");
    }

    private void isiSaldo(long kartuId, long nominal) {
        tx.executeWithoutResult(s -> ledger.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.KARTU_TAMU).subjekId(kartuId)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI).nominal(nominal)
                .idempotencyKey("seed-kt-" + kartuId + "-" + System.nanoTime()).build()));
    }

    // ────────────────────────────── REFUND ──────────────────────────────

    @Test
    @DisplayName("#120: refund pengembalian kartu → saldo 0, label dikosongkan, kartu tetap aktif")
    void refundPengembalianKartu() {
        KartuTamu k = kartuService.buatKartu(SEKOLAH, null, null, null, "Bu Sari", AKTOR);
        isiSaldo(k.getId(), 27_000);

        HasilRefundResponse hasil = service.refund(
                SEKOLAH, k.getId(), "REF-KT-1", "kartu dikembalikan", AKTOR);

        assertThat(hasil.getNominal()).isEqualTo(27_000L);
        assertThat(hasil.getSaldoSetelah()).isZero();
        assertThat(hasil.isLabelDikosongkan()).isTrue();
        assertThat(ledger.saldo(SEKOLAH, SubjekTipe.KARTU_TAMU, k.getId())).isZero();

        KartuTamu sesudah = kartuService.detailKartu(SEKOLAH, k.getId());
        assertThat(sesudah.getLabelPemegang()).isNull();
        assertThat(sesudah.getAktif()).isTrue(); // kartu tetap aktif, siap dipakai ulang

        String jenis = jdbc.queryForObject(
                "SELECT jenis FROM saldo_ledger WHERE subjek_id = ? AND arah = 'DEBIT'",
                String.class, k.getId());
        assertThat(jenis).isEqualTo("REFUND");
    }

    @Test
    @DisplayName("#120: refund idempoten — bukti sama tidak mengembalikan dua kali")
    void refundIdempoten() {
        KartuTamu k = kartuService.buatKartu(SEKOLAH, null, null, null, "Tamu", AKTOR);
        isiSaldo(k.getId(), 15_000);

        HasilRefundResponse pertama = service.refund(SEKOLAH, k.getId(), "REF-KT-2", null, AKTOR);
        HasilRefundResponse kedua = service.refund(SEKOLAH, k.getId(), "REF-KT-2", null, AKTOR);

        assertThat(pertama.isIdempoten()).isFalse();
        assertThat(kedua.isIdempoten()).isTrue();
        assertThat(kedua.getNominal()).isEqualTo(15_000L);

        Integer baris = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saldo_ledger WHERE subjek_id = ? AND jenis = 'REFUND'",
                Integer.class, k.getId());
        assertThat(baris).isEqualTo(1);
    }

    @Test
    @DisplayName("#120: refund saldo kosong → ConflictException")
    void refundSaldoKosongDitolak() {
        KartuTamu k = kartuService.buatKartu(SEKOLAH, null, null, null, "Tamu", AKTOR);

        assertThatThrownBy(() -> service.refund(SEKOLAH, k.getId(), "REF-KT-3", null, AKTOR))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("#120: refund kartu sekolah lain → 404")
    void refundTenantLain404() {
        KartuTamu k = kartuService.buatKartu(SEKOLAH, null, null, null, "Tamu", AKTOR);
        isiSaldo(k.getId(), 5_000);

        assertThatThrownBy(() -> service.refund(SEKOLAH_LAIN, k.getId(), "REF-KT-4", null, AKTOR))
                .isInstanceOf(NotFoundEntity.class);
    }

    // ─────────────────────── PINDAH KARTU HILANG ───────────────────────

    @Test
    @DisplayName("#120: pindah saldo kartu hilang → kartu lama diblokir, saldo pindah")
    void pindahKartuHilang() {
        KartuTamu lama = kartuService.buatKartu(SEKOLAH, null, null, null, "Bu Sari", AKTOR);
        KartuTamu baru = kartuService.buatKartu(SEKOLAH, null, null, null, "Bu Sari", AKTOR);
        isiSaldo(lama.getId(), 40_000);
        isiSaldo(baru.getId(), 3_000);

        HasilRefundResponse hasil = service.pindahKartuHilang(
                SEKOLAH, lama.getId(), baru.getId(), "BA-KT-1", "kartu hilang", AKTOR);

        assertThat(hasil.getNominal()).isEqualTo(40_000L);
        assertThat(hasil.getSaldoSetelah()).isZero();
        assertThat(hasil.getTujuanSubjekId()).isEqualTo(baru.getId());
        assertThat(hasil.isKartuDiblokir()).isTrue();
        assertThat(ledger.saldo(SEKOLAH, SubjekTipe.KARTU_TAMU, lama.getId())).isZero();
        assertThat(ledger.saldo(SEKOLAH, SubjekTipe.KARTU_TAMU, baru.getId())).isEqualTo(43_000L);

        // Kartu lama diblokir (PRD §9.4: berlaku instan).
        Boolean diblokir = jdbc.queryForObject(
                "SELECT diblokir FROM blokir_kartu WHERE subjek_tipe = 'KARTU_TAMU' AND subjek_id = ?",
                Boolean.class, lama.getId());
        assertThat(diblokir).isTrue();

        Integer transferRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saldo_ledger WHERE jenis = 'TRANSFER'", Integer.class);
        assertThat(transferRows).isEqualTo(2); // DEBIT + KREDIT
    }

    @Test
    @DisplayName("#120: pindah idempoten — berita acara sama tidak memindah dua kali")
    void pindahIdempoten() {
        KartuTamu lama = kartuService.buatKartu(SEKOLAH, null, null, null, "Tamu", AKTOR);
        KartuTamu baru = kartuService.buatKartu(SEKOLAH, null, null, null, "Tamu", AKTOR);
        isiSaldo(lama.getId(), 10_000);

        service.pindahKartuHilang(SEKOLAH, lama.getId(), baru.getId(), "BA-KT-2", null, AKTOR);
        service.pindahKartuHilang(SEKOLAH, lama.getId(), baru.getId(), "BA-KT-2", null, AKTOR);

        assertThat(ledger.saldo(SEKOLAH, SubjekTipe.KARTU_TAMU, baru.getId())).isEqualTo(10_000L);

        Integer transferRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saldo_ledger WHERE jenis = 'TRANSFER'", Integer.class);
        assertThat(transferRows).isEqualTo(2); // tetap 2, bukan 4
    }

    @Test
    @DisplayName("#120: pindah ke diri sendiri ditolak")
    void pindahKeDiriSendiriDitolak() {
        KartuTamu k = kartuService.buatKartu(SEKOLAH, null, null, null, "Tamu", AKTOR);
        isiSaldo(k.getId(), 10_000);

        assertThatThrownBy(() -> service.pindahKartuHilang(
                SEKOLAH, k.getId(), k.getId(), "BA-KT-3", null, AKTOR))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("tidak boleh sama");
    }

    @Test
    @DisplayName("#120: pindah ke kartu tujuan nonaktif ditolak")
    void pindahTujuanNonaktifDitolak() {
        KartuTamu lama = kartuService.buatKartu(SEKOLAH, null, null, null, "Tamu", AKTOR);
        KartuTamu baru = kartuService.buatKartu(SEKOLAH, null, null, null, "Tamu", AKTOR);
        isiSaldo(lama.getId(), 10_000);
        kartuService.nonaktifkanKartu(SEKOLAH, baru.getId(), AKTOR);

        assertThatThrownBy(() -> service.pindahKartuHilang(
                SEKOLAH, lama.getId(), baru.getId(), "BA-KT-4", null, AKTOR))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("nonaktif");
    }

    @Test
    @DisplayName("#120: pindah kartu sekolah lain → 404")
    void pindahTenantLain404() {
        KartuTamu lama = kartuService.buatKartu(SEKOLAH, null, null, null, "Tamu", AKTOR);
        KartuTamu baru = kartuService.buatKartu(SEKOLAH, null, null, null, "Tamu", AKTOR);
        isiSaldo(lama.getId(), 10_000);

        assertThatThrownBy(() -> service.pindahKartuHilang(
                SEKOLAH_LAIN, lama.getId(), baru.getId(), "BA-KT-5", null, AKTOR))
                .isInstanceOf(NotFoundEntity.class);
    }
}
