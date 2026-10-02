package com.asqi.scholia_kantin_be.service.stok;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.security.SekolahGuard;
import com.asqi.scholia_kantin_be.model.StokCache;
import com.asqi.scholia_kantin_be.repository.StokCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Facade <b>transaksi-owning</b> untuk operasi tulis stok.
 *
 * <p>Mengapa ada kelas terpisah: {@link LedgerStokService} menandai operasi
 * tulisnya {@code @Transactional(propagation = MANDATORY)} — sengaja, agar
 * pemotongan stok <b>selalu</b> menjadi bagian transaksi bisnis yang lebih besar
 * (mis. satu tap = saldo + stok + transaksi dalam satu commit, PRD §11.2).
 * Karena itu controller <b>tidak boleh</b> memanggil {@code LedgerStokService}
 * langsung (akan gagal tanpa transaksi). Facade inilah yang membuka transaksi
 * lalu mendelegasikan.
 *
 * <p>Tanggung jawab lain: validasi &amp; normalisasi input HTTP, penentuan
 * {@code referensiTipe}/{@code referensiId} (idempotency di level ledger), dan
 * konversi entitas → DTO respons.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StokOperasiService {

    private final LedgerStokService ledgerStok;
    private final StokCacheRepository cacheRepo;
    private final SekolahGuard sekolahGuard;

    // ────────────────────────────────────────────────────────────────
    // TULIS
    // ────────────────────────────────────────────────────────────────

    /**
     * Barang masuk (PRD §7.2): tambah stok &amp; perbarui HPP rata-rata
     * tertimbang. Idempoten lewat {@code referensiId} (nomor bukti penerimaan).
     */
    @Transactional
    public HasilMutasiStok masukBarang(Long sekolahId, Long menuId, int qty,
                                       long hargaBeliPerUnit, String referensiId, Long aktorId) {
        validasiReferensi(referensiId, "Nomor bukti barang masuk");
        return ledgerStok.masukBarang(sekolahId, menuId, qty, hargaBeliPerUnit,
                "BARANG_MASUK", referensiId, aktorId);
    }

    /**
     * Penyesuaian stok hasil opname fisik (PRD §7.3). Alasan wajib.
     * HPP rata-rata tidak berubah; bila selisih = 0 tidak ada mutasi.
     */
    @Transactional
    public HasilMutasiStok sesuaikanOpname(Long sekolahId, Long menuId, int qtyFisik,
                                           String alasan, String referensiId, Long aktorId) {
        validasiReferensi(referensiId, "Nomor berita acara opname");
        return ledgerStok.sesuaikanOpname(sekolahId, menuId, qtyFisik, alasan, referensiId, aktorId);
    }

    // ────────────────────────────────────────────────────────────────
    // BACA
    // ────────────────────────────────────────────────────────────────

    /** Stok &amp; HPP berjalan satu menu (tenant-scoped). */
    @Transactional(readOnly = true)
    public StokCache lihat(Long sekolahId, Long menuId) {
        StokCache cache = cacheRepo.findByMenuId(menuId)
                .orElseThrow(() -> new NotFoundEntity("Data stok menu tidak ditemukan"));
        // Guard ganda: bila baris ada tapi milik sekolah lain → 404 (bukan bocor).
        sekolahGuard.pastikanMilikSekolah(cache.getSekolahId(), "Stok");
        if (!cache.getSekolahId().equals(sekolahId)) {
            throw new NotFoundEntity("Data stok menu tidak ditemukan");
        }
        return cache;
    }

    /** Daftar menu dengan stok di bawah minimum (tenant-scoped). */
    @Transactional(readOnly = true)
    public List<StokCache> stokMenipis(Long sekolahId) {
        return cacheRepo.stokMenipis(sekolahId);
    }

    /** Hitung ulang stok dari ledger — untuk verifikasi/rekonsiliasi cache. */
    @Transactional(readOnly = true)
    public long hitungUlangDariLedger(Long sekolahId, Long menuId) {
        return ledgerStok.hitungUlangDariLedger(sekolahId, menuId);
    }

    private void validasiReferensi(String referensiId, String label) {
        if (referensiId == null || referensiId.isBlank()) {
            throw new InvalidOperationException(label + " wajib diisi (idempotency)");
        }
    }
}
