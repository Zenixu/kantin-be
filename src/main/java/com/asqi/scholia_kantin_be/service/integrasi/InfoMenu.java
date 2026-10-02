package com.asqi.scholia_kantin_be.service.integrasi;

import lombok.Builder;
import lombok.Getter;

/**
 * Snapshot informasi menu yang dibutuhkan saat tap: harga jual, nama, kategori
 * (untuk snapshot transaksi &amp; validasi blokir item).
 *
 * <p>Menu adalah milik kantin-be (Fase 5), tetapi diletakkan di balik port agar
 * {@code TapService} tidak bergantung langsung pada entitas katalog yang belum
 * dibangun.
 */
@Getter
@Builder
public class InfoMenu {

    private final Long menuId;

    private final String nama;

    private final Long hargaJual;

    private final Long kategoriId;

    /** Menu aktif (tidak soft-deleted). Menu nonaktif tak boleh dijual. */
    private final boolean aktif;
}
