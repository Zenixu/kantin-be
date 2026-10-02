package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.dto.HasilValidasiTap;
import com.asqi.scholia_kantin_be.dto.TapRequest;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import com.asqi.scholia_kantin_be.service.integrasi.InfoMenu;
import com.asqi.scholia_kantin_be.service.stok.LedgerStokService;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Validator 6 tahap sebelum tap dieksekusi (PRD §6.1).
 *
 * <p>Dipisah dari {@link TapService} agar <b>urutan</b> pemeriksaan eksplisit,
 * mudah dibaca, dan mudah diuji. Urutan wajib:
 * <pre>
 *   1 kartu dikenal → 2 tidak diblokir → 3 tidak ada item diblokir ortu
 *   → 4 stok cukup → 5 ≤ limit harian → 6 saldo ≥ total
 * </pre>
 * Kegagalan pertama menghentikan proses dan mengembalikan pesan yang tampil di
 * layar kasir (sesuai tabel PRD §6.1).
 *
 * <p>Validator <b>tidak</b> mengubah state apa pun — hanya membaca.
 */
@Service
@RequiredArgsConstructor
public class TapValidator {

    private final LedgerStokService ledgerStok;

    /** Satu baris keranjang yang sudah diperkaya info menu &amp; subtotal. */
    @Getter
    @Builder
    public static class Baris {
        private final Long menuId;
        private final int qty;
        private final InfoMenu menu;
        private final long subtotal;
    }

    /** Hasil validasi lengkap: status + baris keranjang (bila lolos tahap 3–4). */
    @Getter
    public static class Hasil {
        private final HasilValidasiTap validasi;
        private final List<Baris> baris;
        private final long total;

        Hasil(HasilValidasiTap validasi, List<Baris> baris, long total) {
            this.validasi = validasi;
            this.baris = baris;
            this.total = total;
        }

        public boolean valid() {
            return validasi.isValid();
        }
    }

    /**
     * Jalankan seluruh tahap validasi.
     *
     * @param sekolahId    tenant
     * @param request      permintaan tap (berisi daftar item)
     * @param kartu        hasil lookup kartu
     * @param saldoSaatIni saldo berjalan subjek (dari ledger)
     * @param belanjaHariIni total belanja DEBIT hari ini (untuk limit)
     * @param cariMenu     fungsi lookup info menu (dari port katalog)
     */
    public Hasil validasi(Long sekolahId, TapRequest request, InfoKartu kartu,
                          long saldoSaatIni, long belanjaHariIni,
                          java.util.function.Function<Long, InfoMenu> cariMenu) {

        // ── Tahap 1: kartu dikenal ──────────────────────────────────
        if (!kartu.isDikenal()) {
            return gagal(1, "Kartu tidak dikenal", null, 0);
        }

        // ── Tahap 2: kartu tidak diblokir (tanpa cache, PRD §11.11) ──
        if (kartu.isDiblokir()) {
            String pesan = kartu.siswa()
                    ? "Kartu diblokir, hubungi orang tua"
                    : "Kartu diblokir";
            return gagal(2, pesan, null, 0);
        }

        // ── Bangun baris keranjang + validasi menu ──────────────────
        List<Baris> baris = new ArrayList<>();
        long total = 0L;
        for (TapRequest.ItemTap item : request.getItems()) {
            if (item.getQty() == null || item.getQty() <= 0) {
                return gagal(4, "Jumlah item harus lebih dari 0", null, 0);
            }
            InfoMenu menu = cariMenu.apply(item.getMenuId());
            if (menu == null || !menu.isAktif()) {
                return gagal(1, "Menu tidak ditemukan atau nonaktif", null, 0);
            }

            // ── Tahap 3: item/kategori tidak diblokir ortu (siswa saja) ──
            if (kartu.siswa()) {
                boolean diblokirItem = kartu.getMenuDiblokir() != null
                        && kartu.getMenuDiblokir().contains(menu.getMenuId());
                boolean diblokirKategori = kartu.getKategoriDiblokir() != null
                        && menu.getKategoriId() != null
                        && kartu.getKategoriDiblokir().contains(menu.getKategoriId());
                if (diblokirItem || diblokirKategori) {
                    return gagal(3, "Item " + menu.getNama() + " diblokir oleh orang tua", null, 0);
                }
            }

            // ── Tahap 4: stok cukup ─────────────────────────────────
            int stok = ledgerStok.stok(sekolahId, menu.getMenuId());
            if (stok < item.getQty()) {
                return gagal(4, "Stok " + menu.getNama() + " tidak cukup (sisa " + stok + ")", null, 0);
            }

            long subtotal = menu.getHargaJual() * item.getQty();
            total += subtotal;
            baris.add(Baris.builder()
                    .menuId(menu.getMenuId())
                    .qty(item.getQty())
                    .menu(menu)
                    .subtotal(subtotal)
                    .build());
        }

        // ── Tahap 5: ≤ limit harian (siswa saja) ────────────────────
        if (kartu.siswa() && kartu.getLimitHarian() != null) {
            long sisaLimit = kartu.getLimitHarian() - belanjaHariIni;
            if (total > sisaLimit) {
                long sisa = Math.max(0, sisaLimit);
                return gagal(5, "Melebihi limit harian (sisa Rp " + sisa + ")", baris, total);
            }
        }

        // ── Tahap 6: saldo ≥ total ──────────────────────────────────
        if (saldoSaatIni < total) {
            return gagal(6, "Saldo kurang Rp " + (total - saldoSaatIni), baris, total);
        }

        return new Hasil(HasilValidasiTap.lolos(), baris, total);
    }

    private Hasil gagal(int tahap, String pesan, List<Baris> baris, long total) {
        return new Hasil(HasilValidasiTap.gagal(tahap, pesan), baris, total);
    }
}
