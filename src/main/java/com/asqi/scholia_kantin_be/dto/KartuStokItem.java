package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.ArahStok;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import com.asqi.scholia_kantin_be.model.MutasiStok;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Laporan <b>Kartu stok per item</b> (PRD §9.5, issue #118): riwayat mutasi satu
 * menu — masuk, terjual, void, penyesuaian — beserta saldo stok berjalan.
 * Akses Pengelola/Bendahara.
 *
 * @param menuId       id menu
 * @param namaMenu     nama menu
 * @param satuan       satuan menu (mis. PCS)
 * @param stokSekarang stok berjalan saat ini
 * @param mutasi       riwayat mutasi (terbaru dulu)
 */
public record KartuStokItem(
        Long menuId,
        String namaMenu,
        String satuan,
        int stokSekarang,
        List<BarisMutasi> mutasi) {

    /** Satu baris mutasi stok pada kartu stok. */
    public record BarisMutasi(
            Long id,
            OffsetDateTime waktu,
            JenisMutasiStok jenis,
            ArahStok arah,
            int qty,
            int stokSetelah,
            Long hppSnapshot,
            Long hargaBeliSatuan,
            String referensiId,
            String alasan,
            String aktorNama) {

        public static BarisMutasi dari(MutasiStok m) {
            return new BarisMutasi(m.getId(), m.getWaktu(), m.getJenis(), m.getArah(),
                    m.getQty() == null ? 0 : m.getQty(),
                    m.getStokSetelah() == null ? 0 : m.getStokSetelah(),
                    m.getHppSnapshot(), m.getHargaBeliSatuan(),
                    m.getReferensiId(), m.getAlasan(), m.getAktorNama());
        }
    }
}
