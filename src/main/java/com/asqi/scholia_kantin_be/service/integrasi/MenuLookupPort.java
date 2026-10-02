package com.asqi.scholia_kantin_be.service.integrasi;

/**
 * Port lookup menu (katalog) untuk kebutuhan transaksi kasir.
 *
 * <p>Katalog adalah milik kantin-be (Fase 5). Port ini menjadi jahitan agar
 * {@code TapService} bisa diuji tanpa database katalog lengkap dan agar modul
 * katalog bisa dibangun terpisah.
 */
public interface MenuLookupPort {

    /**
     * Ambil info menu untuk snapshot transaksi.
     *
     * @return info menu, atau {@code null} bila menu tidak ada
     */
    InfoMenu cari(Long sekolahId, Long menuId);
}
