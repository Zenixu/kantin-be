package com.asqi.scholia_kantin_be.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Ringkasan kontrol satu subjek (PRD §8.3, issue #40): status blokir kartu,
 * limit harian, dan daftar item/kategori yang diblokir.
 *
 * <p>Dipakai FE (kasir/back office &amp; ortu) untuk menampilkan kontrol aktif.
 */
@Data
@Builder
public class KontrolSubjekResponse {

    private String subjekTipe;
    private Long subjekId;

    /** Kartu diblokir (tap ditolak). */
    private boolean diblokir;
    private String alasanBlokir;

    /** Limit harian (rupiah); {@code null} = tanpa limit. */
    private Long limitHarian;
    private boolean adaLimit;

    /** Daftar menu yang diblokir (id). */
    private List<Long> menuDiblokir;

    /** Daftar kategori yang diblokir (id). */
    private List<Long> kategoriDiblokir;

    /** Baris blokir item mentah (untuk audit/tampilan detail). */
    private List<BlokirItemResponse> item;
}
