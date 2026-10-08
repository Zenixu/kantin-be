package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.BarisKartuTamu;
import com.asqi.scholia_kantin_be.dto.BarisKerugianStok;
import com.asqi.scholia_kantin_be.dto.BarisPembatalanKasir;
import com.asqi.scholia_kantin_be.dto.BarisPenjualanDimensi;
import com.asqi.scholia_kantin_be.dto.BarisPenjualan;
import com.asqi.scholia_kantin_be.dto.BarisStok;
import com.asqi.scholia_kantin_be.dto.KartuStokItem;
import com.asqi.scholia_kantin_be.dto.RingkasanPenjualan;
import com.asqi.scholia_kantin_be.dto.RingkasanRekonsiliasi;
import com.asqi.scholia_kantin_be.dto.RingkasanSaldoMengendap;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.MutasiStok;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Menyusun laporan menjadi berkas Excel (PRD §9.5) memakai {@link LaporanService}
 * (agregasi) &amp; {@link ExcelWriter} (tulis .xlsx).
 *
 * <p>Setiap {@link JenisLaporan} dipetakan ke header + baris. Semua tenant-scoped
 * lewat {@link LaporanService}.
 */
@Service
@RequiredArgsConstructor
public class LaporanExportService {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final LaporanService laporan;
    private final ExcelWriter excel;
    private final JamKantin jam;

    /** Hasil ekspor: nama berkas + byte .xlsx. */
    public record HasilEkspor(String namaBerkas, byte[] isi) {
    }

    @Transactional(readOnly = true)
    public HasilEkspor ekspor(Long sekolahId, JenisLaporan jenis, LocalDate tanggal,
                              OffsetDateTime dari, OffsetDateTime sampai) {
        return ekspor(sekolahId, jenis, tanggal, dari, sampai, null);
    }

    /**
     * Ekspor laporan. {@code menuId} hanya dipakai laporan yang butuh satu item
     * (mis. {@link JenisLaporan#KARTU_STOK}); laporan lain mengabaikannya.
     */
    @Transactional(readOnly = true)
    public HasilEkspor ekspor(Long sekolahId, JenisLaporan jenis, LocalDate tanggal,
                              OffsetDateTime dari, OffsetDateTime sampai, Long menuId) {
        String label = labelPeriode(tanggal, dari, sampai);
        return switch (jenis) {
            case PENJUALAN -> eksporPenjualan(sekolahId, tanggal, dari, sampai, label);
            case PENJUALAN_ITEM -> eksporBarisPenjualan(sekolahId, tanggal, dari, sampai, label,
                    "Penjualan per Item", "Item",
                    laporan.penjualanPerItem(sekolahId, tanggal, dari, sampai));
            case PENJUALAN_KATEGORI -> eksporBarisPenjualan(sekolahId, tanggal, dari, sampai, label,
                    "Penjualan per Kategori", "Kategori",
                    laporan.penjualanPerKategori(sekolahId, tanggal, dari, sampai));
            case SALDO_MENGENDAP -> eksporSaldoMengendap(sekolahId, label);
            case REKONSILIASI -> eksporRekonsiliasi(sekolahId, tanggal, dari, sampai, label);
            case STOK -> eksporStok(sekolahId, label);
            case KERUGIAN_STOK -> eksporKerugianStok(sekolahId, tanggal, dari, sampai, label);
            case BARANG_MASUK -> eksporBarangMasuk(sekolahId, tanggal, dari, sampai, label);
            case PEMBATALAN -> eksporPembatalan(sekolahId, tanggal, dari, sampai, label);
            case KARTU_TAMU -> eksporKartuTamu(sekolahId, label);
            case PENJUALAN_TITIK -> eksporPenjualanDimensi(sekolahId, tanggal, dari, sampai, label,
                    "Penjualan per Titik Kasir", "Titik",
                    laporan.penjualanPerTitik(sekolahId, tanggal, dari, sampai));
            case PENJUALAN_PETUGAS -> eksporPenjualanDimensi(sekolahId, tanggal, dari, sampai, label,
                    "Penjualan per Petugas", "Petugas",
                    laporan.penjualanPerPetugas(sekolahId, tanggal, dari, sampai));
            case KARTU_STOK -> eksporKartuStok(sekolahId, menuId, label);
        };
    }

    // ────────────────────────────────────────────────────────────────

    private HasilEkspor eksporPenjualan(Long sekolahId, LocalDate tgl, OffsetDateTime dari,
                                        OffsetDateTime sampai, String label) {
        RingkasanPenjualan r = laporan.ringkasanPenjualan(sekolahId, tgl, dari, sampai);
        List<String> header = List.of("Metrik", "Nilai");
        List<List<Object>> baris = List.of(
                List.of("Jumlah transaksi sukses", r.jumlahTransaksi()),
                List.of("Penjualan bruto", r.penjualanBruto()),
                List.of("Jumlah transaksi void", r.jumlahVoid()),
                List.of("Nilai void", r.nilaiVoid()),
                List.of("Penjualan bersih", r.penjualanBersih()),
                List.of("Total HPP", r.totalHpp()),
                List.of("Laba kotor", r.labaKotor()));
        return berkas("Penjualan & Laba Kotor", label, header, baris);
    }

    private HasilEkspor eksporBarisPenjualan(Long sekolahId, LocalDate tgl, OffsetDateTime dari,
                                             OffsetDateTime sampai, String label, String judul,
                                             String kolomNama, List<BarisPenjualan> data) {
        List<String> header = List.of("ID", kolomNama, "Qty", "Nilai");
        List<List<Object>> baris = new ArrayList<>();
        for (BarisPenjualan b : data) {
            baris.add(List.of(nvl(b.kunciId()), nvl(b.nama()), b.qty(), b.nilai()));
        }
        return berkas(judul, label, header, baris);
    }

    private HasilEkspor eksporSaldoMengendap(Long sekolahId, String label) {
        RingkasanSaldoMengendap r = laporan.saldoMengendap(sekolahId);
        List<String> header = List.of("Kategori", "Jumlah Subjek", "Saldo");
        List<List<Object>> baris = List.of(
                List.of("Saldo Siswa", r.jumlahSiswa(), r.saldoSiswa()),
                List.of("Saldo Kartu Tamu", r.jumlahKartuTamu(), r.saldoKartuTamu()),
                List.of("TOTAL (kewajiban sekolah)", "", r.total()));
        return berkas("Saldo Mengendap", label, header, baris);
    }

    private HasilEkspor eksporRekonsiliasi(Long sekolahId, LocalDate tgl, OffsetDateTime dari,
                                           OffsetDateTime sampai, String label) {
        RingkasanRekonsiliasi r = laporan.rekonsiliasi(sekolahId, tgl, dari, sampai);
        List<String> header = List.of("Pos", "Nilai");
        List<List<Object>> baris = List.of(
                List.of("Top-up online", r.topupOnline()),
                List.of("Top-up tunai", r.topupTunai()),
                List.of("Penjualan", r.penjualan()),
                List.of("Void penjualan", r.voidPenjualan()),
                List.of("Koreksi masuk", r.koreksiMasuk()),
                List.of("Koreksi keluar", r.koreksiKeluar()),
                List.of("Refund", r.refund()),
                List.of("Transfer (net)", r.transfer()),
                List.of("Saldo mengendap", r.saldoMengendap()),
                List.of("Total kredit (semua waktu)", r.totalKreditSemua()),
                List.of("Total debit (semua waktu)", r.totalDebitSemua()),
                List.of("Selisih", r.selisih()),
                List.of("SEIMBANG?", r.seimbang() ? "YA" : "TIDAK — PERIKSA!"));
        return berkas("Rekonsiliasi", label, header, baris);
    }

    private HasilEkspor eksporStok(Long sekolahId, String label) {
        List<BarisStok> data = laporan.laporanStok(sekolahId, false);
        List<String> header = List.of("Menu ID", "Nama", "Stok", "Stok Min", "HPP",
                "Nilai Persediaan", "Menipis");
        List<List<Object>> baris = new ArrayList<>();
        for (BarisStok b : data) {
            baris.add(List.of(nvl(b.menuId()), nvl(b.nama()), b.stok(), b.stokMinimum(),
                    b.hpp(), b.nilaiPersediaan(), b.menipis() ? "YA" : ""));
        }
        return berkas("Laporan Stok", label, header, baris);
    }

    private HasilEkspor eksporKerugianStok(Long sekolahId, LocalDate tgl, OffsetDateTime dari,
                                           OffsetDateTime sampai, String label) {
        List<BarisKerugianStok> data = laporan.kerugianStok(sekolahId, tgl, dari, sampai);
        List<String> header = List.of("Jenis", "Jumlah Baris", "Total Qty", "Total Nilai");
        List<List<Object>> baris = new ArrayList<>();
        for (BarisKerugianStok b : data) {
            baris.add(List.of(b.jenis(), b.jumlahBaris(), b.totalQty(), b.totalNilai()));
        }
        return berkas("Kerugian Stok", label, header, baris);
    }

    private HasilEkspor eksporBarangMasuk(Long sekolahId, LocalDate tgl, OffsetDateTime dari,
                                          OffsetDateTime sampai, String label) {
        List<MutasiStok> data = laporan.barangMasuk(sekolahId, tgl, dari, sampai);
        List<String> header = List.of("Waktu", "Menu ID", "Qty", "Harga Beli/Unit",
                "Referensi", "Alasan");
        List<List<Object>> baris = new ArrayList<>();
        for (MutasiStok m : data) {
            baris.add(List.of(
                    m.getWaktu() == null ? "" : FMT.format(m.getWaktu()),
                    nvl(m.getMenuId()), m.getQty(), nvl(m.getHargaBeliSatuan()),
                    nvl(m.getReferensiId()), nvl(m.getAlasan())));
        }
        return berkas("Barang Masuk", label, header, baris);
    }

    private HasilEkspor eksporKartuStok(Long sekolahId, Long menuId, String label) {
        List<String> header = List.of("Waktu", "Jenis", "Arah", "Qty", "Stok Setelah",
                "HPP Snapshot", "Harga Beli/Unit", "Referensi", "Alasan", "Aktor");
        List<List<Object>> baris = new ArrayList<>();
        String judul = "Kartu Stok";
        if (menuId != null) {
            KartuStokItem kartu = laporan.kartuStokItem(sekolahId, menuId, 500);
            if (kartu != null) {
                judul = "Kartu Stok - " + kartu.namaMenu();
                for (KartuStokItem.BarisMutasi m : kartu.mutasi()) {
                    baris.add(List.of(
                            m.waktu() == null ? "" : FMT.format(m.waktu()),
                            m.jenis() == null ? "" : m.jenis().name(),
                            m.arah() == null ? "" : m.arah().name(),
                            m.qty(), m.stokSetelah(), nvl(m.hppSnapshot()),
                            nvl(m.hargaBeliSatuan()), nvl(m.referensiId()),
                            nvl(m.alasan()), nvl(m.aktorNama())));
                }
            }
        }
        return berkas(judul, label, header, baris);
    }

    private HasilEkspor eksporPembatalan(Long sekolahId, LocalDate tgl, OffsetDateTime dari,
                                         OffsetDateTime sampai, String label) {
        List<BarisPembatalanKasir> data = laporan.pembatalanKasir(sekolahId, tgl, dari, sampai);
        List<String> header = List.of("Transaksi ID", "Subjek", "Subjek ID", "Total",
                "Alasan Void", "Void Oleh", "Void At", "Petugas", "Titik Kasir", "Waktu");
        List<List<Object>> baris = new ArrayList<>();
        for (BarisPembatalanKasir b : data) {
            baris.add(List.of(
                    nvl(b.getTransaksiId()),
                    b.getSubjekTipe() == null ? "" : b.getSubjekTipe().name(),
                    nvl(b.getSubjekId()), b.getTotal(), nvl(b.getAlasanVoid()),
                    nvl(b.getVoidOleh()),
                    b.getVoidAt() == null ? "" : FMT.format(b.getVoidAt()),
                    nvl(b.getPetugasId()), nvl(b.getTitikKasirId()),
                    b.getWaktu() == null ? "" : FMT.format(b.getWaktu())));
        }
        return berkas("Pembatalan Kasir", label, header, baris);
    }

    private HasilEkspor eksporKartuTamu(Long sekolahId, String label) {
        List<BarisKartuTamu> data = laporan.laporanKartuTamu(sekolahId);
        List<String> header = List.of("Kartu ID", "Nomor Kartu", "Pemegang", "RFID UID",
                "Aktif", "Saldo");
        List<List<Object>> baris = new ArrayList<>();
        for (BarisKartuTamu b : data) {
            baris.add(List.of(nvl(b.getKartuId()), nvl(b.getNomorKartu()),
                    nvl(b.getLabelPemegang()), nvl(b.getRfidUid()),
                    b.isAktif() ? "YA" : "TIDAK", b.getSaldo()));
        }
        return berkas("Laporan Kartu Tamu", label, header, baris);
    }

    private HasilEkspor eksporPenjualanDimensi(Long sekolahId, LocalDate tgl, OffsetDateTime dari,
                                               OffsetDateTime sampai, String label, String judul,
                                               String kolomNama, List<BarisPenjualanDimensi> data) {
        List<String> header = List.of("ID", kolomNama, "Jumlah Transaksi", "Nilai", "HPP", "Laba Kotor");
        List<List<Object>> baris = new ArrayList<>();
        for (BarisPenjualanDimensi b : data) {
            baris.add(List.of(nvl(b.kunciId()), nvl(b.nama()), b.jumlahTransaksi(),
                    b.nilai(), b.hpp(), b.labaKotor()));
        }
        return berkas(judul, label, header, baris);
    }

    // ────────────────────────────────────────────────────────────────

    private HasilEkspor berkas(String judul, String label, List<String> header,
                               List<List<Object>> baris) {
        List<String> judulBaris = List.of(judul, "Periode: " + label);
        byte[] isi = excel.tulis(judul, judulBaris, header, baris);
        String nama = judul.replaceAll("\\s+", "-").toLowerCase() + ".xlsx";
        return new HasilEkspor(nama, isi);
    }

    private String labelPeriode(LocalDate tanggal, OffsetDateTime dari, OffsetDateTime sampai) {
        if (dari != null && sampai != null) {
            return FMT.format(dari) + " s/d " + FMT.format(sampai);
        }
        LocalDate tgl = (tanggal != null) ? tanggal : jam.hariIni();
        return tgl.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
    }

    private static Object nvl(Object v) {
        return v == null ? "" : v;
    }
}
