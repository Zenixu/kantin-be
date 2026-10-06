package com.asqi.scholia_kantin_be.service.stok;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Perhitungan HPP (Harga Pokok Penjualan) — <b>rata-rata tertimbang</b>
 * (PRD §7.4).
 *
 * <pre>
 *   HPP baru = (stok sekarang × HPP sekarang + qty masuk × harga beli)
 *              ÷ (stok sekarang + qty masuk)
 * </pre>
 *
 * <p>Aturan (PRD §7.4):
 * <ul>
 *   <li>Dihitung ulang setiap barang masuk, dibulatkan ke rupiah terdekat.</li>
 *   <li>Bila stok sekarang 0 → HPP baru = harga beli barang masuk.</li>
 *   <li>Penjualan memakai HPP yang berlaku saat itu dan menyimpannya sebagai
 *       snapshot; void mengembalikan stok dengan HPP snapshot tersebut.</li>
 * </ul>
 *
 * <p>Perhitungan murni (tanpa DB) agar mudah diuji unit (CONVENTIONS.md §9).
 * Nilai uang tetap integer rupiah; {@code BigDecimal} hanya dipakai sebagai
 * alat hitung sementara lalu dibulatkan, <b>bukan</b> disimpan sebagai pecahan.
 */
@Service
public class HppService {

    /**
     * Hitung HPP rata-rata tertimbang baru setelah barang masuk.
     *
     * @param stokSekarang stok sistem sebelum barang masuk (≥ 0)
     * @param hppSekarang  HPP rata-rata berjalan sebelum masuk (≥ 0)
     * @param qtyMasuk     jumlah barang masuk (&gt; 0)
     * @param hargaBeli    harga beli per unit barang masuk (≥ 0)
     * @return HPP baru per unit (rupiah integer, dibulatkan HALF_UP)
     */
    public long hitungRataRataTertimbang(long stokSekarang, long hppSekarang,
                                         int qtyMasuk, long hargaBeli) {
        if (qtyMasuk <= 0) {
            throw new IllegalArgumentException("qtyMasuk harus > 0");
        }
        if (stokSekarang < 0 || hppSekarang < 0 || hargaBeli < 0) {
            throw new IllegalArgumentException("stok/HPP/harga beli tidak boleh negatif");
        }

        // Stok kosong (atau minus secara teori) → HPP = harga beli barang masuk.
        if (stokSekarang <= 0) {
            return hargaBeli;
        }

        long totalStok = stokSekarang + qtyMasuk;
        BigDecimal nilaiLama = BigDecimal.valueOf(stokSekarang).multiply(BigDecimal.valueOf(hppSekarang));
        BigDecimal nilaiMasuk = BigDecimal.valueOf(qtyMasuk).multiply(BigDecimal.valueOf(hargaBeli));
        BigDecimal hppBaru = nilaiLama.add(nilaiMasuk)
                .divide(BigDecimal.valueOf(totalStok), 0, RoundingMode.HALF_UP);

        return hppBaru.longValueExact();
    }

    /**
     * Hitung HPP rata-rata tertimbang <b>setelah barang masuk dibalik</b>
     * (PRD §7.2) — kebalikan dari {@link #hitungRataRataTertimbang}.
     *
     * <pre>
     *   HPP baru = (stok sekarang × HPP sekarang − qty dibalik × harga beli asal)
     *              ÷ (stok sekarang − qty dibalik)
     * </pre>
     *
     * <p>Nilai persediaan dikurangi sebesar nilai barang yang dibalik; bila stok
     * menjadi 0 → HPP 0. Nilai tidak pernah negatif (dijaga {@code ≤ 0 → 0}).
     *
     * @param stokSekarang  stok sistem sebelum pembalik (≥ {@code qtyBalik})
     * @param hppSekarang   HPP rata-rata berjalan sebelum pembalik (≥ 0)
     * @param qtyBalik      jumlah yang dibalik (&gt; 0, ≤ {@code stokSekarang})
     * @param hargaBeliAsal harga beli/unit barang masuk asal (≥ 0)
     * @return HPP baru per unit (rupiah integer, dibulatkan HALF_UP)
     */
    public long hitungRataRataSetelahPembalik(long stokSekarang, long hppSekarang,
                                              int qtyBalik, long hargaBeliAsal) {
        if (qtyBalik <= 0) {
            throw new IllegalArgumentException("qtyBalik harus > 0");
        }
        if (stokSekarang < 0 || hppSekarang < 0 || hargaBeliAsal < 0) {
            throw new IllegalArgumentException("stok/HPP/harga beli tidak boleh negatif");
        }
        if (qtyBalik > stokSekarang) {
            throw new IllegalArgumentException("qtyBalik melebihi stok sekarang");
        }

        long stokBaru = stokSekarang - qtyBalik;
        if (stokBaru <= 0) {
            return 0L;
        }

        BigDecimal nilaiLama = BigDecimal.valueOf(stokSekarang).multiply(BigDecimal.valueOf(hppSekarang));
        BigDecimal nilaiBalik = BigDecimal.valueOf(qtyBalik).multiply(BigDecimal.valueOf(hargaBeliAsal));
        BigDecimal nilaiBaru = nilaiLama.subtract(nilaiBalik);
        if (nilaiBaru.signum() <= 0) {
            return 0L;
        }

        return nilaiBaru.divide(BigDecimal.valueOf(stokBaru), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Nilai persediaan satu item = stok × HPP (untuk laporan stok, PRD §9.5). */
    public long nilaiPersediaan(int stok, long hpp) {
        return (long) stok * hpp;
    }
}
