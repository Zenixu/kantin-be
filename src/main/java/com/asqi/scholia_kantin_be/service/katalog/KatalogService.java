package com.asqi.scholia_kantin_be.service.katalog;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.enums.SatuanMenu;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.KategoriMenu;
import com.asqi.scholia_kantin_be.model.Menu;
import com.asqi.scholia_kantin_be.repository.KategoriMenuRepository;
import com.asqi.scholia_kantin_be.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Katalog menu &amp; kategori (PRD §7.1).
 *
 * <p>Aturan yang ditegakkan:
 * <ul>
 *   <li><b>Soft delete</b> — item/kategori dinonaktifkan ({@code isActive=false}),
 *       riwayat transaksi tetap (transaksi menyimpan snapshot).</li>
 *   <li><b>Ubah harga jual tercatat di audit</b> (PRD §7.1, §11.7) — transaksi
 *       lama tidak berubah karena snapshot.</li>
 *   <li><b>Kategori terpakai hanya boleh dinonaktifkan</b>, tidak dihapus keras
 *       (FK {@code ON DELETE RESTRICT}).</li>
 *   <li>Semua baca/tulis <b>tenant-scoped</b> (PRD §11.4): sekolah lain ⇒ 404.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KatalogService {

    private final KategoriMenuRepository kategoriRepo;
    private final MenuRepository menuRepo;
    private final IdGenerator idGenerator;
    private final JamKantin jam;
    private final AuditLogger auditLogger;

    // ────────────────────────────────────────────────────────────────
    // KATEGORI
    // ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<KategoriMenu> daftarKategori(Long sekolahId, boolean hanyaAktif) {
        return kategoriRepo.daftar(sekolahId, hanyaAktif);
    }

    @Transactional
    public KategoriMenu buatKategori(Long sekolahId, String nama, Integer urutan, Long aktorId) {
        String bersih = normalisasiNama(nama);
        kategoriRepo.findBySekolahIdAndNamaIgnoreCase(sekolahId, bersih).ifPresent(k -> {
            throw new InvalidOperationException("Kategori '" + bersih + "' sudah ada");
        });

        var sekarang = jam.sekarang();
        KategoriMenu kategori = KategoriMenu.builder()
                .id(idGenerator.berikutnyaLong())
                .sekolahId(sekolahId)
                .nama(bersih)
                .urutan(urutan == null ? 0 : urutan)
                .isActive(true)
                .createdAt(sekarang)
                .updatedAt(sekarang)
                .build();

        KategoriMenu tersimpan = kategoriRepo.save(kategori);
        auditLogger.catat(aktorId, sekolahId, "KATEGORI_DIBUAT", "KategoriMenu",
                String.valueOf(tersimpan.getId()), null, null, bersih);
        return tersimpan;
    }

    @Transactional
    public KategoriMenu ubahKategori(Long sekolahId, Long kategoriId, String nama,
                                     Integer urutan, Boolean aktif, Long aktorId) {
        KategoriMenu kategori = kategoriRepo.findByIdAndSekolahId(kategoriId, sekolahId)
                .orElseThrow(() -> new NotFoundEntity("Kategori tidak ditemukan"));

        String lama = kategori.getNama();
        if (nama != null && !nama.isBlank()) {
            String bersih = normalisasiNama(nama);
            kategoriRepo.findBySekolahIdAndNamaIgnoreCase(sekolahId, bersih)
                    .filter(k -> !k.getId().equals(kategoriId))
                    .ifPresent(k -> {
                        throw new InvalidOperationException("Kategori '" + bersih + "' sudah ada");
                    });
            kategori.setNama(bersih);
        }
        if (urutan != null) {
            kategori.setUrutan(urutan);
        }
        if (aktif != null) {
            kategori.setIsActive(aktif);
        }
        kategori.setUpdatedAt(jam.sekarang());

        KategoriMenu tersimpan = kategoriRepo.save(kategori);
        auditLogger.catat(aktorId, sekolahId, "KATEGORI_DIUBAH", "KategoriMenu",
                String.valueOf(kategoriId), null, lama, kategori.getNama());
        return tersimpan;
    }

    /**
     * Nonaktifkan kategori (soft delete). Kategori yang masih dipakai item
     * aktif <b>tidak boleh</b> dinonaktifkan agar item tak menggantung (§7.1).
     */
    @Transactional
    public KategoriMenu nonaktifkanKategori(Long sekolahId, Long kategoriId, Long aktorId) {
        KategoriMenu kategori = kategoriRepo.findByIdAndSekolahId(kategoriId, sekolahId)
                .orElseThrow(() -> new NotFoundEntity("Kategori tidak ditemukan"));

        long dipakai = menuRepo.countByKategoriIdAndIsActiveTrue(kategoriId);
        if (dipakai > 0) {
            throw new InvalidOperationException(
                    "Kategori masih dipakai " + dipakai + " item aktif — nonaktifkan item dahulu");
        }

        kategori.setIsActive(false);
        kategori.setUpdatedAt(jam.sekarang());
        KategoriMenu tersimpan = kategoriRepo.save(kategori);
        auditLogger.catat(aktorId, sekolahId, "KATEGORI_DINONAKTIFKAN", "KategoriMenu",
                String.valueOf(kategoriId), null, "aktif=true", "aktif=false");
        return tersimpan;
    }

    // ────────────────────────────────────────────────────────────────
    // MENU
    // ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Menu> daftarMenu(Long sekolahId, Long kategoriId, boolean hanyaAktif) {
        return menuRepo.daftar(sekolahId, kategoriId, hanyaAktif);
    }

    @Transactional(readOnly = true)
    public Menu lihatMenu(Long sekolahId, Long menuId) {
        return menuRepo.findByIdAndSekolahId(menuId, sekolahId)
                .orElseThrow(() -> new NotFoundEntity("Menu tidak ditemukan"));
    }

    @Transactional
    public Menu buatMenu(Long sekolahId, Long kategoriId, String nama, Long hargaJual,
                         SatuanMenu satuan, String fotoUrl, Integer stokMinimum, Long aktorId) {
        String bersih = normalisasiNama(nama);
        validasiHarga(hargaJual);
        if (kategoriId != null) {
            pastikanKategoriMilikSekolah(sekolahId, kategoriId);
        }

        var sekarang = jam.sekarang();
        Menu menu = Menu.builder()
                .id(idGenerator.berikutnyaLong())
                .sekolahId(sekolahId)
                .kategoriId(kategoriId)
                .nama(bersih)
                .hargaJual(hargaJual)
                .satuan(satuan == null ? SatuanMenu.PCS : satuan)
                .fotoUrl(fotoUrl)
                .stokMinimum(stokMinimum == null ? 0 : stokMinimum)
                .isActive(true)
                .createdAt(sekarang)
                .updatedAt(sekarang)
                .build();

        Menu tersimpan = menuRepo.save(menu);
        auditLogger.catat(aktorId, sekolahId, "MENU_DIBUAT", "Menu",
                String.valueOf(tersimpan.getId()), null, null,
                bersih + " @ " + hargaJual);
        return tersimpan;
    }

    /**
     * Ubah item. Perubahan <b>harga jual dicatat khusus</b> di audit (PRD §7.1)
     * agar jejak perubahan harga terbaca jelas.
     */
    @Transactional
    public Menu ubahMenu(Long sekolahId, Long menuId, Long kategoriId, String nama,
                         Long hargaJual, SatuanMenu satuan, String fotoUrl,
                         Integer stokMinimum, Boolean aktif, Long aktorId) {
        Menu menu = menuRepo.findByIdAndSekolahId(menuId, sekolahId)
                .orElseThrow(() -> new NotFoundEntity("Menu tidak ditemukan"));

        if (nama != null && !nama.isBlank()) {
            menu.setNama(normalisasiNama(nama));
        }
        if (kategoriId != null) {
            pastikanKategoriMilikSekolah(sekolahId, kategoriId);
            menu.setKategoriId(kategoriId);
        }
        if (hargaJual != null) {
            validasiHarga(hargaJual);
            long hargaLama = menu.getHargaJual();
            if (hargaLama != hargaJual) {
                // Audit khusus perubahan harga (PRD §7.1) — snapshot transaksi lama tetap.
                auditLogger.catat(aktorId, sekolahId, "UBAH_HARGA_JUAL", "Menu",
                        String.valueOf(menuId), null,
                        String.valueOf(hargaLama), String.valueOf(hargaJual));
                menu.setHargaJual(hargaJual);
            }
        }
        if (satuan != null) {
            menu.setSatuan(satuan);
        }
        if (fotoUrl != null) {
            menu.setFotoUrl(fotoUrl);
        }
        if (stokMinimum != null) {
            if (stokMinimum < 0) {
                throw new InvalidOperationException("Stok minimum tidak boleh negatif");
            }
            menu.setStokMinimum(stokMinimum);
        }
        if (aktif != null) {
            menu.setIsActive(aktif);
        }
        menu.setUpdatedAt(jam.sekarang());

        Menu tersimpan = menuRepo.save(menu);
        auditLogger.catat(aktorId, sekolahId, "MENU_DIUBAH", "Menu",
                String.valueOf(menuId), null, null, tersimpan.getNama());
        return tersimpan;
    }

    /** Nonaktifkan item (soft delete) — item nonaktif tidak bisa dijual (PRD §7.1). */
    @Transactional
    public Menu nonaktifkanMenu(Long sekolahId, Long menuId, Long aktorId) {
        Menu menu = menuRepo.findByIdAndSekolahId(menuId, sekolahId)
                .orElseThrow(() -> new NotFoundEntity("Menu tidak ditemukan"));
        menu.setIsActive(false);
        menu.setUpdatedAt(jam.sekarang());
        Menu tersimpan = menuRepo.save(menu);
        auditLogger.catat(aktorId, sekolahId, "MENU_DINONAKTIFKAN", "Menu",
                String.valueOf(menuId), null, "aktif=true", "aktif=false");
        return tersimpan;
    }

    // ────────────────────────────────────────────────────────────────
    // HELPER
    // ────────────────────────────────────────────────────────────────

    private void pastikanKategoriMilikSekolah(Long sekolahId, Long kategoriId) {
        kategoriRepo.findByIdAndSekolahId(kategoriId, sekolahId)
                .orElseThrow(() -> new NotFoundEntity("Kategori tidak ditemukan"));
    }

    private void validasiHarga(Long hargaJual) {
        if (hargaJual == null || hargaJual < 0) {
            throw new InvalidOperationException("Harga jual wajib dan tidak boleh negatif");
        }
    }

    private String normalisasiNama(String nama) {
        if (nama == null || nama.isBlank()) {
            throw new InvalidOperationException("Nama wajib diisi");
        }
        String bersih = nama.trim().replaceAll("\\s+", " ");
        if (bersih.length() > 150) {
            throw new InvalidOperationException("Nama maksimal 150 karakter");
        }
        return bersih;
    }
}
