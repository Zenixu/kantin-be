package com.asqi.scholia_kantin_be.service.laporan;

import com.asqi.scholia_kantin_be.dto.BarisKerugianStok;
import com.asqi.scholia_kantin_be.dto.BarisPenjualan;
import com.asqi.scholia_kantin_be.dto.BarisStok;
import com.asqi.scholia_kantin_be.dto.RingkasanPenjualan;
import com.asqi.scholia_kantin_be.dto.RingkasanRekonsiliasi;
import com.asqi.scholia_kantin_be.dto.RingkasanSaldoMengendap;
import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.MutasiStok;
import com.asqi.scholia_kantin_be.model.SaldoLedger;
import com.asqi.scholia_kantin_be.model.StokCache;
import com.asqi.scholia_kantin_be.repository.KategoriMenuRepository;
import com.asqi.scholia_kantin_be.repository.MenuRepository;
import com.asqi.scholia_kantin_be.repository.MutasiStokRepository;
import com.asqi.scholia_kantin_be.repository.SaldoCacheRepository;
import com.asqi.scholia_kantin_be.repository.SaldoLedgerRepository;
import com.asqi.scholia_kantin_be.repository.StokCacheRepository;
import com.asqi.scholia_kantin_be.repository.TransaksiItemRepository;
import com.asqi.scholia_kantin_be.repository.TransaksiRepository;
import com.asqi.scholia_kantin_be.service.stok.HppService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Laporan &amp; agregasi (PRD §9.5). Semua query <b>tenant-scoped</b>
 * ({@code sekolahId} eksplisit) dan <b>baca-saja</b> — tidak mengubah ledger.
 *
 * <p>Bila {@code dari}/{@code sampai} {@code null}, periode dianggap
 * <b>hari ini</b> menurut zona sekolah ({@link JamKantin}). Laporan berbasis
 * periode memakai rentang <b>inklusif-dari, eksklusif-sampai</b>.
 *
 * <p><b>Invariant rekonsiliasi (PRD §5):</b> {@link #rekonsiliasi} memeriksa
 * {@code Σ KREDIT − Σ DEBIT == saldo mengendap}. Bila tidak sama → peringatan.
 */
@Service
@RequiredArgsConstructor
public class LaporanService {

    /** Batas baris laporan item terlaris (top-N). */
    private static final int MAKS_ITEM_TERLARIS = 100;

    private static final Set<JenisMutasiSaldo> JENIS_KOREKSI =
            EnumSet.of(JenisMutasiSaldo.KOREKSI, JenisMutasiSaldo.PENYESUAIAN);

    private final SaldoLedgerRepository saldoLedgerRepo;
    private final SaldoCacheRepository saldoCacheRepo;
    private final TransaksiRepository transaksiRepo;
    private final TransaksiItemRepository transaksiItemRepo;
    private final MutasiStokRepository mutasiStokRepo;
    private final StokCacheRepository stokCacheRepo;
    private final MenuRepository menuRepo;
    private final KategoriMenuRepository kategoriRepo;
    private final HppService hppService;
    private final JamKantin jam;

    // ────────────────────────────────────────────────────────────────
    // Periode
    // ────────────────────────────────────────────────────────────────

    /** Rentang waktu efektif: default hari ini (zona sekolah) bila null. */
    private OffsetDateTime[] rentang(LocalDate tanggal, OffsetDateTime dari, OffsetDateTime sampai) {
        LocalDate tgl = (tanggal != null) ? tanggal : jam.hariIni();
        OffsetDateTime awal = (dari != null) ? dari : tgl.atStartOfDay(jam.zona()).toOffsetDateTime();
        OffsetDateTime akhir = (sampai != null) ? sampai : awal.plusDays(1);
        return new OffsetDateTime[]{awal, akhir};
    }

    // ────────────────────────────────────────────────────────────────
    // Penjualan & laba kotor (PRD §9.5)
    // ────────────────────────────────────────────────────────────────

    /** Ringkasan penjualan &amp; laba kotor satu periode. */
    @Transactional(readOnly = true)
    public RingkasanPenjualan ringkasanPenjualan(Long sekolahId, LocalDate tanggal,
                                                 OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        OffsetDateTime awal = r[0];
        OffsetDateTime akhir = r[1];

        long jumlahSukses = 0, bruto = 0, jumlahVoid = 0, nilaiVoid = 0;
        for (Object[] baris : transaksiRepo.rekapPerStatusRentang(sekolahId, awal, akhir)) {
            StatusTransaksi status = (StatusTransaksi) baris[0];
            long jumlah = ((Number) baris[1]).longValue();
            long total = ((Number) baris[2]).longValue();
            if (status == StatusTransaksi.SUKSES) {
                jumlahSukses = jumlah;
                bruto = total;
            } else if (status == StatusTransaksi.VOID) {
                jumlahVoid = jumlah;
                nilaiVoid = total;
            }
        }
        long hpp = nolJikaNull(transaksiRepo.totalHppRentang(
                sekolahId, StatusTransaksi.SUKSES, awal, akhir));

        return new RingkasanPenjualan(awal, akhir, jumlahSukses, bruto,
                jumlahVoid, nilaiVoid, bruto, hpp, bruto - hpp);
    }

    /** Penjualan per item (PRD §9.5). */
    @Transactional(readOnly = true)
    public List<BarisPenjualan> penjualanPerItem(Long sekolahId, LocalDate tanggal,
                                                 OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        List<BarisPenjualan> hasil = new ArrayList<>();
        for (Object[] b : transaksiItemRepo.itemTerlaris(
                sekolahId, r[0], r[1], PageRequest.of(0, MAKS_ITEM_TERLARIS))) {
            hasil.add(new BarisPenjualan(
                    (Long) b[0], (String) b[1],
                    ((Number) b[2]).longValue(), ((Number) b[3]).longValue()));
        }
        return hasil;
    }

    /** Penjualan per kategori (PRD §9.5) — nama kategori diisi dari katalog. */
    @Transactional(readOnly = true)
    public List<BarisPenjualan> penjualanPerKategori(Long sekolahId, LocalDate tanggal,
                                                     OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        Map<Long, String> namaKategori = new HashMap<>();
        kategoriRepo.daftar(sekolahId, false)
                .forEach(k -> namaKategori.put(k.getId(), k.getNama()));

        List<BarisPenjualan> hasil = new ArrayList<>();
        for (Object[] b : transaksiItemRepo.penjualanPerKategori(sekolahId, r[0], r[1])) {
            Long kategoriId = (Long) b[0];
            hasil.add(new BarisPenjualan(
                    kategoriId, namaKategori.get(kategoriId),
                    ((Number) b[1]).longValue(), ((Number) b[2]).longValue()));
        }
        return hasil;
    }

    // ────────────────────────────────────────────────────────────────
    // Saldo mengendap (PRD §9.5)
    // ────────────────────────────────────────────────────────────────

    /** Total dana titipan (kewajiban sekolah) — siswa + Kartu Tamu. */
    @Transactional(readOnly = true)
    public RingkasanSaldoMengendap saldoMengendap(Long sekolahId) {
        long saldoSiswa = 0, saldoKartuTamu = 0, jumlahSiswa = 0, jumlahKartuTamu = 0;
        for (Object[] b : saldoCacheRepo.totalMengendapPerTipe(sekolahId)) {
            SubjekTipe tipe = (SubjekTipe) b[0];
            long total = ((Number) b[1]).longValue();
            long jumlah = ((Number) b[2]).longValue();
            if (tipe == SubjekTipe.SISWA) {
                saldoSiswa = total;
                jumlahSiswa = jumlah;
            } else if (tipe == SubjekTipe.KARTU_TAMU) {
                saldoKartuTamu = total;
                jumlahKartuTamu = jumlah;
            }
        }
        return new RingkasanSaldoMengendap(
                saldoSiswa, saldoKartuTamu, saldoSiswa + saldoKartuTamu,
                jumlahSiswa, jumlahKartuTamu);
    }

    // ────────────────────────────────────────────────────────────────
    // Rekonsiliasi harian (PRD §9.5, invariant §5)
    // ────────────────────────────────────────────────────────────────

    /** Arus saldo periode + pemeriksaan invariant menyeluruh. */
    @Transactional(readOnly = true)
    public RingkasanRekonsiliasi rekonsiliasi(Long sekolahId, LocalDate tanggal,
                                              OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        OffsetDateTime awal = r[0];
        OffsetDateTime akhir = r[1];

        long topupOnline = 0, topupTunai = 0, penjualan = 0, voidPenjualan = 0;
        long koreksiMasuk = 0, koreksiKeluar = 0, refund = 0, transfer = 0;

        for (Object[] b : saldoLedgerRepo.rekapArusRentang(sekolahId, awal, akhir)) {
            ArahMutasi arah = (ArahMutasi) b[0];
            JenisMutasiSaldo jenis = (JenisMutasiSaldo) b[1];
            long nominal = ((Number) b[2]).longValue();
            switch (jenis) {
                case TOPUP_ONLINE -> topupOnline += nominal;
                case TOPUP_TUNAI -> topupTunai += nominal;
                case PENJUALAN -> penjualan += nominal;
                case VOID_PENJUALAN -> voidPenjualan += nominal;
                case REFUND -> refund += nominal;
                case TRANSFER -> transfer += nominal;
                case KOREKSI, PENYESUAIAN -> {
                    if (arah == ArahMutasi.KREDIT) {
                        koreksiMasuk += nominal;
                    } else {
                        koreksiKeluar += nominal;
                    }
                }
                default -> { /* tidak ada */ }
            }
        }

        long totalKredit = 0, totalDebit = 0;
        for (Object[] b : saldoLedgerRepo.totalPerArah(sekolahId)) {
            ArahMutasi arah = (ArahMutasi) b[0];
            long nominal = ((Number) b[1]).longValue();
            if (arah == ArahMutasi.KREDIT) {
                totalKredit = nominal;
            } else {
                totalDebit = nominal;
            }
        }

        long mengendap = saldoMengendap(sekolahId).total();
        long selisih = (totalKredit - totalDebit) - mengendap;

        return new RingkasanRekonsiliasi(awal, akhir, topupOnline, topupTunai,
                penjualan, voidPenjualan, koreksiMasuk, koreksiKeluar, refund,
                transfer, mengendap, totalKredit, totalDebit, selisih, selisih == 0);
    }

    // ────────────────────────────────────────────────────────────────
    // Stok (PRD §9.5)
    // ────────────────────────────────────────────────────────────────

    /** Stok sekarang + nilai persediaan (stok × HPP) untuk semua menu. */
    @Transactional(readOnly = true)
    public List<BarisStok> laporanStok(Long sekolahId, boolean hanyaMenipis) {
        // Sumber kebenaran nama, kategori & stok minimum = katalog menu (PRD §7.1).
        // (stok_cache.stok_minimum tidak disinkron dari menu, jadi jangan dipakai.)
        Map<Long, com.asqi.scholia_kantin_be.model.Menu> menuById = new HashMap<>();
        menuRepo.daftar(sekolahId, null, false).forEach(m -> menuById.put(m.getId(), m));

        List<BarisStok> hasil = new ArrayList<>();
        for (StokCache s : stokCacheRepo.findBySekolahIdAndMenuIdIn(
                sekolahId, menuById.keySet().isEmpty() ? Set.of(-1L) : menuById.keySet())) {
            var menu = menuById.get(s.getMenuId());
            if (menu == null) {
                continue; // baris cache tanpa menu (yatim) — lewati
            }
            int stokMinimum = menu.getStokMinimum() == null ? 0 : menu.getStokMinimum();
            boolean menipis = s.getStok() <= stokMinimum;
            if (hanyaMenipis && !menipis) {
                continue;
            }
            hasil.add(new BarisStok(s.getMenuId(), menu.getNama(), menu.getKategoriId(),
                    s.getStok(), stokMinimum, s.getHpp(),
                    hppService.nilaiPersediaan(s.getStok(), s.getHpp()), menipis,
                    menu.getNama(), s.getStok()));
        }
        return hasil;
    }

    /** Kerugian stok: opname keluar &amp; barang rusak pada periode (PRD §9.5). */
    @Transactional(readOnly = true)
    public List<BarisKerugianStok> kerugianStok(Long sekolahId, LocalDate tanggal,
                                                OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        List<BarisKerugianStok> hasil = new ArrayList<>();
        for (Object[] b : mutasiStokRepo.rekapKerugianRentang(
                sekolahId, EnumSet.of(JenisMutasiStok.OPNAME_KELUAR, JenisMutasiStok.BARANG_RUSAK),
                r[0], r[1])) {
            hasil.add(new BarisKerugianStok(
                    ((JenisMutasiStok) b[0]).name(),
                    ((Number) b[1]).longValue(),
                    ((Number) b[2]).longValue(),
                    ((Number) b[3]).longValue()));
        }
        return hasil;
    }

    /** Barang masuk pada periode (PRD §9.5). */
    @Transactional(readOnly = true)
    public List<MutasiStok> barangMasuk(Long sekolahId, LocalDate tanggal,
                                        OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        return mutasiStokRepo.barangMasukRentang(sekolahId, r[0], r[1]);
    }

    /** Kartu stok per item: riwayat mutasi satu menu (PRD §9.5). */
    @Transactional(readOnly = true)
    public List<MutasiStok> kartuStok(Long sekolahId, Long menuId, int batas) {
        int limit = (batas <= 0 || batas > 500) ? 200 : batas;
        return mutasiStokRepo.kartuStok(sekolahId, menuId, PageRequest.of(0, limit));
    }

    /** Riwayat saldo satu subjek pada periode (laporan per siswa, PRD §9.5). */
    @Transactional(readOnly = true)
    public List<SaldoLedger> mutasiSaldoRentang(Long sekolahId, LocalDate tanggal,
                                                OffsetDateTime dari, OffsetDateTime sampai) {
        OffsetDateTime[] r = rentang(tanggal, dari, sampai);
        return saldoLedgerRepo.padaRentang(sekolahId, r[0], r[1]);
    }

    private static long nolJikaNull(Long nilai) {
        return nilai == null ? 0L : nilai;
    }
}
