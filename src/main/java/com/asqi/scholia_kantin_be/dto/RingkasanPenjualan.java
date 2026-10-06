package com.asqi.scholia_kantin_be.dto;

import java.time.OffsetDateTime;

/**
 * Ringkasan penjualan &amp; laba kotor untuk satu periode (PRD §9.5: laporan
 * Penjualan &amp; Laba Kotor).
 *
 * <p>Laba kotor = penjualan bersih − Σ HPP item terjual (PRD §5). Nilai void
 * ditampilkan terpisah agar terlihat bruto vs bersih.
 *
 * @param dari            awal periode (inklusif)
 * @param sampai          akhir periode (eksklusif)
 * @param jumlahTransaksi banyak transaksi SUKSES
 * @param penjualanBruto  Σ total transaksi SUKSES
 * @param jumlahVoid      banyak transaksi VOID
 * @param nilaiVoid       Σ total transaksi VOID (info)
 * @param penjualanBersih penjualan bruto (SUKSES) — void tidak pernah masuk bruto
 * @param totalHpp        Σ HPP snapshot item terjual (SUKSES)
 * @param labaKotor       penjualanBersih − totalHpp
 */
public record RingkasanPenjualan(
        OffsetDateTime dari,
        OffsetDateTime sampai,
        long jumlahTransaksi,
        long penjualanBruto,
        long jumlahVoid,
        long nilaiVoid,
        long penjualanBersih,
        long totalHpp,
        long labaKotor) {
}
