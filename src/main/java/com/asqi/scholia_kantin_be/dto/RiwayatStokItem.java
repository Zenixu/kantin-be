package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.ArahStok;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import com.asqi.scholia_kantin_be.model.MutasiStok;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Satu baris <b>riwayat mutasi stok</b> (PRD §9.5) untuk
 * {@code GET /api/stok/riwayat} — dipakai FE menampilkan daftar restock
 * sebelum memilih baris yang akan dibalik.
 */
@Data
@Builder
public class RiwayatStokItem {

    /** ID baris ledger (dipakai sebagai {@code mutasiId} saat membalik). */
    private Long id;

    private Long menuId;

    /** Nama menu (bila masih ada di katalog; {@code null} bila tak ditemukan). */
    private String menuNama;

    private ArahStok arah;

    private JenisMutasiStok jenis;

    private int qty;

    /** Harga beli/unit — hanya terisi untuk {@code BARANG_MASUK}. */
    private Long hargaBeliSatuan;

    /** Total nilai = qty × harga beli (hanya untuk {@code BARANG_MASUK}). */
    private Long totalNilai;

    private Long hppSnapshot;

    /** Stok sistem setelah mutasi ini (audit). */
    private int stokSetelah;

    private String referensiTipe;

    private String referensiId;

    /** Alasan (opname/pembalik). */
    private String alasan;

    /** Untuk {@code BARANG_MASUK_PEMBALIK}: id barang masuk yang dibalik. */
    private Long mutasiAsalId;

    /** Qty yang sudah dibalik dari baris ini (hanya untuk {@code BARANG_MASUK}). */
    private Integer sudahDibalik;

    /** Sisa yang masih dapat dibalik (hanya untuk {@code BARANG_MASUK}). */
    private Integer sisaDapatDibalik;

    /** true bila baris ini masih bisa dibalik (BARANG_MASUK &amp; sisa &gt; 0). */
    private boolean dapatDibalik;

    private Long aktorId;

    /**
     * Nama aktor (snapshot saat mutasi dicatat). {@code null} bila aksi sistem
     * atau baris lama sebelum kolom ini ada — FE menampilkan "Sistem".
     */
    private String aktorNama;

    private OffsetDateTime waktu;

    /** Proyeksi entitas ledger → DTO (menyembunyikan kolom internal). */
    public static RiwayatStokItem dari(MutasiStok m) {
        boolean barangMasuk = m.getJenis() == JenisMutasiStok.BARANG_MASUK;
        Long harga = barangMasuk ? m.getHargaBeliSatuan() : null;
        return RiwayatStokItem.builder()
                .id(m.getId())
                .menuId(m.getMenuId())
                .arah(m.getArah())
                .jenis(m.getJenis())
                .qty(m.getQty())
                .hargaBeliSatuan(harga)
                .totalNilai(harga == null ? null : harga * m.getQty())
                .hppSnapshot(m.getHppSnapshot())
                .stokSetelah(m.getStokSetelah())
                .referensiTipe(m.getReferensiTipe())
                .referensiId(m.getReferensiId())
                .alasan(m.getAlasan())
                .mutasiAsalId(m.getMutasiAsalId())
                .aktorId(m.getAktorId())
                .aktorNama(m.getAktorNama())
                .waktu(m.getWaktu())
                .build();
    }
}
