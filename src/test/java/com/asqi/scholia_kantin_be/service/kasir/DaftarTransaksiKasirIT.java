package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.dto.HalamanResponse;
import com.asqi.scholia_kantin_be.dto.TransaksiKasirItem;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
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
 * Uji integrasi <b>{@code GET /api/kasir/transaksi}</b> — daftar transaksi untuk
 * koreksi bendahara (PRD §6.3, §9.2; issue <b>#212</b>) di atas PostgreSQL nyata.
 *
 * <p><b>Kenapa IT, bukan uji controller ber-mock.</b> Pelajaran #198/#199: bug
 * query (tipe parameter, dialect, batas hari) <b>tidak</b> muncul saat service
 * di-mock — SQL-nya tak pernah dieksekusi. Uji ini menutup <b>semua</b> filter
 * yang dijanjikan issue (#212 acceptance criteria) plus paginasi, isolasi tenant,
 * dan resolusi nama.
 *
 * <p><b>Batas hari</b> diuji eksplisit (23:45 vs 00:15) karena filter
 * {@code tanggal} harus mengikuti <b>zona waktu sekolah</b> ({@code JamKantin}),
 * bukan zona server.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class DaftarTransaksiKasirIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;

    private static final long TITIK_UTAMA = 1L;
    private static final long TITIK_BELAKANG = 2L;
    private static final long TITIK_LAIN = 3L;

    private static final long SESI_15 = 11L;   // sekolah 1, titik utama, 15 Okt
    private static final long SESI_16 = 12L;   // sekolah 1, titik utama, 16 Okt
    private static final long SESI_16B = 13L;  // sekolah 1, titik belakang, 16 Okt
    private static final long SESI_LAIN = 14L; // sekolah lain

    private static final LocalDate TGL_15 = LocalDate.parse("2026-10-15");
    private static final LocalDate TGL_16 = LocalDate.parse("2026-10-16");

    @Autowired
    private TransaksiKasirService service;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void siapkan() {
        TenantContext.clear();
        jdbc.execute("TRUNCATE TABLE transaksi, sesi_kasir, titik_kasir, kartu_tamu CASCADE");

        titik(TITIK_UTAMA, SEKOLAH, "Kasir Utama");
        titik(TITIK_BELAKANG, SEKOLAH, "Kasir Belakang");
        titik(TITIK_LAIN, SEKOLAH_LAIN, "Kasir Sekolah Lain");

        sesi(SESI_15, SEKOLAH, TITIK_UTAMA, "2026-10-15");
        sesi(SESI_16, SEKOLAH, TITIK_UTAMA, "2026-10-16");
        sesi(SESI_16B, SEKOLAH, TITIK_BELAKANG, "2026-10-16");
        sesi(SESI_LAIN, SEKOLAH_LAIN, TITIK_LAIN, "2026-10-16");

        kartu(100L, SEKOLAH, "KT-001", "Bu Sri");
        kartu(101L, SEKOLAH, "KT-002", null);          // label kosong → fallback nomor kartu
        kartu(102L, SEKOLAH_LAIN, "KT-003", "Bapak Lain");

        // id=1: 15 Okt 08:00 (SISWA) — sekolah 1
        trx(1L, SEKOLAH, SESI_15, TITIK_UTAMA, "SISWA", 5001L, 42L, 15000L,
                "SUKSES", "2026-10-15T08:00:00+07:00");
        // id=2: 15 Okt 23:45 (KARTU_TAMU) — MASIH tanggal 15 menurut zona sekolah
        trx(2L, SEKOLAH, SESI_15, TITIK_UTAMA, "KARTU_TAMU", 100L, 42L, 12000L,
                "SUKSES", "2026-10-15T23:45:00+07:00");
        // id=3: 16 Okt 00:15 (SISWA, VOID) — sudah masuk tanggal 16
        trx(3L, SEKOLAH, SESI_16, TITIK_UTAMA, "SISWA", 5002L, 43L, 20000L,
                "VOID", "2026-10-16T00:15:00+07:00");
        // id=4: 16 Okt 10:00 (KARTU_TAMU, titik belakang)
        trx(4L, SEKOLAH, SESI_16B, TITIK_BELAKANG, "KARTU_TAMU", 101L, 43L, 8000L,
                "SUKSES", "2026-10-16T10:00:00+07:00");
        // id=5: SEKOLAH LAIN — tidak boleh bocor ke tenant SEKOLAH
        trx(5L, SEKOLAH_LAIN, SESI_LAIN, TITIK_LAIN, "KARTU_TAMU", 102L, 44L, 9000L,
                "SUKSES", "2026-10-16T11:00:00+07:00");
    }

    // ────────────────────────────────────────────────────────────────
    // Semua filter (acceptance criteria #212)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("tanpa filter → semua transaksi tenant, urut waktu DESC")
    void tanpaFilter() {
        HalamanResponse<TransaksiKasirItem> hasil = service.daftar(SEKOLAH, null, null, null, 0, 20);

        assertThat(hasil.getItems()).extracting(TransaksiKasirItem::getId)
                .containsExactly(4L, 3L, 2L, 1L);
        assertThat(hasil.getTotal()).isEqualTo(4L);
    }

    @Test
    @DisplayName("filter sesiId → hanya transaksi sesi itu")
    void filterSesiId() {
        HalamanResponse<TransaksiKasirItem> hasil =
                service.daftar(SEKOLAH, SESI_15, null, null, 0, 20);

        assertThat(hasil.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(2L, 1L);
        assertThat(hasil.getTotal()).isEqualTo(2L);
    }

    @Test
    @DisplayName("filter tanggal → batas hari mengikuti zona sekolah (23:45 vs 00:15)")
    void filterTanggal() {
        HalamanResponse<TransaksiKasirItem> tgl16 =
                service.daftar(SEKOLAH, null, TGL_16, null, 0, 20);
        HalamanResponse<TransaksiKasirItem> tgl15 =
                service.daftar(SEKOLAH, null, TGL_15, null, 0, 20);

        // 16 Okt: 00:15 (id=3) & 10:00 (id=4) — 23:45 milik 15 Okt, bukan di sini.
        assertThat(tgl16.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(4L, 3L);
        // 15 Okt: 08:00 (id=1) & 23:45 (id=2).
        assertThat(tgl15.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("filter status → SUKSES / VOID")
    void filterStatus() {
        HalamanResponse<TransaksiKasirItem> void_ =
                service.daftar(SEKOLAH, null, null, StatusTransaksi.VOID, 0, 20);
        HalamanResponse<TransaksiKasirItem> sukses =
                service.daftar(SEKOLAH, null, null, StatusTransaksi.SUKSES, 0, 20);

        assertThat(void_.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(3L);
        assertThat(sukses.getItems()).extracting(TransaksiKasirItem::getId)
                .containsExactly(4L, 2L, 1L);
    }

    @Test
    @DisplayName("filter kombinasi (sesiId + status) → terpenuhi keduanya")
    void filterKombinasi() {
        HalamanResponse<TransaksiKasirItem> hasil =
                service.daftar(SEKOLAH, SESI_15, null, StatusTransaksi.SUKSES, 0, 20);

        assertThat(hasil.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("filter tanpa hasil → halaman kosong (bukan 500)")
    void filterTanpaHasil() {
        HalamanResponse<TransaksiKasirItem> hasil =
                service.daftar(SEKOLAH, SESI_16B, TGL_15, null, 0, 20);

        assertThat(hasil.getItems()).isEmpty();
        assertThat(hasil.getTotal()).isZero();
    }

    @Test
    @DisplayName("tenant-scoped: transaksi sekolah lain tidak bocor")
    void tenantScoped() {
        HalamanResponse<TransaksiKasirItem> hasil =
                service.daftar(SEKOLAH_LAIN, null, null, null, 0, 20);

        assertThat(hasil.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(5L);
        assertThat(hasil.getItems()).extracting(TransaksiKasirItem::getSubjekNama)
                .containsExactly("Bapak Lain");
    }

    @Test
    @DisplayName("paginasi: halaman & ukuran dihormati")
    void paginasi() {
        HalamanResponse<TransaksiKasirItem> hal0 = service.daftar(SEKOLAH, null, null, null, 0, 2);
        HalamanResponse<TransaksiKasirItem> hal1 = service.daftar(SEKOLAH, null, null, null, 1, 2);

        assertThat(hal0.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(4L, 3L);
        assertThat(hal1.getItems()).extracting(TransaksiKasirItem::getId).containsExactly(2L, 1L);
        assertThat(hal0.getTotal()).isEqualTo(4L);
        assertThat(hal0.getTotalHalaman()).isEqualTo(2);
        assertThat(hal0.getHalaman()).isZero();
        assertThat(hal0.getUkuran()).isEqualTo(2);
    }

    @Test
    @DisplayName("ukuran tidak wajar dibatasi (bukan error)")
    void ukuranDibatasi() {
        HalamanResponse<TransaksiKasirItem> nol = service.daftar(SEKOLAH, null, null, null, 0, 0);
        HalamanResponse<TransaksiKasirItem> raksasa =
                service.daftar(SEKOLAH, null, null, null, 0, 99_999);
        HalamanResponse<TransaksiKasirItem> halamanNegatif =
                service.daftar(SEKOLAH, null, null, null, -3, 20);

        assertThat(nol.getUkuran()).isEqualTo(20);      // ukuran<=0 → default
        assertThat(raksasa.getUkuran()).isEqualTo(20);  // >UKURAN_MAKS → default
        assertThat(halamanNegatif.getHalaman()).isZero();
    }

    // ────────────────────────────────────────────────────────────────
    // Isi respons (yang dibutuhkan UI koreksi bendahara)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("nama titik kasir & subjek kartu tamu terisi (batch, bukan N+1)")
    void namaTerisi() {
        HalamanResponse<TransaksiKasirItem> hasil = service.daftar(SEKOLAH, null, null, null, 0, 20);

        assertThat(hasil.getItems())
                .filteredOn(t -> t.getId().equals(4L))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getTitikKasirNama()).isEqualTo("Kasir Belakang");
                    assertThat(t.getSubjekTipe()).isEqualTo(SubjekTipe.KARTU_TAMU);
                    assertThat(t.getSubjekNama()).isEqualTo("KT-002"); // label kosong → nomor kartu
                });

        assertThat(hasil.getItems())
                .filteredOn(t -> t.getId().equals(2L))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getTitikKasirNama()).isEqualTo("Kasir Utama");
                    assertThat(t.getSubjekNama()).isEqualTo("Bu Sri");
                });

        // SISWA: nama tidak ada di kantin-be (data siswa milik admin-be) → null.
        assertThat(hasil.getItems())
                .filteredOn(t -> t.getId().equals(1L))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getSubjekTipe()).isEqualTo(SubjekTipe.SISWA);
                    assertThat(t.getSubjekNama()).isNull();
                });
    }

    @Test
    @DisplayName("VOID: dapatDikoreksi=false + alasan/waktu koreksi terbawa")
    void voidTidakDapatDikoreksi() {
        HalamanResponse<TransaksiKasirItem> hasil =
                service.daftar(SEKOLAH, null, null, StatusTransaksi.VOID, 0, 20);

        assertThat(hasil.getItems()).singleElement().satisfies(t -> {
            assertThat(t.getStatus()).isEqualTo(StatusTransaksi.VOID);
            assertThat(t.isDapatDikoreksi()).isFalse();
            assertThat(t.getAlasanVoid()).isEqualTo("salah input uji");
            assertThat(t.getVoidAt()).isNotNull();
        });

        assertThat(service.daftar(SEKOLAH, null, null, StatusTransaksi.SUKSES, 0, 20).getItems())
                .allSatisfy(t -> assertThat(t.isDapatDikoreksi()).isTrue());
    }

    // ────────────────────────────────────────────────────────────────
    // Helper seed
    // ────────────────────────────────────────────────────────────────

    private void titik(long id, long sekolah, String nama) {
        jdbc.update("""
                INSERT INTO titik_kasir (id, sekolah_id, nama)
                VALUES (?, ?, ?)
                """, id, sekolah, nama);
    }

    private void sesi(long id, long sekolah, long titikId, String tanggal) {
        jdbc.update("""
                INSERT INTO sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status)
                VALUES (?, ?, ?, ?::date, 'TERBUKA')
                """, id, sekolah, titikId, tanggal);
    }

    private void kartu(long id, long sekolah, String nomor, String labelPemegang) {
        jdbc.update("""
                INSERT INTO kartu_tamu (id, sekolah_id, nomor_kartu, label_pemegang, dibuat_oleh)
                VALUES (?, ?, ?, ?, 42)
                """, id, sekolah, nomor, labelPemegang);
    }

    private void trx(long id, long sekolah, long sesiId, long titikId, String subjekTipe,
                     long subjekId, long petugasId, long total, String status, String waktu) {
        boolean diVoid = "VOID".equals(status);
        jdbc.update("""
                INSERT INTO transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, titik_kasir_id,
                                       subjek_tipe, subjek_id, petugas_id, total, total_hpp, status,
                                       alasan_void, void_at, void_oleh, waktu)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?,
                        ?, ?::timestamptz, ?, ?::timestamptz)
                """, id, "seed-212-" + id, sekolah, sesiId, titikId,
                subjekTipe, subjekId, petugasId, total, status,
                diVoid ? "salah input uji" : null,
                diVoid ? waktu : null,
                diVoid ? petugasId : null,
                waktu);
    }
}
