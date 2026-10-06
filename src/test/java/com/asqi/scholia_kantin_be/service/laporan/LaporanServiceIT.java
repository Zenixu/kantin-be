package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.BarisKerugianStok;
import com.asqi.scholia_kantin_be.dto.BarisPenjualan;
import com.asqi.scholia_kantin_be.dto.BarisStok;
import com.asqi.scholia_kantin_be.dto.RingkasanPenjualan;
import com.asqi.scholia_kantin_be.dto.RingkasanRekonsiliasi;
import com.asqi.scholia_kantin_be.dto.RingkasanSaldoMengendap;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;
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

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE transaksi_item, transaksi, sesi_kasir, titik_kasir, "
                + "saldo_ledger, saldo_cache, mutasi_stok, stok_cache, kategori_menu, menu CASCADE");
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
}
