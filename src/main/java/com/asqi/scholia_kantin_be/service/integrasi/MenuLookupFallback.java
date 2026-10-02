package com.asqi.scholia_kantin_be.service.integrasi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementasi <b>sementara</b> {@link MenuLookupPort} — fail-closed.
 *
 * <p>Modul katalog (Fase 5) belum dibangun. Sampai itu ada, lookup menu
 * mengembalikan {@code null} sehingga validasi tap gagal dengan pesan
 * "Menu tidak ditemukan atau nonaktif" — <b>bukan</b> menjual item tanpa
 * harga/HPP yang benar.
 *
 * <p>Ketika {@code MenuRepository} tersedia, ganti dengan implementasi nyata
 * ({@code @Primary}) atau hapus kelas ini.
 */
@Service
@Slf4j
public class MenuLookupFallback implements MenuLookupPort {

    @Override
    public InfoMenu cari(Long sekolahId, Long menuId) {
        log.warn("Lookup menu belum dikonfigurasi (Fase 5). menuId={} tidak ditemukan.", menuId);
        return null;
    }
}
