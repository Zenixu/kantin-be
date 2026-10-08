package com.asqi.scholia_kantin_be.service.kartu;

import com.asqi.scholia_kantin_be.dto.HalamanResponse;
import com.asqi.scholia_kantin_be.dto.MutasiSaldoItem;
import com.asqi.scholia_kantin_be.dto.RiwayatKartuTamuResponse;
import com.asqi.scholia_kantin_be.dto.RiwayatTransaksiKartuItem;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.KartuTamu;
import com.asqi.scholia_kantin_be.repository.TransaksiRepository;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Riwayat transaksi &amp; mutasi saldo per <b>Kartu Tamu</b> (PRD §9.4/§9.5,
 * issue #122) — dapat dilihat/dicetak TU atas permintaan pemegang.
 *
 * <p>Menggabungkan identitas kartu + saldo berjalan ({@link LedgerSaldoService})
 * + transaksi berhalaman ({@link TransaksiRepository}, tenant-scoped) + mutasi
 * saldo terbaru. Kartu divalidasi lewat {@link KartuTamuService#detailKartu}
 * sehingga sekolah lain → 404 (PRD §11.4).
 */
@Service
@RequiredArgsConstructor
public class RiwayatKartuTamuService {

    private final KartuTamuService kartuService;
    private final TransaksiRepository transaksiRepo;
    private final LedgerSaldoService ledgerSaldo;

    /**
     * Riwayat satu Kartu Tamu: identitas + saldo + transaksi (berhalaman) +
     * mutasi saldo terbaru.
     *
     * @param sekolahId   tenant
     * @param kartuId     ID kartu
     * @param batasMutasi jumlah mutasi saldo terbaru yang disertakan
     * @param halaman     indeks halaman transaksi (0-based)
     * @param ukuran      ukuran halaman transaksi
     */
    @Transactional(readOnly = true)
    public RiwayatKartuTamuResponse riwayat(Long sekolahId, Long kartuId,
                                            int batasMutasi, int halaman, int ukuran) {
        KartuTamu kartu = kartuService.detailKartu(sekolahId, kartuId);

        Page<RiwayatTransaksiKartuItem> transaksi = transaksiRepo
                .findBySekolahIdAndSubjekTipeAndSubjekIdOrderByIdDesc(
                        sekolahId, SubjekTipe.KARTU_TAMU, kartuId,
                        PageRequest.of(Math.max(0, halaman), Math.max(1, Math.min(ukuran, 100))))
                .map(RiwayatTransaksiKartuItem::dari);

        List<MutasiSaldoItem> mutasi = ledgerSaldo
                .riwayatTerbaru(sekolahId, SubjekTipe.KARTU_TAMU, kartuId, batasMutasi)
                .stream()
                .map(MutasiSaldoItem::dari)
                .toList();

        return RiwayatKartuTamuResponse.builder()
                .kartuId(kartu.getId())
                .nomorKartu(kartu.getNomorKartu())
                .labelPemegang(kartu.getLabelPemegang())
                .aktif(Boolean.TRUE.equals(kartu.getAktif()))
                .saldo(ledgerSaldo.saldo(sekolahId, SubjekTipe.KARTU_TAMU, kartuId))
                .transaksi(HalamanResponse.dari(transaksi, x -> x))
                .mutasi(mutasi)
                .build();
    }
}
