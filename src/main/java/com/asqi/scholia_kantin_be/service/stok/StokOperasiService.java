package com.asqi.scholia_kantin_be.service.stok;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.dto.HalamanResponse;
import com.asqi.scholia_kantin_be.dto.RiwayatStokItem;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import com.asqi.scholia_kantin_be.model.Menu;
import com.asqi.scholia_kantin_be.model.MutasiStok;
import com.asqi.scholia_kantin_be.model.StokCache;
import com.asqi.scholia_kantin_be.repository.MenuRepository;
import com.asqi.scholia_kantin_be.repository.MutasiStokRepository;
import com.asqi.scholia_kantin_be.repository.StokCacheRepository;
import com.asqi.scholia_kantin_be.security.SekolahGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
 * konversi entitas → DTO respons (termasuk riwayat).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StokOperasiService {

    private static final int UKURAN_MAKS = 200;

    private final LedgerStokService ledgerStok;
    private final StokCacheRepository cacheRepo;
    private final MutasiStokRepository mutasiRepo;
    private final MenuRepository menuRepo;
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
     * Koreksi barang masuk salah input dengan <b>barang masuk pembalik</b>
     * (PRD §7.2). Data asal tak diubah; dicatat mutasi pembalik baru.
     *
     * @param mutasiId   id baris barang masuk yang dibatalkan
     * @param qty        jumlah dibalik; {@code null} = balik seluruh sisa
     * @param alasan     alasan koreksi (wajib, audit)
     * @param referensiId nomor bukti pembalik (idempotency, wajib)
     */
    @Transactional
    public HasilMutasiStok pembalikBarangMasuk(Long sekolahId, Long mutasiId, Integer qty,
                                               String alasan, String referensiId, Long aktorId) {
        validasiReferensi(referensiId, "Nomor bukti barang masuk pembalik");
        return ledgerStok.pembalikBarangMasuk(sekolahId, mutasiId, qty, alasan, referensiId, aktorId);
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

    /**
     * Riwayat mutasi stok (PRD §9.5) dengan filter opsional — dasar UI memilih
     * baris barang masuk yang akan dibalik. Terbaru dulu, berhalaman.
     *
     * <p>Untuk baris {@code BARANG_MASUK} disertakan {@code sudahDibalik} &amp;
     * {@code sisaDapatDibalik} (dihitung batch, hindari N+1) agar FE tahu mana
     * yang masih dapat dibatalkan.
     */
    @Transactional(readOnly = true)
    public HalamanResponse<RiwayatStokItem> riwayat(Long sekolahId, Long menuId, JenisMutasiStok jenis,
                                                    OffsetDateTime dari, OffsetDateTime sampai,
                                                    int halaman, int ukuran) {
        int ukuranAman = (ukuran <= 0 || ukuran > UKURAN_MAKS) ? 20 : ukuran;
        int halamanAman = Math.max(0, halaman);
        Pageable pageable = PageRequest.of(halamanAman, ukuranAman);

        Page<MutasiStok> page = mutasiRepo.riwayat(sekolahId, menuId, jenis, dari, sampai, pageable);

        // Hitung sisa yang dapat dibalik untuk semua BARANG_MASUK di halaman ini
        // dalam SATU query (bukan per baris).
        List<Long> asalIds = page.getContent().stream()
                .filter(m -> m.getJenis() == JenisMutasiStok.BARANG_MASUK)
                .map(MutasiStok::getId)
                .toList();
        Map<Long, Long> sudahDibalik = totalDibalikPerAsal(sekolahId, asalIds);

        // Nama menu untuk tampilan (batch).
        Set<Long> menuIds = page.getContent().stream()
                .map(MutasiStok::getMenuId)
                .collect(Collectors.toSet());
        Map<Long, String> namaMenu = menuRepo.findAllById(menuIds).stream()
                .filter(m -> m.getSekolahId().equals(sekolahId))
                .collect(Collectors.toMap(Menu::getId, Menu::getNama, (a, b) -> a));

        return HalamanResponse.<MutasiStok, RiwayatStokItem>dari(page, m -> {
            RiwayatStokItem item = RiwayatStokItem.dari(m);
            item.setMenuNama(namaMenu.get(m.getMenuId()));
            if (m.getJenis() == JenisMutasiStok.BARANG_MASUK) {
                long dibalik = sudahDibalik.getOrDefault(m.getId(), 0L);
                int sisa = (int) Math.max(0, m.getQty() - dibalik);
                item.setSudahDibalik((int) dibalik);
                item.setSisaDapatDibalik(sisa);
                item.setDapatDibalik(sisa > 0);
            } else {
                item.setDapatDibalik(false);
            }
            return item;
        });
    }

    // ────────────────────────────────────────────────────────────────
    // HELPER
    // ────────────────────────────────────────────────────────────────

    /** Peta {@code asalMutasiId → total qty dibalik} (satu query, hindari N+1). */
    private Map<Long, Long> totalDibalikPerAsal(Long sekolahId, List<Long> asalIds) {
        if (asalIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> hasil = new HashMap<>();
        for (Object[] baris : mutasiRepo.totalDibalikPerAsal(sekolahId, asalIds)) {
            hasil.put((Long) baris[0], ((Number) baris[1]).longValue());
        }
        return hasil;
    }

    private void validasiReferensi(String referensiId, String label) {
        if (referensiId == null || referensiId.isBlank()) {
            throw new InvalidOperationException(label + " wajib diisi (idempotency)");
        }
    }
}
