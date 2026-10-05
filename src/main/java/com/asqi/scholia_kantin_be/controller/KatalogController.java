package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.KategoriRequest;
import com.asqi.scholia_kantin_be.dto.MenuRequest;
import com.asqi.scholia_kantin_be.dto.MenuResponse;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.model.KategoriMenu;
import com.asqi.scholia_kantin_be.model.Menu;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.security.PerluPeran;
import com.asqi.scholia_kantin_be.security.TenantContext;
import com.asqi.scholia_kantin_be.service.katalog.KatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Katalog menu &amp; kategori (PRD §7.1).
 *
 * <p>Baca boleh oleh petugas kasir; tulis hanya pengelola/TU/admin. Semua
 * tenant-scoped (sekolah lain ⇒ 404). Soft delete lewat {@code nonaktifkan}.
 */
@RestController
@RequestMapping("api/katalog")
@RequiredArgsConstructor
public class KatalogController {

    private final KatalogService katalog;

    // ────────────────────────────────────────────────────────────────
    // KATEGORI
    // ────────────────────────────────────────────────────────────────

    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.PENGELOLA_KANTIN,
            AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("kategori")
    public ResponseEntity<Response<List<KategoriMenu>>> daftarKategori(
            @RequestParam(defaultValue = "false") boolean hanyaAktif) {
        return CommonResponse.data(
                katalog.daftarKategori(TenantContext.sekolahIdWajib(), hanyaAktif));
    }

    @PerluPeran({AktorKantin.PENGELOLA_KANTIN, AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @PostMapping("kategori")
    public ResponseEntity<Response<KategoriMenu>> buatKategori(
            @Valid @RequestBody KategoriRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {
        KategoriMenu kategori = katalog.buatKategori(
                TenantContext.sekolahIdWajib(), request.getNama(),
                request.getUrutan(), identitas.aktorIdWajib());
        return CommonResponse.data(kategori, "Kategori dibuat");
    }

    @PerluPeran({AktorKantin.PENGELOLA_KANTIN, AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @PutMapping("kategori/{kategoriId}")
    public ResponseEntity<Response<KategoriMenu>> ubahKategori(
            @PathVariable Long kategoriId,
            @Valid @RequestBody KategoriRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {
        KategoriMenu kategori = katalog.ubahKategori(
                TenantContext.sekolahIdWajib(), kategoriId, request.getNama(),
                request.getUrutan(), null, identitas.aktorIdWajib());
        return CommonResponse.data(kategori, "Kategori diperbarui");
    }

    /** Nonaktifkan kategori (soft delete) — tak bisa bila masih dipakai item aktif. */
    @PerluPeran({AktorKantin.PENGELOLA_KANTIN, AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @DeleteMapping("kategori/{kategoriId}")
    public ResponseEntity<Response<KategoriMenu>> nonaktifkanKategori(
            @PathVariable Long kategoriId,
            @AuthenticationPrincipal IdentitasKantin identitas) {
        KategoriMenu kategori = katalog.nonaktifkanKategori(
                TenantContext.sekolahIdWajib(), kategoriId, identitas.aktorIdWajib());
        return CommonResponse.data(kategori, "Kategori dinonaktifkan");
    }

    // ────────────────────────────────────────────────────────────────
    // MENU
    // ────────────────────────────────────────────────────────────────

    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.PENGELOLA_KANTIN,
            AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("menu")
    public ResponseEntity<Response<List<MenuResponse>>> daftarMenu(
            @RequestParam(required = false) Long kategoriId,
            @RequestParam(defaultValue = "false") boolean hanyaAktif) {
        Long sekolahId = TenantContext.sekolahIdWajib();
        List<Menu> menu = katalog.daftarMenu(sekolahId, kategoriId, hanyaAktif);
        // Stok berjalan diambil sekaligus (satu query) agar FE tak memanggil
        // GET /api/stok/{menuId} per item (hindari N+1).
        Map<Long, Integer> stok = katalog.stokBerjalan(sekolahId,
                menu.stream().map(Menu::getId).toList());
        List<MenuResponse> daftar = menu.stream()
                .map(m -> MenuResponse.dari(m, stok.getOrDefault(m.getId(), 0)))
                .toList();
        return CommonResponse.data(daftar);
    }

    @PerluPeran({AktorKantin.PETUGAS_KANTIN, AktorKantin.PENGELOLA_KANTIN,
            AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @GetMapping("menu/{menuId}")
    public ResponseEntity<Response<MenuResponse>> lihatMenu(@PathVariable Long menuId) {
        Long sekolahId = TenantContext.sekolahIdWajib();
        Menu menu = katalog.lihatMenu(sekolahId, menuId);
        return CommonResponse.data(MenuResponse.dari(menu, katalog.stokBerjalan(sekolahId, menuId)));
    }

    @PerluPeran({AktorKantin.PENGELOLA_KANTIN, AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @PostMapping("menu")
    public ResponseEntity<Response<MenuResponse>> buatMenu(
            @Valid @RequestBody MenuRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {
        var menu = katalog.buatMenu(
                TenantContext.sekolahIdWajib(), request.getKategoriId(), request.getNama(),
                request.getHargaJual(), request.getSatuan(), request.getFotoUrl(),
                request.getStokMinimum(), identitas.aktorIdWajib());
        return CommonResponse.data(MenuResponse.dari(menu), "Menu dibuat");
    }

    @PerluPeran({AktorKantin.PENGELOLA_KANTIN, AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @PutMapping("menu/{menuId}")
    public ResponseEntity<Response<MenuResponse>> ubahMenu(
            @PathVariable Long menuId,
            @Valid @RequestBody MenuRequest request,
            @AuthenticationPrincipal IdentitasKantin identitas) {
        var menu = katalog.ubahMenu(
                TenantContext.sekolahIdWajib(), menuId, request.getKategoriId(), request.getNama(),
                request.getHargaJual(), request.getSatuan(), request.getFotoUrl(),
                request.getStokMinimum(), null, identitas.aktorIdWajib());
        return CommonResponse.data(MenuResponse.dari(menu,
                katalog.stokBerjalan(TenantContext.sekolahIdWajib(), menuId)), "Menu diperbarui");
    }

    /** Nonaktifkan item (soft delete) — item nonaktif tidak bisa dijual. */
    @PerluPeran({AktorKantin.PENGELOLA_KANTIN, AktorKantin.TU_SEKOLAH, AktorKantin.ADMIN_SEKOLAH})
    @DeleteMapping("menu/{menuId}")
    public ResponseEntity<Response<MenuResponse>> nonaktifkanMenu(
            @PathVariable Long menuId,
            @AuthenticationPrincipal IdentitasKantin identitas) {
        var menu = katalog.nonaktifkanMenu(
                TenantContext.sekolahIdWajib(), menuId, identitas.aktorIdWajib());
        return CommonResponse.data(MenuResponse.dari(menu), "Menu dinonaktifkan");
    }
}
