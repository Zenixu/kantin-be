package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.BarisKartuTamu;
import com.asqi.scholia_kantin_be.dto.BarisKerugianStok;
import com.asqi.scholia_kantin_be.dto.BarisPembatalanKasir;
import com.asqi.scholia_kantin_be.dto.BarisPenjualanDimensi;
import com.asqi.scholia_kantin_be.dto.BarisPenjualan;
import com.asqi.scholia_kantin_be.dto.BarisStok;
import com.asqi.scholia_kantin_be.dto.KartuStokItem;
import com.asqi.scholia_kantin_be.dto.LaporanPerSiswa;
import com.asqi.scholia_kantin_be.dto.RekapSetoranTuItem;
import com.asqi.scholia_kantin_be.dto.RingkasanPenjualan;
import com.asqi.scholia_kantin_be.dto.RingkasanRekonsiliasi;
import com.asqi.scholia_kantin_be.dto.RingkasanSaldoMengendap;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.helper.JamKantin;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji integrasi modul laporan &amp; ekspor Excel (PRD §9.5, issue #41) dengan
 * PostgreSQL nyata (Testcontainers).
 *
 * <p>Data disemai lewat ledger nyata (saldo &amp; stok) dan JDBC (transaksi),
 * lalu diverifikasi: agregat penjualan/laba kotor, saldo mengendap, kerugian
 * stok, dan <b>invariant rekonsiliasi seimbang</b> (Σ KREDIT − Σ DEBIT == saldo
 * mengendap). Ekspor diperiksa menghasilkan berkas .xlsx yang valid (bisa dibaca
 * ulang Apache POI).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class LaporanServiceIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;
    private static final long SISWA = 100L;
    private static final long SISWA2 = 200L;
    private static final long TAMU = 900L;
    private static final long MENU_A = 10L;
    private static final long MENU_B = 20L;
    private static final long KATEGORI = 500L;

    @Autowired
    private LaporanService laporan;

    @Autowired
    private LaporanExportService exportService;

    @Autowired
    private LedgerSaldoService ledgerSaldo;

    @Autowired
    private LedgerStokService ledgerStok;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JamKantin jam;

    @Autowired
    private com.asqi.scholia_kantin_be.service.saldo.SetoranTuService setoranService;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE transaksi_item, transaksi, sesi_kasir, titik_kasir, "
                + "saldo_ledger, saldo_cache, mutasi_stok, stok_cache, kategori_menu, menu, "
                + "kartu_tamu, setoran_tu, audit_log CASCADE");
        jdbc.update("INSERT INTO titik_kasir (id, sekolah_id, nama, is_active, created_at, updated_at) "
                + "VALUES (1, ?, 'Kasir 1', true, now(), now())", SEKOLAH);
        jdbc.update("INSERT INTO sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status, "
                + "total_bruto, total_void, total_bersih, dibuka_at, auto_tutup, posting_buku_kas, "
                + "created_at, updated_at) "
                + "VALUES (1, ?, 1, CURRENT_DATE, 'TERBUKA', 0, 0, 0, now(), false, false, now(), now())",
                SEKOLAH);
        jdbc.update("INSERT INTO kategori_menu (id, sekolah_id, nama, urutan, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'Makanan', 0, true, now(), now())", KATEGORI, SEKOLAH);
        jdbc.update("INSERT INTO menu (id, sekolah_id, kategori_id, nama, harga_jual, satuan, "
                + "stok_minimum, is_active, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'Nasi Uduk', 8000, 'PCS', 2, true, now(), now())",
                MENU_A, SEKOLAH, KATEGORI);
        jdbc.update("INSERT INTO menu (id, sekolah_id, kategori_id, nama, harga_jual, satuan, "
                + "stok_minimum, is_active, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'Es Jeruk', 5000, 'PCS', 2, true, now(), now())",
                MENU_B, SEKOLAH, KATEGORI);
    }

    private void topup(long subjekId, SubjekTipe tipe, long nominal, JenisMutasiSaldo jenis) {
        tx.executeWithoutResult(s -> ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(tipe).subjekId(subjekId)
                .jenis(jenis).nominal(nominal)
                .idempotencyKey("topup-" + subjekId + "-" + System.nanoTime()).build()));
    }

    private void transaksi(long id, long subjekId, long total, long totalHpp) {
        jdbc.update("INSERT INTO transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, "
                + "titik_kasir_id, subjek_tipe, subjek_id, petugas_id, total, total_hpp, status, "
                + "waktu, created_at, updated_at) "
                + "VALUES (?, ?, ?, 1, 1, 'SISWA', ?, 5, ?, ?, 'SUKSES', now(), now(), now())",
                id, "trx-" + id, SEKOLAH, subjekId, total, totalHpp);
    }

    private void item(long id, long transaksiId, long menuId, String nama, int qty,
                      long harga, long hpp) {
        jdbc.update("INSERT INTO transaksi_item (id, transaksi_id, menu_id, nama_menu, kategori_id, "
                + "harga_jual, qty, hpp_snapshot, subtotal, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())",
                id, transaksiId, menuId, nama, KATEGORI, harga, qty, hpp, harga * qty);
    }

    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ringkasan penjualan — bruto, HPP, laba kotor dari transaksi SUKSES")
    void ringkasanPenjualan() {
        transaksi(1, SISWA, 21_000, 10_000);
        item(1, 1, MENU_A, "Nasi Uduk", 2, 8_000, 4_000);
        item(2, 1, MENU_B, "Es Jeruk", 1, 5_000, 2_000);
        transaksi(2, SISWA, 8_000, 4_000);
        item(3, 2, MENU_A, "Nasi Uduk", 1, 8_000, 4_000);

        RingkasanPenjualan r = laporan.ringkasanPenjualan(SEKOLAH, null, null, null);

        assertThat(r.jumlahTransaksi()).isEqualTo(2);
        assertThat(r.penjualanBruto()).isEqualTo(29_000);
        assertThat(r.totalHpp()).isEqualTo(14_000);
        assertThat(r.labaKotor()).isEqualTo(15_000);
        assertThat(r.jumlahVoid()).isZero();
    }

    @Test
    @DisplayName("penjualan per item & per kategori")
    void penjualanPerItemDanKategori() {
        transaksi(1, SISWA, 21_000, 10_000);
        item(1, 1, MENU_A, "Nasi Uduk", 2, 8_000, 4_000);
        item(2, 1, MENU_B, "Es Jeruk", 1, 5_000, 2_000);

        List<BarisPenjualan> perItem = laporan.penjualanPerItem(SEKOLAH, null, null, null);
        assertThat(perItem).hasSize(2);
        assertThat(perItem.get(0).nama()).isEqualTo("Nasi Uduk"); // terlaris dulu (qty 2)
        assertThat(perItem.get(0).qty()).isEqualTo(2);
        assertThat(perItem.get(0).nilai()).isEqualTo(16_000);

        List<BarisPenjualan> perKategori = laporan.penjualanPerKategori(SEKOLAH, null, null, null);
        assertThat(perKategori).hasSize(1);
        assertThat(perKategori.get(0).kunciId()).isEqualTo(KATEGORI);
        assertThat(perKategori.get(0).nama()).isEqualTo("Makanan");
        assertThat(perKategori.get(0).nilai()).isEqualTo(21_000);
    }

    @Test
    @DisplayName("saldo mengendap — total siswa + Kartu Tamu")
    void saldoMengendap() {
        topup(SISWA, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_TUNAI);
        topup(SISWA2, SubjekTipe.SISWA, 30_000, JenisMutasiSaldo.TOPUP_ONLINE);
        topup(TAMU, SubjekTipe.KARTU_TAMU, 20_000, JenisMutasiSaldo.TOPUP_TUNAI);

        RingkasanSaldoMengendap r = laporan.saldoMengendap(SEKOLAH);

        assertThat(r.saldoSiswa()).isEqualTo(80_000);
        assertThat(r.saldoKartuTamu()).isEqualTo(20_000);
        assertThat(r.total()).isEqualTo(100_000);
        assertThat(r.jumlahSiswa()).isEqualTo(2);
        assertThat(r.jumlahKartuTamu()).isEqualTo(1);
    }

    @Test
    @DisplayName("rekonsiliasi SEIMBANG — Σ KREDIT − Σ DEBIT == saldo mengendap")
    void rekonsiliasiSeimbang() {
        // Top-up + penjualan (debit saldo) → cache & ledger tetap konsisten.
        topup(SISWA, SubjekTipe.SISWA, 100_000, JenisMutasiSaldo.TOPUP_TUNAI);
        topup(SISWA2, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_ONLINE);
        tx.executeWithoutResult(s -> ledgerSaldo.debit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).subjekId(SISWA)
                .jenis(JenisMutasiSaldo.PENJUALAN).nominal(21_000)
                .idempotencyKey("jual-1").build()));

        RingkasanRekonsiliasi r = laporan.rekonsiliasi(SEKOLAH, null, null, null);

        assertThat(r.topupTunai()).isEqualTo(100_000);
        assertThat(r.topupOnline()).isEqualTo(50_000);
        assertThat(r.penjualan()).isEqualTo(21_000);
        assertThat(r.saldoMengendap()).isEqualTo(129_000);
        assertThat(r.selisih()).isZero();
        assertThat(r.seimbang()).isTrue();
    }

    @Test
    @DisplayName("kerugian stok — opname keluar & barang rusak beserta nilainya")
    void kerugianStok() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_A, 20, 4_000, "BM-1", "BM-1", 1L));
        // Opname turun 3 (hilang) & 2 rusak.
        tx.executeWithoutResult(s -> ledgerStok.sesuaikanOpname(SEKOLAH, MENU_A, 17, "opname harian", "OP-1", 1L));
        tx.executeWithoutResult(s -> ledgerStok.sesuaikanOpname(SEKOLAH, MENU_A, 15, "rusak/basi", "OP-2", true, "OPNAME", 1L));

        List<BarisKerugianStok> r = laporan.kerugianStok(SEKOLAH, null, null, null);

        assertThat(r).extracting(BarisKerugianStok::jenis)
                .containsExactlyInAnyOrder("OPNAME_KELUAR", "BARANG_RUSAK");
        BarisKerugianStok opnameKeluar = r.stream()
                .filter(b -> b.jenis().equals("OPNAME_KELUAR")).findFirst().orElseThrow();
        assertThat(opnameKeluar.totalQty()).isEqualTo(3);
        assertThat(opnameKeluar.totalNilai()).isEqualTo(3 * 4_000L);
    }

    @Test
    @DisplayName("laporan stok — stok sekarang, nilai persediaan, penanda menipis")
    void laporanStok() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_A, 10, 4_000, "BM-1", "BM-1", 1L));
        // MENU_B tidak diisi stok → stok 0 (menipis, min 2) tapi tanpa baris cache? pakai masuk 1.
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_B, 1, 2_000, "BM-2", "BM-2", 1L));

        List<BarisStok> semua = laporan.laporanStok(SEKOLAH, false);
        assertThat(semua).hasSize(2);

        BarisStok nasi = semua.stream().filter(b -> b.menuId().equals(MENU_A)).findFirst().orElseThrow();
        assertThat(nasi.stok()).isEqualTo(10);
        assertThat(nasi.nilaiPersediaan()).isEqualTo(40_000);
        assertThat(nasi.menipis()).isFalse();

        BarisStok es = semua.stream().filter(b -> b.menuId().equals(MENU_B)).findFirst().orElseThrow();
        assertThat(es.stok()).isEqualTo(1);
        assertThat(es.menipis()).isTrue(); // 1 <= min 2

        List<BarisStok> menipisSaja = laporan.laporanStok(SEKOLAH, true);
        assertThat(menipisSaja).extracting(BarisStok::menuId).containsExactly(MENU_B);
    }

    @Test
    @DisplayName("laporan stok — alias FE namaMenu & stokBerjalan (issue #98)")
    void laporanStokAliasFe() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_A, 10, 4_000, "BM-1", "BM-1", 1L));

        BarisStok nasi = laporan.laporanStok(SEKOLAH, false).stream()
                .filter(b -> b.menuId().equals(MENU_A)).findFirst().orElseThrow();

        // Alias wajib sama nilainya dengan field asal (kontrak FE Laporan Inventaris).
        assertThat(nasi.namaMenu()).isEqualTo(nasi.nama());
        assertThat(nasi.stokBerjalan()).isEqualTo(nasi.stok());
        assertThat(nasi.stokBerjalan()).isEqualTo(10);
        assertThat(nasi.namaMenu()).isNotBlank();
    }

    @Test
    @DisplayName("ekspor Excel — berkas .xlsx valid & berisi data")
    void eksporExcelValid() throws Exception {
        topup(SISWA, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_TUNAI);
        transaksi(1, SISWA, 8_000, 4_000);
        item(1, 1, MENU_A, "Nasi Uduk", 1, 8_000, 4_000);

        LaporanExportService.HasilEkspor hasil = exportService.ekspor(
                SEKOLAH, JenisLaporan.PENJUALAN, null, null, null);

        assertThat(hasil.namaBerkas()).endsWith(".xlsx");
        assertThat(hasil.isi()).isNotEmpty();

        // Berkas harus benar-benar terbaca sebagai workbook Excel.
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(hasil.isi()))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThan(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("Penjualan");
        }
    }

    @Test
    @DisplayName("tenant scoping — sekolah lain tidak melihat data sekolah ini")
    void tenantScoping() {
        topup(SISWA, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_TUNAI);
        transaksi(1, SISWA, 8_000, 4_000);
        item(1, 1, MENU_A, "Nasi Uduk", 1, 8_000, 4_000);

        assertThat(laporan.saldoMengendap(SEKOLAH_LAIN).total()).isZero();
        assertThat(laporan.ringkasanPenjualan(SEKOLAH_LAIN, null, null, null).penjualanBruto()).isZero();
        assertThat(laporan.kerugianStok(SEKOLAH_LAIN, null, null, null)).isEmpty();
    }

    // ────────────────────────────────────────────────────────────────
    // PEMBATALAN KASIR (PRD §9.5, issue #114)
    // ────────────────────────────────────────────────────────────────

    /** Seed satu transaksi VOID (wajib beralasan, PRD §6.3). */
    private void transaksiVoid(long id, long subjekId, long total, String alasan) {
        jdbc.update("INSERT INTO transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, "
                + "titik_kasir_id, subjek_tipe, subjek_id, petugas_id, total, total_hpp, status, "
                + "alasan_void, void_at, void_oleh, waktu, created_at, updated_at) "
                + "VALUES (?, ?, ?, 1, 1, 'SISWA', ?, 5, ?, 0, 'VOID', ?, now(), 777, now(), now(), now())",
                id, "trx-" + id, SEKOLAH, subjekId, total, alasan);
    }

    @Test
    @DisplayName("#114: pembatalan kasir — hanya transaksi VOID, alasan & petugas ikut")
    void pembatalanKasir() {
        transaksi(1, SISWA, 8_000, 4_000); // SUKSES → tidak masuk laporan
        transaksiVoid(2, SISWA, 16_000, "Kartu dipakai bukan pemiliknya");
        transaksiVoid(3, SISWA2, 8_000, "salah input");

        List<BarisPembatalanKasir> r = laporan.pembatalanKasir(SEKOLAH, null, null, null);

        assertThat(r).hasSize(2);
        assertThat(r).extracting(BarisPembatalanKasir::getTransaksiId)
                .containsExactlyInAnyOrder(2L, 3L);
        BarisPembatalanKasir b = r.stream()
                .filter(x -> x.getTransaksiId().equals(2L)).findFirst().orElseThrow();
        assertThat(b.getSubjekTipe()).isEqualTo(SubjekTipe.SISWA);
        assertThat(b.getSubjekId()).isEqualTo(SISWA);
        assertThat(b.getAlasanVoid()).isEqualTo("Kartu dipakai bukan pemiliknya");
        assertThat(b.getVoidOleh()).isEqualTo(777L);
        assertThat(b.getVoidAt()).isNotNull();
    }

    @Test
    @DisplayName("#114: pembatalan kasir — tenant scoping (sekolah lain kosong)")
    void pembatalanKasirTenantScoping() {
        transaksiVoid(2, SISWA, 16_000, "Kartu dipakai bukan pemiliknya");

        assertThat(laporan.pembatalanKasir(SEKOLAH_LAIN, null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("#114: ekspor PEMBATALAN menghasilkan .xlsx valid")
    void eksporPembatalan() throws Exception {
        transaksiVoid(2, SISWA, 16_000, "Kartu dipakai bukan pemiliknya");

        LaporanExportService.HasilEkspor hasil = exportService.ekspor(
                SEKOLAH, JenisLaporan.PEMBATALAN, null, null, null);

        assertThat(hasil.namaBerkas()).endsWith(".xlsx");
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(hasil.isi()))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThan(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("Pembatalan");
        }
    }

    // ────────────────────────────────────────────────────────────────
    // KARTU TAMU (PRD §9.5, issue #115)
    // ────────────────────────────────────────────────────────────────

    private void kartuTamu(long id, String nomor, String pemegang, boolean aktif) {
        jdbc.update("INSERT INTO kartu_tamu (id, sekolah_id, nomor_kartu, rfid_uid, aktif, "
                + "label_pemegang, dibuat_oleh, dibuat_pada) "
                + "VALUES (?, ?, ?, ?, ?, ?, 1, now())",
                id, SEKOLAH, nomor, "UID-" + id, aktif, pemegang);
    }

    @Test
    @DisplayName("#115: laporan kartu tamu — daftar kartu + pemegang + saldo + status")
    void laporanKartuTamu() {
        kartuTamu(500, "KT-001", "Bu Sari", true);
        kartuTamu(501, "KT-002", "Tamu", false);
        topup(500, SubjekTipe.KARTU_TAMU, 30_000, JenisMutasiSaldo.TOPUP_TUNAI);

        List<BarisKartuTamu> r = laporan.laporanKartuTamu(SEKOLAH);

        assertThat(r).hasSize(2);
        BarisKartuTamu k1 = r.stream().filter(x -> x.getKartuId().equals(500L)).findFirst().orElseThrow();
        assertThat(k1.getNomorKartu()).isEqualTo("KT-001");
        assertThat(k1.getLabelPemegang()).isEqualTo("Bu Sari");
        assertThat(k1.getSaldo()).isEqualTo(30_000L);
        assertThat(k1.isAktif()).isTrue();

        BarisKartuTamu k2 = r.stream().filter(x -> x.getKartuId().equals(501L)).findFirst().orElseThrow();
        assertThat(k2.getSaldo()).isZero();
        assertThat(k2.isAktif()).isFalse();
    }

    @Test
    @DisplayName("#115: laporan kartu tamu — tenant scoping (sekolah lain kosong)")
    void laporanKartuTamuTenantScoping() {
        kartuTamu(500, "KT-001", "Bu Sari", true);

        assertThat(laporan.laporanKartuTamu(SEKOLAH_LAIN)).isEmpty();
    }

    @Test
    @DisplayName("#115: ekspor KARTU_TAMU menghasilkan .xlsx valid")
    void eksporKartuTamu() throws Exception {
        kartuTamu(500, "KT-001", "Bu Sari", true);
        topup(500, SubjekTipe.KARTU_TAMU, 30_000, JenisMutasiSaldo.TOPUP_TUNAI);

        LaporanExportService.HasilEkspor hasil = exportService.ekspor(
                SEKOLAH, JenisLaporan.KARTU_TAMU, null, null, null);

        assertThat(hasil.namaBerkas()).endsWith(".xlsx");
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(hasil.isi()))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThan(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("Kartu Tamu");
        }
    }

    // ────────────────────────────────────────────────────────────────
    // PENJUALAN PER TITIK & PETUGAS (PRD §9.5, issue #116)
    // ────────────────────────────────────────────────────────────────

    private void titikKasir(long id, String nama) {
        jdbc.update("INSERT INTO titik_kasir (id, sekolah_id, nama, is_active, created_at, updated_at) "
                + "VALUES (?, ?, ?, true, now(), now())", id, SEKOLAH, nama);
    }

    private void transaksiDimensi(long id, long titikId, long petugasId, long total, long totalHpp) {
        jdbc.update("INSERT INTO transaksi (id, idempotency_key, sekolah_id, sesi_kasir_id, "
                + "titik_kasir_id, subjek_tipe, subjek_id, petugas_id, total, total_hpp, status, "
                + "waktu, created_at, updated_at) "
                + "VALUES (?, ?, ?, 1, ?, 'SISWA', 1, ?, ?, ?, 'SUKSES', now(), now(), now())",
                id, "trx-" + id, SEKOLAH, titikId, petugasId, total, totalHpp);
    }

    @Test
    @DisplayName("#116: penjualan per titik kasir — grup + nama + laba kotor")
    void penjualanPerTitik() {
        titikKasir(2, "Kasir 2");
        transaksiDimensi(1, 1, 5, 21_000, 10_000);
        transaksiDimensi(2, 1, 5, 8_000, 4_000);
        transaksiDimensi(3, 2, 6, 15_000, 6_000);

        List<BarisPenjualanDimensi> r = laporan.penjualanPerTitik(SEKOLAH, null, null, null);

        assertThat(r).hasSize(2);
        BarisPenjualanDimensi t1 = r.stream().filter(x -> x.kunciId().equals(1L)).findFirst().orElseThrow();
        assertThat(t1.nama()).isEqualTo("Kasir 1");
        assertThat(t1.jumlahTransaksi()).isEqualTo(2);
        assertThat(t1.nilai()).isEqualTo(29_000);
        assertThat(t1.hpp()).isEqualTo(14_000);
        assertThat(t1.labaKotor()).isEqualTo(15_000);

        BarisPenjualanDimensi t2 = r.stream().filter(x -> x.kunciId().equals(2L)).findFirst().orElseThrow();
        assertThat(t2.nama()).isEqualTo("Kasir 2");
        assertThat(t2.nilai()).isEqualTo(15_000);
    }

    @Test
    @DisplayName("#116: penjualan per petugas — grup + laba kotor")
    void penjualanPerPetugas() {
        titikKasir(2, "Kasir 2");
        transaksiDimensi(1, 1, 5, 21_000, 10_000);
        transaksiDimensi(2, 1, 5, 8_000, 4_000);
        transaksiDimensi(3, 2, 6, 15_000, 6_000);

        List<BarisPenjualanDimensi> r = laporan.penjualanPerPetugas(SEKOLAH, null, null, null);

        assertThat(r).hasSize(2);
        BarisPenjualanDimensi p5 = r.stream().filter(x -> x.kunciId().equals(5L)).findFirst().orElseThrow();
        assertThat(p5.jumlahTransaksi()).isEqualTo(2);
        assertThat(p5.nilai()).isEqualTo(29_000);
        assertThat(p5.labaKotor()).isEqualTo(15_000);

        BarisPenjualanDimensi p6 = r.stream().filter(x -> x.kunciId().equals(6L)).findFirst().orElseThrow();
        assertThat(p6.jumlahTransaksi()).isEqualTo(1);
        assertThat(p6.nilai()).isEqualTo(15_000);
    }

    @Test
    @DisplayName("#116: penjualan per titik — transaksi VOID tidak dihitung")
    void penjualanPerTitikAbaikanVoid() {
        transaksiDimensi(1, 1, 5, 21_000, 10_000);
        transaksiVoid(2, SISWA, 8_000, "Kartu dipakai bukan pemiliknya");

        List<BarisPenjualanDimensi> r = laporan.penjualanPerTitik(SEKOLAH, null, null, null);

        assertThat(r).hasSize(1);
        assertThat(r.get(0).jumlahTransaksi()).isEqualTo(1);
        assertThat(r.get(0).nilai()).isEqualTo(21_000);
    }

    @Test
    @DisplayName("#116: penjualan per titik — tenant scoping (sekolah lain kosong)")
    void penjualanPerTitikTenantScoping() {
        transaksiDimensi(1, 1, 5, 21_000, 10_000);

        assertThat(laporan.penjualanPerTitik(SEKOLAH_LAIN, null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("#116: ekspor PENJUALAN_TITIK & PENJUALAN_PETUGAS menghasilkan .xlsx valid")
    void eksporPenjualanDimensi() throws Exception {
        titikKasir(2, "Kasir 2");
        transaksiDimensi(1, 1, 5, 21_000, 10_000);
        transaksiDimensi(3, 2, 6, 15_000, 6_000);

        for (JenisLaporan jenis : List.of(JenisLaporan.PENJUALAN_TITIK, JenisLaporan.PENJUALAN_PETUGAS)) {
            LaporanExportService.HasilEkspor hasil = exportService.ekspor(SEKOLAH, jenis, null, null, null);
            assertThat(hasil.namaBerkas()).endsWith(".xlsx");
            try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                    new java.io.ByteArrayInputStream(hasil.isi()))) {
                var sheet = wb.getSheetAt(0);
                assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThan(0);
                assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("Penjualan per");
            }
        }
    }

    // ────────────────────────────────────────────────────────────────
    // PER SISWA (PRD §9.5, issue #117)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("#117: per siswa — ringkasan + transaksi + mutasi saldo satu subjek")
    void laporanPerSiswa() {
        topup(SISWA, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_TUNAI);
        transaksi(1, SISWA, 21_000, 10_000);
        transaksi(2, SISWA, 8_000, 4_000);
        transaksiVoid(3, SISWA, 16_000, "Kartu dipakai bukan pemiliknya");
        // Subjek lain — harus TIDAK ikut
        transaksi(4, SISWA2, 30_000, 12_000);

        LaporanPerSiswa r = laporan.laporanPerSiswa(SEKOLAH, SubjekTipe.SISWA, SISWA, null, null, null);

        assertThat(r.subjekTipe()).isEqualTo(SubjekTipe.SISWA);
        assertThat(r.subjekId()).isEqualTo(SISWA);
        assertThat(r.saldo()).isEqualTo(50_000L);
        assertThat(r.transaksi()).hasSize(3);
        assertThat(r.ringkasan().jumlahTransaksiSukses()).isEqualTo(2);
        assertThat(r.ringkasan().nilaiBelanjaSukses()).isEqualTo(29_000);
        assertThat(r.ringkasan().totalHpp()).isEqualTo(14_000);
        assertThat(r.ringkasan().jumlahTransaksiVoid()).isEqualTo(1);
        assertThat(r.ringkasan().nilaiVoid()).isEqualTo(16_000);
        assertThat(r.ringkasan().totalTopup()).isEqualTo(50_000);
        assertThat(r.mutasiSaldo()).hasSize(1);
        assertThat(r.mutasiSaldo().get(0).jenis()).isEqualTo(JenisMutasiSaldo.TOPUP_TUNAI);
        assertThat(r.mutasiSaldo().get(0).nominal()).isEqualTo(50_000);
    }

    @Test
    @DisplayName("#117: per siswa — siswa tanpa data → ringkasan nol, daftar kosong")
    void laporanPerSiswaKosong() {
        LaporanPerSiswa r = laporan.laporanPerSiswa(SEKOLAH, SubjekTipe.SISWA, 999L, null, null, null);

        assertThat(r.saldo()).isZero();
        assertThat(r.transaksi()).isEmpty();
        assertThat(r.mutasiSaldo()).isEmpty();
        assertThat(r.ringkasan().jumlahTransaksiSukses()).isZero();
        assertThat(r.ringkasan().totalTopup()).isZero();
    }

    @Test
    @DisplayName("#117: per siswa — tenant scoping (sekolah lain kosong)")
    void laporanPerSiswaTenantScoping() {
        topup(SISWA, SubjekTipe.SISWA, 50_000, JenisMutasiSaldo.TOPUP_TUNAI);
        transaksi(1, SISWA, 21_000, 10_000);

        LaporanPerSiswa r = laporan.laporanPerSiswa(SEKOLAH_LAIN, SubjekTipe.SISWA, SISWA, null, null, null);

        assertThat(r.transaksi()).isEmpty();
        assertThat(r.mutasiSaldo()).isEmpty();
        assertThat(r.saldo()).isZero();
    }

    // ────────────────────────────────────────────────────────────────
    // KARTU STOK PER ITEM (PRD §9.5, issue #118)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("#118: kartu stok per item — riwayat mutasi + saldo berjalan")
    void kartuStokItem() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_A, 20, 4_000, "BM-1", "BM-1", 1L));
        tx.executeWithoutResult(s -> ledgerStok.sesuaikanOpname(SEKOLAH, MENU_A, 17, "opname harian", "OP-1", 1L));

        KartuStokItem kartu = laporan.kartuStokItem(SEKOLAH, MENU_A, 100);

        assertThat(kartu).isNotNull();
        assertThat(kartu.menuId()).isEqualTo(MENU_A);
        assertThat(kartu.namaMenu()).isEqualTo("Nasi Uduk");
        assertThat(kartu.stokSekarang()).isEqualTo(17);
        assertThat(kartu.mutasi()).hasSize(2);
        // terbaru dulu (id DESC)
        assertThat(kartu.mutasi().get(0).jenis()).isEqualTo(JenisMutasiStok.OPNAME_KELUAR);
        assertThat(kartu.mutasi().get(0).stokSetelah()).isEqualTo(17);
        assertThat(kartu.mutasi().get(1).jenis()).isEqualTo(JenisMutasiStok.BARANG_MASUK);
        assertThat(kartu.mutasi().get(1).qty()).isEqualTo(20);
        assertThat(kartu.mutasi().get(1).stokSetelah()).isEqualTo(20);
    }

    @Test
    @DisplayName("#118: kartu stok — menu sekolah lain / tak ada → null (→404)")
    void kartuStokItemTenantScoping() {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_A, 20, 4_000, "BM-1", "BM-1", 1L));

        assertThat(laporan.kartuStokItem(SEKOLAH_LAIN, MENU_A, 100)).isNull();
        assertThat(laporan.kartuStokItem(SEKOLAH, 999_999L, 100)).isNull();
    }

    @Test
    @DisplayName("#118: ekspor KARTU_STOK menghasilkan .xlsx valid")
    void eksporKartuStok() throws Exception {
        tx.executeWithoutResult(s -> ledgerStok.masukBarang(SEKOLAH, MENU_A, 20, 4_000, "BM-1", "BM-1", 1L));

        LaporanExportService.HasilEkspor hasil = exportService.ekspor(
                SEKOLAH, JenisLaporan.KARTU_STOK, null, null, null, MENU_A);

        assertThat(hasil.namaBerkas()).endsWith(".xlsx");
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(hasil.isi()))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThan(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("Kartu Stok");
        }
    }

    // ────────────────────────────────────────────────────────────────
    // SETORAN KAS TU (PRD §9.2 & §9.5, issue #143)
    // ────────────────────────────────────────────────────────────────

    /** Catat top-up tunai oleh petugas tertentu (aktor_id = petugasId). */
    private void topupTunaiPetugas(long petugasId, long nominal) {
        long subjek = 50_000L + Math.abs(System.nanoTime() % 10_000);
        tx.executeWithoutResult(s -> ledgerSaldo.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).subjekId(subjek)
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI).nominal(nominal)
                .referensiTipe("TOPUP").aktorId(petugasId)
                .idempotencyKey("setoran-" + petugasId + "-" + System.nanoTime()).build()));
    }

    @Test
    @DisplayName("#143: laporan setoran TU — rekap per petugas + selisih (belum disetor)")
    void laporanSetoranTu() {
        topupTunaiPetugas(555L, 50_000);
        topupTunaiPetugas(555L, 30_000);
        topupTunaiPetugas(777L, 20_000);

        List<RekapSetoranTuItem> r = laporan.laporanSetoranTu(SEKOLAH, jam.hariIni());

        assertThat(r).hasSize(2);
        RekapSetoranTuItem a = r.stream().filter(x -> x.getPetugasId().equals(555L))
                .findFirst().orElseThrow();
        assertThat(a.getTotalTopup()).isEqualTo(80_000);
        assertThat(a.getJumlahDisetor()).isZero();
        assertThat(a.getSelisih()).isEqualTo(80_000);
        assertThat(a.getTanggal()).isEqualTo(jam.hariIni());
    }

    @Test
    @DisplayName("#143: laporan setoran TU — selisih benar setelah konfirmasi setoran")
    void laporanSetoranTuSetelahKonfirmasi() {
        topupTunaiPetugas(555L, 80_000);
        // Kurang setor Rp5.000 → selisih = 5.000 (dicatat, bukan dihapus).
        setoranService.konfirmasi(SEKOLAH, jam.hariIni(), 555L, 75_000, "BA-143-1",
                "kurang Rp5.000", 999L);

        RekapSetoranTuItem a = laporan.laporanSetoranTu(SEKOLAH, jam.hariIni()).stream()
                .filter(x -> x.getPetugasId().equals(555L)).findFirst().orElseThrow();
        assertThat(a.getJumlahDisetor()).isEqualTo(75_000);
        assertThat(a.getSelisih()).isEqualTo(5_000);
        assertThat(a.getReferensiId()).isEqualTo("BA-143-1");
    }

    @Test
    @DisplayName("#143: laporan setoran TU — tenant scoping (sekolah lain kosong)")
    void laporanSetoranTuTenantScoping() {
        topupTunaiPetugas(555L, 80_000);

        assertThat(laporan.laporanSetoranTu(SEKOLAH_LAIN, jam.hariIni())).isEmpty();
    }

    @Test
    @DisplayName("#143: ekspor SETORAN_TU menghasilkan .xlsx valid + berisi baris rekap")
    void eksporSetoranTu() throws Exception {
        topupTunaiPetugas(555L, 80_000);

        LaporanExportService.HasilEkspor hasil = exportService.ekspor(
                SEKOLAH, JenisLaporan.SETORAN_TU, jam.hariIni(), null, null);

        assertThat(hasil.namaBerkas()).endsWith(".xlsx");
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(hasil.isi()))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThan(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("Setoran");
        }
    }
}
