package com.asqi.scholia_kantin_be.service.katalog;

import com.asqi.scholia_kantin_be.model.Menu;
import com.asqi.scholia_kantin_be.repository.MenuRepository;
import com.asqi.scholia_kantin_be.service.integrasi.InfoMenu;
import com.asqi.scholia_kantin_be.service.integrasi.MenuLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementasi <b>nyata</b> (produksi) {@link MenuLookupPort} — menggantikan
 * {@code MenuLookupFallback} yang mengembalikan {@code null}.
 *
 * <p>Dipanggil {@code TapService} untuk snapshot transaksi: nama, harga jual,
 * kategori, dan status aktif menu (PRD §6.2, §7.1). Tenant-scoped — menu
 * sekolah lain dianggap tidak ada.
 *
 * <p><b>Tanpa {@code @Primary}</b>: di produksi hanya ada satu bean
 * {@link MenuLookupPort}, jadi tidak perlu. Uji yang menyediakan fake
 * {@code @Primary} tetap menang tanpa konflik.
 */
@Service
@RequiredArgsConstructor
public class MenuLookupKatalogAdapter implements MenuLookupPort {

    private final MenuRepository menuRepo;

    @Override
    @Transactional(readOnly = true)
    public InfoMenu cari(Long sekolahId, Long menuId) {
        if (sekolahId == null || menuId == null) {
            return null;
        }
        return menuRepo.findByIdAndSekolahId(menuId, sekolahId)
                .map(this::keInfo)
                .orElse(null);
    }

    private InfoMenu keInfo(Menu m) {
        return InfoMenu.builder()
                .menuId(m.getId())
                .nama(m.getNama())
                .hargaJual(m.getHargaJual())
                .kategoriId(m.getKategoriId())
                .aktif(Boolean.TRUE.equals(m.getIsActive()))
                .build();
    }
}
