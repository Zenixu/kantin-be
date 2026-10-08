package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.model.Transaksi;
import com.asqi.scholia_kantin_be.model.TransaksiItem;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Satu transaksi pada riwayat Kartu Tamu (PRD §9.4/§9.5, issue #122).
 *
 * <p>Proyeksi entitas {@link Transaksi} → DTO, termasuk rincian item &amp; info
 * void, agar TU dapat menampilkan/mencetak riwayat per kartu atas permintaan
 * pemegang.
 */
@Data
@Builder
public class RiwayatTransaksiKartuItem {

    private Long transaksiId;
    private OffsetDateTime waktu;
    private long total;
    private long totalHpp;
    private StatusTransaksi status;
    private String kartuUid;
    private Long petugasId;
    private Long titikKasirId;
    private String alasanVoid;
    private OffsetDateTime voidAt;
    private List<ItemRingkas> items;

    /** Satu baris item transaksi (snapshot). */
    @Data
    @Builder
    public static class ItemRingkas {
        private Long menuId;
        private String namaMenu;
        private long hargaJual;
        private int qty;
        private long subtotal;

        static ItemRingkas dari(TransaksiItem i) {
            return ItemRingkas.builder()
                    .menuId(i.getMenuId())
                    .namaMenu(i.getNamaMenu())
                    .hargaJual(i.getHargaJual())
                    .qty(i.getQty())
                    .subtotal(i.getSubtotal())
                    .build();
        }
    }

    /** Proyeksi entitas → DTO. */
    public static RiwayatTransaksiKartuItem dari(Transaksi t) {
        return RiwayatTransaksiKartuItem.builder()
                .transaksiId(t.getId())
                .waktu(t.getWaktu())
                .total(t.getTotal())
                .totalHpp(t.getTotalHpp())
                .status(t.getStatus())
                .kartuUid(t.getKartuUid())
                .petugasId(t.getPetugasId())
                .titikKasirId(t.getTitikKasirId())
                .alasanVoid(t.getAlasanVoid())
                .voidAt(t.getVoidAt())
                .items(t.getItems() == null ? List.of()
                        : t.getItems().stream().map(ItemRingkas::dari).toList())
                .build();
    }
}
