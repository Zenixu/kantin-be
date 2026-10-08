package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.SaldoLedger;
import com.asqi.scholia_kantin_be.model.Transaksi;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Laporan <b>Per siswa</b> (PRD §9.5, issue #117): riwayat lengkap satu siswa
 * pada periode — ringkasan, daftar transaksi, dan mutasi saldo — untuk menjawab
 * komplain orang tua. Akses Bendahara/Admin.
 *
 * @param subjekTipe  tipe subjek (SISWA / KARTU_TAMU)
 * @param subjekId    id subjek
 * @param saldo       saldo berjalan saat ini
 * @param periode     label periode (mis. "2025-01-01 s/d 2025-01-31")
 * @param ringkasan   agregat periode
 * @param transaksi   daftar transaksi periode (urut waktu menaik)
 * @param mutasiSaldo daftar mutasi saldo periode (urut waktu menaik)
 */
public record LaporanPerSiswa(
        SubjekTipe subjekTipe,
        Long subjekId,
        long saldo,
        String periode,
        Ringkasan ringkasan,
        List<BarisTransaksi> transaksi,
        List<BarisMutasi> mutasiSaldo) {

    /** Agregat periode untuk satu siswa. */
    public record Ringkasan(
            long jumlahTransaksiSukses,
            long nilaiBelanjaSukses,
            long jumlahTransaksiVoid,
            long nilaiVoid,
            long totalTopup,
            long totalHpp) {
    }

    /** Satu transaksi siswa pada periode. */
    public record BarisTransaksi(
            Long id,
            StatusTransaksi status,
            long total,
            long totalHpp,
            Long petugasId,
            Long titikKasirId,
            String alasanVoid,
            OffsetDateTime waktu) {

        public static BarisTransaksi dari(Transaksi t) {
            return new BarisTransaksi(t.getId(), t.getStatus(), t.getTotal(), t.getTotalHpp(),
                    t.getPetugasId(), t.getTitikKasirId(), t.getAlasanVoid(), t.getWaktu());
        }
    }

    /** Satu mutasi saldo siswa pada periode. */
    public record BarisMutasi(
            Long id,
            JenisMutasiSaldo jenis,
            String arah,
            long nominal,
            long saldoSetelah,
            Long transaksiId,
            String keterangan,
            OffsetDateTime waktu) {

        public static BarisMutasi dari(SaldoLedger l) {
            return new BarisMutasi(l.getId(), l.getJenis(),
                    l.getArah() == null ? null : l.getArah().name(),
                    l.getNominal() == null ? 0L : l.getNominal(),
                    l.getSaldoSetelah() == null ? 0L : l.getSaldoSetelah(),
                    l.getTransaksiId(), l.getKeterangan(), l.getWaktu());
        }
    }
}
