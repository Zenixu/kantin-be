package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.Transaksi;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Satu baris <b>daftar transaksi kasir</b> (PRD §6.3, §9.2) untuk
 * {@code GET /api/kasir/transaksi} — dipakai bendahara memilih transaksi yang
 * akan dikoreksi pada <b>sesi yang sudah ditutup</b> (issue #212).
 *
 * <p><b>Latar.</b> Aksi koreksinya sudah ada
 * ({@code POST /api/kasir/transaksi/{transaksiId}/koreksi}), tetapi bendahara
 * tidak punya cara menemukan {@code transaksiId} — FE memanggil endpoint daftar
 * yang belum ada dan menerima <b>404</b>. DTO ini mengisi kekosongan itu.
 *
 * <p>Kolom internal disembunyikan (HPP, idempotency key). Nama titik kasir dan
 * nama pemegang kartu tamu diisi service secara <i>batch</i> (hindari N+1);
 * untuk subjek {@code SISWA} nama <b>tidak</b> tersedia di kantin-be (data siswa
 * milik admin-be, ADR-0002) sehingga bernilai {@code null} — FE menampilkan
 * nomor induk/identitasnya sendiri.
 */
@Data
@Builder
public class TransaksiKasirItem {

    /** ID transaksi — dipakai sebagai {@code transaksiId} saat mengoreksi. */
    private Long id;

    private OffsetDateTime waktu;

    private Long sesiKasirId;

    private Long titikKasirId;

    /** Nama titik kasir; {@code null} bila titiknya sudah dihapus/di luar tenant. */
    private String titikKasirNama;

    /** Akun petugas yang memproses (PRD §6.6). Nama petugas tak ada di kantin-be. */
    private Long petugasId;

    private SubjekTipe subjekTipe;

    private Long subjekId;

    /**
     * Label subjek: untuk {@code KARTU_TAMU} diisi label pemegang (fallback nomor
     * kartu); untuk {@code SISWA} {@code null} (lihat catatan kelas).
     */
    private String subjekNama;

    /** Kartu fisik yang dipakai — jejak audit sengketa. */
    private String kartuUid;

    private Long total;

    private StatusTransaksi status;

    /** Alasan koreksi/void (terisi hanya bila {@code status = VOID}). */
    private String alasanVoid;

    /** Waktu transaksi di-void/dikoreksi (terisi hanya bila {@code status = VOID}). */
    private OffsetDateTime voidAt;

    /** true bila transaksi masih dapat dikoreksi bendahara (belum VOID). */
    private boolean dapatDikoreksi;

    /** Proyeksi entitas → DTO (tanpa nama titik/subjek — diisi service batch). */
    public static TransaksiKasirItem dari(Transaksi t) {
        return TransaksiKasirItem.builder()
                .id(t.getId())
                .waktu(t.getWaktu())
                .sesiKasirId(t.getSesiKasirId())
                .titikKasirId(t.getTitikKasirId())
                .petugasId(t.getPetugasId())
                .subjekTipe(t.getSubjekTipe())
                .subjekId(t.getSubjekId())
                .kartuUid(t.getKartuUid())
                .total(t.getTotal())
                .status(t.getStatus())
                .alasanVoid(t.getAlasanVoid())
                .voidAt(t.getVoidAt())
                .dapatDikoreksi(t.getStatus() == StatusTransaksi.SUKSES)
                .build();
    }
}
