package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.Transaksi;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Satu baris laporan <b>Pembatalan kasir</b> (PRD §9.5, issue #114):
 * transaksi yang dibatalkan lewat tombol Batalkan ("Kartu dipakai bukan
 * pemiliknya") per subjek.
 *
 * <p>Proyeksi entitas {@link Transaksi} (status VOID) → DTO agar laporan dapat
 * menampilkan alasan void, waktu, &amp; petugas.
 */
@Data
@Builder
public class BarisPembatalanKasir {

    private Long transaksiId;
    private SubjekTipe subjekTipe;
    private Long subjekId;
    private String kartuUid;
    private long total;
    private String alasanVoid;
    private OffsetDateTime voidAt;
    private Long voidOleh;
    private Long petugasId;
    private Long titikKasirId;
    private OffsetDateTime waktu;

    /** Proyeksi entitas → DTO. */
    public static BarisPembatalanKasir dari(Transaksi t) {
        return BarisPembatalanKasir.builder()
                .transaksiId(t.getId())
                .subjekTipe(t.getSubjekTipe())
                .subjekId(t.getSubjekId())
                .kartuUid(t.getKartuUid())
                .total(t.getTotal())
                .alasanVoid(t.getAlasanVoid())
                .voidAt(t.getVoidAt())
                .voidOleh(t.getVoidOleh())
                .petugasId(t.getPetugasId())
                .titikKasirId(t.getTitikKasirId())
                .waktu(t.getWaktu())
                .build();
    }
}
