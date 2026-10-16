package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.SaldoLedger;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Satu baris <b>daftar mutasi koreksi saldo</b> (PRD §9.2) untuk
 * {@code GET /api/saldo/koreksi} — dipakai bendahara melihat riwayat koreksi
 * (issue <b>#213</b>).
 *
 * <p><b>Latar.</b> Halaman FE *Mutasi Koreksi Bendahara* memanggil
 * {@code GET /api/saldo/koreksi} → <b>405</b> (hanya ada POST) lalu fallback
 * {@code /api/v1/bendahara/koreksi} → <b>404</b>. Aksi koreksinya sendiri sudah
 * ada ({@code POST /api/saldo/koreksi}); yang belum ada adalah cara
 * <i>membacanya kembali</i>. Data koreksi <b>tidak pernah diedit/dihapus</b>
 * (PRD §9.2) — karena itu daftar ini murni baca dari ledger.
 *
 * <p><b>{@code saldoSebelum} dihitung, bukan kolom.</b> Tabel
 * {@code saldo_ledger} hanya menyimpan {@code saldo_setelah}; nilai sebelumnya
 * diturunkan dari arah mutasi ({@code KREDIT} menambah, {@code DEBIT}
 * mengurangi — lihat {@code LedgerSaldoService}).
 *
 * <p>Nama subjek hanya tersedia untuk {@code KARTU_TAMU} (label pemegang /
 * nomor kartu); subjek {@code SISWA} {@code null} karena master siswa milik
 * admin-be (ADR-0002). Nama bendahara juga tidak tersedia (tanpa master
 * pengguna) — hanya {@code aktorId}.
 */
@Data
@Builder
public class MutasiKoreksiItem {

    /** ID baris ledger — jejak audit koreksi. */
    private Long id;

    private OffsetDateTime waktu;

    private SubjekTipe subjekTipe;

    private Long subjekId;

    /** Label subjek: {@code KARTU_TAMU} = label pemegang (fallback nomor kartu); {@code SISWA} = null. */
    private String subjekNama;

    /** {@code KREDIT} = saldo bertambah, {@code DEBIT} = saldo berkurang. */
    private ArahMutasi arah;

    private Long nominal;

    /** Saldo sebelum koreksi (dihitung dari {@code saldoSetelah} &amp; arah). */
    private Long saldoSebelum;

    private Long saldoSetelah;

    /** Alasan koreksi / berita acara (PRD §9.2). */
    private String keterangan;

    /** Referensi berita acara dari bendahara (bila ada). */
    private String referensiId;

    /** Transaksi kasir yang dikoreksi (bila koreksinya berupa pembalik transaksi). */
    private Long transaksiId;

    /** Akun bendahara pelaku koreksi. */
    private Long aktorId;

    /** Proyeksi entitas ledger → DTO (nama subjek diisi service secara batch). */
    public static MutasiKoreksiItem dari(SaldoLedger l) {
        boolean menambah = l.getArah() == ArahMutasi.KREDIT;
        long sebelum = menambah
                ? l.getSaldoSetelah() - l.getNominal()
                : l.getSaldoSetelah() + l.getNominal();
        return MutasiKoreksiItem.builder()
                .id(l.getId())
                .waktu(l.getWaktu())
                .subjekTipe(l.getSubjekTipe())
                .subjekId(l.getSubjekId())
                .arah(l.getArah())
                .nominal(l.getNominal())
                .saldoSebelum(sebelum)
                .saldoSetelah(l.getSaldoSetelah())
                .keterangan(l.getKeterangan())
                .referensiId(l.getReferensiId())
                .transaksiId(l.getTransaksiId())
                .aktorId(l.getAktorId())
                .build();
    }
}
