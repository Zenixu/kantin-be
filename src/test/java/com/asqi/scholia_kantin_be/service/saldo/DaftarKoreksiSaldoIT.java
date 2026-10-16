package com.asqi.scholia_kantin_be.service.saldo;

import com.asqi.scholia_kantin_be.dto.HalamanResponse;
import com.asqi.scholia_kantin_be.dto.MutasiKoreksiItem;
import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji integrasi <b>{@code GET /api/saldo/koreksi}</b> — daftar mutasi koreksi
 * saldo (PRD §9.2; issue <b>#213</b>) di atas PostgreSQL nyata.
 *
 * <p><b>Kenapa IT.</b> Pelajaran #198/#199: bug query (tipe parameter, dialect,
 * batas hari) tidak muncul saat service di-mock. Uji ini menutup semua filter
 * yang dijanjikan issue (#213 acceptance criteria) plus paginasi, isolasi tenant,
 * penyaringan jenis {@code KOREKSI}, dan perhitungan {@code saldoSebelum}.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class DaftarKoreksiSaldoIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;

    private static final LocalDate TGL_15 = LocalDate.parse("2026-10-15");
    private static final LocalDate TGL_16 = LocalDate.parse("2026-10-16");

    @Autowired
    private SaldoOperasiService operasi;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void siapkan() {
        TenantContext.clear();
        jdbc.execute("TRUNCATE TABLE saldo_ledger, saldo_cache, kartu_tamu CASCADE");

        kartu(200L, SEKOLAH, "KT-010", "Bu Rina");
        kartu(201L, SEKOLAH, "KT-011", null);        // label kosong → fallback nomor kartu
        kartu(202L, SEKOLAH_LAIN, "KT-012", "Bapak Lain");

        // Bukan koreksi — TIDAK boleh muncul di endpoint ini.
        ledger(1L, SEKOLAH, "SISWA", 5001L, "KREDIT", "TOPUP_TUNAI", 50_000L, 50_000L,
                "2026-10-15T08:00:00+07:00", null);
        // Koreksi DEBIT 15 Okt 23:45 — masih tanggal 15 menurut zona sekolah
        ledger(2L, SEKOLAH, "SISWA", 5001L, "DEBIT", "KOREKSI", 10_000L, 40_000L,
                "2026-10-15T23:45:00+07:00", "salah input topup");
        // Koreksi KREDIT 16 Okt 00:15 — sudah masuk tanggal 16
        ledger(3L, SEKOLAH, "KARTU_TAMU", 200L, "KREDIT", "KOREKSI", 25_000L, 25_000L,
                "2026-10-16T00:15:00+07:00", "pengembalian kelebihan");
        // Koreksi DEBIT 16 Okt 10:00 (kartu tanpa label)
        ledger(4L, SEKOLAH, "KARTU_TAMU", 201L, "DEBIT", "KOREKSI", 5_000L, 20_000L,
                "2026-10-16T10:00:00+07:00", "koreksi audit");
        // Sekolah lain — tidak boleh bocor
        ledger(5L, SEKOLAH_LAIN, "KARTU_TAMU", 202L, "KREDIT", "KOREKSI", 15_000L, 15_000L,
                "2026-10-16T11:00:00+07:00", "koreksi sekolah lain");
    }

    // ────────────────────────────────────────────────────────────────
    // Semua filter (acceptance criteria #213)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("tanpa filter → hanya jenis KOREKSI milik tenant, urut waktu DESC")
    void tanpaFilter() {
        HalamanResponse<MutasiKoreksiItem> hasil =
                operasi.daftarKoreksi(SEKOLAH, null, null, null, 0, 20);

        // id=1 (TOPUP_TUNAI) & id=6 tidak ada → hanya 3 koreksi tenant ini.
        assertThat(hasil.getItems()).extracting(MutasiKoreksiItem::getId)
                .containsExactly(4L, 3L, 2L);
        assertThat(hasil.getTotal()).isEqualTo(3L);
    }

    @Test
    @DisplayName("filter tanggal → batas hari mengikuti zona sekolah (23:45 vs 00:15)")
    void filterTanggal() {
        HalamanResponse<MutasiKoreksiItem> tgl16 =
                operasi.daftarKoreksi(SEKOLAH, TGL_16, null, null, 0, 20);
        HalamanResponse<MutasiKoreksiItem> tgl15 =
                operasi.daftarKoreksi(SEKOLAH, TGL_15, null, null, 0, 20);

        assertThat(tgl16.getItems()).extracting(MutasiKoreksiItem::getId).containsExactly(4L, 3L);
        assertThat(tgl15.getItems()).extracting(MutasiKoreksiItem::getId).containsExactly(2L);
    }

    @Test
    @DisplayName("filter subjekTipe → SISWA / KARTU_TAMU")
    void filterSubjekTipe() {
        HalamanResponse<MutasiKoreksiItem> siswa =
                operasi.daftarKoreksi(SEKOLAH, null, SubjekTipe.SISWA, null, 0, 20);
        HalamanResponse<MutasiKoreksiItem> kartu =
                operasi.daftarKoreksi(SEKOLAH, null, SubjekTipe.KARTU_TAMU, null, 0, 20);

        assertThat(siswa.getItems()).extracting(MutasiKoreksiItem::getId).containsExactly(2L);
        assertThat(kartu.getItems()).extracting(MutasiKoreksiItem::getId).containsExactly(4L, 3L);
    }

    @Test
    @DisplayName("filter subjekTipe + subjekId → satu subjek tertentu")
    void filterSubjekId() {
        HalamanResponse<MutasiKoreksiItem> hasil =
                operasi.daftarKoreksi(SEKOLAH, null, SubjekTipe.KARTU_TAMU, 200L, 0, 20);

        assertThat(hasil.getItems()).extracting(MutasiKoreksiItem::getId).containsExactly(3L);
    }

    @Test
    @DisplayName("filter kombinasi (tanggal + subjekTipe) → terpenuhi keduanya")
    void filterKombinasi() {
        HalamanResponse<MutasiKoreksiItem> hasil =
                operasi.daftarKoreksi(SEKOLAH, TGL_16, SubjekTipe.KARTU_TAMU, null, 0, 20);

        assertThat(hasil.getItems()).extracting(MutasiKoreksiItem::getId).containsExactly(4L, 3L);
    }

    @Test
    @DisplayName("filter tanpa hasil → halaman kosong (bukan 500)")
    void filterTanpaHasil() {
        HalamanResponse<MutasiKoreksiItem> hasil =
                operasi.daftarKoreksi(SEKOLAH, TGL_15, SubjekTipe.KARTU_TAMU, null, 0, 20);

        assertThat(hasil.getItems()).isEmpty();
        assertThat(hasil.getTotal()).isZero();
    }

    @Test
    @DisplayName("tenant-scoped: koreksi sekolah lain tidak bocor")
    void tenantScoped() {
        HalamanResponse<MutasiKoreksiItem> hasil =
                operasi.daftarKoreksi(SEKOLAH_LAIN, null, null, null, 0, 20);

        assertThat(hasil.getItems()).extracting(MutasiKoreksiItem::getId).containsExactly(5L);
        assertThat(hasil.getItems()).extracting(MutasiKoreksiItem::getSubjekNama)
                .containsExactly("Bapak Lain");
    }

    @Test
    @DisplayName("paginasi: halaman & ukuran dihormati")
    void paginasi() {
        HalamanResponse<MutasiKoreksiItem> hal0 = operasi.daftarKoreksi(SEKOLAH, null, null, null, 0, 2);
        HalamanResponse<MutasiKoreksiItem> hal1 = operasi.daftarKoreksi(SEKOLAH, null, null, null, 1, 2);

        assertThat(hal0.getItems()).extracting(MutasiKoreksiItem::getId).containsExactly(4L, 3L);
        assertThat(hal1.getItems()).extracting(MutasiKoreksiItem::getId).containsExactly(2L);
        assertThat(hal0.getTotal()).isEqualTo(3L);
        assertThat(hal0.getTotalHalaman()).isEqualTo(2);
    }

    @Test
    @DisplayName("ukuran tidak wajar dibatasi (bukan error)")
    void ukuranDibatasi() {
        assertThat(operasi.daftarKoreksi(SEKOLAH, null, null, null, 0, 0).getUkuran()).isEqualTo(20);
        assertThat(operasi.daftarKoreksi(SEKOLAH, null, null, null, 0, 99_999).getUkuran())
                .isEqualTo(20);
        assertThat(operasi.daftarKoreksi(SEKOLAH, null, null, null, -3, 20).getHalaman()).isZero();
    }

    // ────────────────────────────────────────────────────────────────
    // Isi respons (yang dibutuhkan UI koreksi bendahara)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("saldoSebelum dihitung dari arah: KREDIT turun, DEBIT naik")
    void saldoSebelumDihitung() {
        HalamanResponse<MutasiKoreksiItem> hasil =
                operasi.daftarKoreksi(SEKOLAH, null, null, null, 0, 20);

        // id=2: DEBIT 10.000, saldoSetelah 40.000 → sebelum 50.000
        assertThat(hasil.getItems()).filteredOn(m -> m.getId().equals(2L)).singleElement()
                .satisfies(m -> {
                    assertThat(m.getArah()).isEqualTo(ArahMutasi.DEBIT);
                    assertThat(m.getNominal()).isEqualTo(10_000L);
                    assertThat(m.getSaldoSebelum()).isEqualTo(50_000L);
                    assertThat(m.getSaldoSetelah()).isEqualTo(40_000L);
                });

        // id=3: KREDIT 25.000, saldoSetelah 25.000 → sebelum 0
        assertThat(hasil.getItems()).filteredOn(m -> m.getId().equals(3L)).singleElement()
                .satisfies(m -> {
                    assertThat(m.getArah()).isEqualTo(ArahMutasi.KREDIT);
                    assertThat(m.getSaldoSebelum()).isZero();
                    assertThat(m.getSaldoSetelah()).isEqualTo(25_000L);
                });
    }

    @Test
    @DisplayName("nama subjek kartu tamu terisi (batch); SISWA null")
    void namaSubjek() {
        HalamanResponse<MutasiKoreksiItem> hasil =
                operasi.daftarKoreksi(SEKOLAH, null, null, null, 0, 20);

        assertThat(hasil.getItems()).filteredOn(m -> m.getId().equals(3L)).singleElement()
                .satisfies(m -> {
                    assertThat(m.getSubjekTipe()).isEqualTo(SubjekTipe.KARTU_TAMU);
                    assertThat(m.getSubjekNama()).isEqualTo("Bu Rina");
                });

        // kartu tanpa label → fallback nomor kartu
        assertThat(hasil.getItems()).filteredOn(m -> m.getId().equals(4L)).singleElement()
                .satisfies(m -> assertThat(m.getSubjekNama()).isEqualTo("KT-011"));

        // SISWA: nama tidak ada di kantin-be (master siswa milik admin-be)
        assertThat(hasil.getItems()).filteredOn(m -> m.getId().equals(2L)).singleElement()
                .satisfies(m -> {
                    assertThat(m.getSubjekTipe()).isEqualTo(SubjekTipe.SISWA);
                    assertThat(m.getSubjekNama()).isNull();
                });
    }

    @Test
    @DisplayName("jejak audit terbawa: keterangan, referensi, aktor, transaksi")
    void jejakAudit() {
        HalamanResponse<MutasiKoreksiItem> hasil =
                operasi.daftarKoreksi(SEKOLAH, null, null, null, 0, 20);

        assertThat(hasil.getItems()).filteredOn(m -> m.getId().equals(2L)).singleElement()
                .satisfies(m -> {
                    assertThat(m.getKeterangan()).isEqualTo("salah input topup");
                    assertThat(m.getReferensiId()).isEqualTo("BA-2026-10-15-002");
                    assertThat(m.getAktorId()).isEqualTo(77L);
                    assertThat(m.getWaktu()).isNotNull();
                });
    }

    // ────────────────────────────────────────────────────────────────
    // Helper seed
    // ────────────────────────────────────────────────────────────────

    private void kartu(long id, long sekolah, String nomor, String labelPemegang) {
        jdbc.update("""
                INSERT INTO kartu_tamu (id, sekolah_id, nomor_kartu, label_pemegang, dibuat_oleh)
                VALUES (?, ?, ?, ?, 42)
                """, id, sekolah, nomor, labelPemegang);
    }

    private void ledger(long id, long sekolah, String subjekTipe, long subjekId, String arah,
                        String jenis, long nominal, long saldoSetelah, String waktu,
                        String keterangan) {
        jdbc.update("""
                INSERT INTO saldo_ledger (id, sekolah_id, subjek_tipe, subjek_id, arah, jenis,
                                          nominal, saldo_setelah, referensi_id, keterangan,
                                          aktor_id, waktu)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 77, ?::timestamptz)
                """, id, sekolah, subjekTipe, subjekId, arah, jenis,
                nominal, saldoSetelah, "BA-2026-10-15-00" + id, keterangan, waktu);
    }
}
