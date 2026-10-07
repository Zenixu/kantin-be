package com.asqi.scholia_kantin_be.service.kartu;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.dto.BlokirItemResponse;
import com.asqi.scholia_kantin_be.dto.KontrolSubjekResponse;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.BlokirItem;
import com.asqi.scholia_kantin_be.model.BlokirKartu;
import com.asqi.scholia_kantin_be.model.LimitHarian;
import com.asqi.scholia_kantin_be.repository.BlokirItemRepository;
import com.asqi.scholia_kantin_be.repository.BlokirKartuRepository;
import com.asqi.scholia_kantin_be.repository.LimitHarianRepository;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Kontrol kartu: blokir kartu, limit harian &amp; blokir item
 * (PRD §6.1 tahap 2/3/5, §8.3, issue <b>#40</b>).
 *
 * <p><b>Milik kantin-be.</b> Berbeda dari identitas pemilik kartu (dari admin-be,
 * masih menunggu kontrak Q7), <i>kontrol</i> (blokir/limit/blokir item) adalah
 * data kantin-be sendiri — sehingga dapat dibangun &amp; ditegakkan <b>tanpa</b>
 * menunggu Q7. Saat Q7 terjawab, implementasi {@code KartuLookupPort} yang nyata
 * cukup memanggil {@link #terapkan(Long, InfoKartu)} untuk menempelkan kontrol ke
 * identitas — logika tap tidak berubah.
 *
 * <p><b>Blokir berlaku instan / tanpa cache (PRD §11.11).</b> {@link #terapkan}
 * membaca tabel kontrol <b>setiap</b> pemanggilan (tidak ada cache berbasis
 * waktu), sehingga kartu yang diblokir sedetik sebelumnya tetap ditolak.
 *
 * <p><b>Kartu Tamu</b> (§9.4) tidak punya limit harian / blokir item — hanya
 * bisa diblokir (mis. kartu hilang). Percobaan set limit/item untuk Kartu Tamu
 * ditolak dengan pesan jelas.
 *
 * <p>Semua operasi <b>tenant-scoped</b> (PRD §11.4) dan <b>diaudit</b>
 * (perubahan limit/blokir — PRD §11.7).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KontrolKartuService {

    private final BlokirKartuRepository blokirRepo;
    private final LimitHarianRepository limitRepo;
    private final BlokirItemRepository itemRepo;
    private final IdGenerator idGenerator;
    private final JamKantin jam;
    private final AuditLogger auditLogger;

    // ────────────────────────────────────────────────────────────────
    // BLOKIR KARTU (PRD §6.1 tahap 2, §8.3)
    // ────────────────────────────────────────────────────────────────

    /**
     * Blokir / buka blokir kartu subjek (upsert). Berlaku instan.
     *
     * @param diblokir {@code true} = blokir, {@code false} = buka blokir
     */
    @Transactional
    public KontrolSubjekResponse ubahBlokir(Long sekolahId, SubjekTipe subjekTipe, Long subjekId,
                                            boolean diblokir, String alasan, Long aktorId) {
        validasiSubjek(subjekTipe, subjekId);
        OffsetDateTime now = jam.sekarang();

        BlokirKartu baris = blokirRepo
                .findBySekolahIdAndSubjekTipeAndSubjekId(sekolahId, subjekTipe, subjekId)
                .orElseGet(() -> BlokirKartu.builder()
                        .id(idGenerator.berikutnyaLong())
                        .sekolahId(sekolahId)
                        .subjekTipe(subjekTipe)
                        .subjekId(subjekId)
                        .createdAt(now)
                        .build());
        boolean lama = baris.isDiblokir();
        baris.setDiblokir(diblokir);
        baris.setAlasan(alasan);
        baris.setDiubahOleh(aktorId);
        baris.setDiubahPada(now);
        blokirRepo.save(baris);

        auditLogger.catat(aktorId, sekolahId,
                diblokir ? "BLOKIR_KARTU" : "BUKA_BLOKIR_KARTU", "BlokirKartu",
                subjekTipe + ":" + subjekId, alasan,
                String.valueOf(lama), String.valueOf(diblokir));
        log.info("Kontrol kartu {}:{} diblokir={} oleh={}",
                subjekTipe, subjekId, diblokir, aktorId);
        return kontrol(sekolahId, subjekTipe, subjekId);
    }

    // ────────────────────────────────────────────────────────────────
    // LIMIT HARIAN (PRD §6.1 tahap 5, §8.3)
    // ────────────────────────────────────────────────────────────────

    /**
     * Set / ubah limit belanja harian subjek (upsert). {@code nominal} {@code null}
     * = tanpa limit. Hanya untuk siswa (Kartu Tamu tanpa limit — §9.4).
     */
    @Transactional
    public KontrolSubjekResponse setLimit(Long sekolahId, SubjekTipe subjekTipe, Long subjekId,
                                          Long nominal, Long aktorId) {
        validasiSubjek(subjekTipe, subjekId);
        if (subjekTipe != SubjekTipe.SISWA) {
            throw new InvalidOperationException(
                    "Limit harian hanya berlaku untuk siswa (Kartu Tamu tanpa limit — PRD §9.4)");
        }
        if (nominal != null && nominal < 0) {
            throw new InvalidOperationException("Limit harian tidak boleh negatif");
        }
        OffsetDateTime now = jam.sekarang();

        LimitHarian baris = limitRepo
                .findBySekolahIdAndSubjekTipeAndSubjekId(sekolahId, subjekTipe, subjekId)
                .orElseGet(() -> LimitHarian.builder()
                        .id(idGenerator.berikutnyaLong())
                        .sekolahId(sekolahId)
                        .subjekTipe(subjekTipe)
                        .subjekId(subjekId)
                        .createdAt(now)
                        .build());
        Long lama = baris.getNominal();
        baris.setNominal(nominal);
        baris.setDiubahOleh(aktorId);
        baris.setDiubahPada(now);
        limitRepo.save(baris);

        auditLogger.catat(aktorId, sekolahId, "SET_LIMIT_HARIAN", "LimitHarian",
                subjekTipe + ":" + subjekId, "Ubah limit harian",
                lama == null ? "TANPA_LIMIT" : String.valueOf(lama),
                nominal == null ? "TANPA_LIMIT" : String.valueOf(nominal));
        log.info("Limit harian {}:{} → {} oleh={}",
                subjekTipe, subjekId, nominal == null ? "tanpa limit" : nominal, aktorId);
        return kontrol(sekolahId, subjekTipe, subjekId);
    }

    // ────────────────────────────────────────────────────────────────
    // BLOKIR ITEM / KATEGORI (PRD §6.1 tahap 3, §8.3)
    // ────────────────────────────────────────────────────────────────

    /**
     * Blokir / buka blokir satu item <b>atau</b> satu kategori untuk subjek.
     * Hanya untuk siswa (Kartu Tamu tanpa blokir item — §9.4).
     *
     * @param menuId     id menu yang diblokir (isi salah satu dengan kategoriId)
     * @param kategoriId id kategori yang diblokir (isi salah satu dengan menuId)
     */
    @Transactional
    public KontrolSubjekResponse ubahBlokirItem(Long sekolahId, SubjekTipe subjekTipe, Long subjekId,
                                                Long menuId, Long kategoriId, boolean diblokir,
                                                Long aktorId) {
        validasiSubjek(subjekTipe, subjekId);
        if (subjekTipe != SubjekTipe.SISWA) {
            throw new InvalidOperationException(
                    "Blokir item hanya berlaku untuk siswa (Kartu Tamu tanpa blokir item — PRD §9.4)");
        }
        boolean adaMenu = menuId != null;
        boolean adaKategori = kategoriId != null;
        if (adaMenu == adaKategori) {
            throw new InvalidOperationException(
                    "Isi tepat satu: menuId (blokir per item) atau kategoriId (blokir per kategori)");
        }
        OffsetDateTime now = jam.sekarang();

        BlokirItem baris = (adaMenu
                ? itemRepo.findBySekolahIdAndSubjekTipeAndSubjekIdAndMenuId(sekolahId, subjekTipe, subjekId, menuId)
                : itemRepo.findBySekolahIdAndSubjekTipeAndSubjekIdAndKategoriId(sekolahId, subjekTipe, subjekId, kategoriId))
                .orElseGet(() -> BlokirItem.builder()
                        .id(idGenerator.berikutnyaLong())
                        .sekolahId(sekolahId)
                        .subjekTipe(subjekTipe)
                        .subjekId(subjekId)
                        .menuId(menuId)
                        .kategoriId(kategoriId)
                        .createdAt(now)
                        .build());
        boolean lama = baris.isDiblokir();
        baris.setDiblokir(diblokir);
        baris.setDiubahOleh(aktorId);
        baris.setDiubahPada(now);
        itemRepo.save(baris);

        String target = adaMenu ? "menu:" + menuId : "kategori:" + kategoriId;
        auditLogger.catat(aktorId, sekolahId,
                diblokir ? "BLOKIR_ITEM" : "BUKA_BLOKIR_ITEM", "BlokirItem",
                subjekTipe + ":" + subjekId, target,
                String.valueOf(lama), String.valueOf(diblokir));
        log.info("Blokir item {}:{} {} diblokir={} oleh={}",
                subjekTipe, subjekId, target, diblokir, aktorId);
        return kontrol(sekolahId, subjekTipe, subjekId);
    }

    // ────────────────────────────────────────────────────────────────
    // BACA
    // ────────────────────────────────────────────────────────────────

    /** Ringkasan kontrol satu subjek (tenant-scoped). */
    @Transactional(readOnly = true)
    public KontrolSubjekResponse kontrol(Long sekolahId, SubjekTipe subjekTipe, Long subjekId) {
        validasiSubjek(subjekTipe, subjekId);

        BlokirKartu blokir = blokirRepo
                .findBySekolahIdAndSubjekTipeAndSubjekId(sekolahId, subjekTipe, subjekId)
                .orElse(null);
        Long limit = limitRepo
                .findBySekolahIdAndSubjekTipeAndSubjekId(sekolahId, subjekTipe, subjekId)
                .map(LimitHarian::getNominal)
                .orElse(null);

        List<BlokirItem> item = itemRepo.cariAktif(sekolahId, subjekTipe, subjekId);
        List<Long> menuDiblokir = item.stream()
                .map(BlokirItem::getMenuId).filter(java.util.Objects::nonNull).distinct().toList();
        List<Long> kategoriDiblokir = item.stream()
                .map(BlokirItem::getKategoriId).filter(java.util.Objects::nonNull).distinct().toList();

        return KontrolSubjekResponse.builder()
                .subjekTipe(subjekTipe.name())
                .subjekId(subjekId)
                .diblokir(blokir != null && blokir.isDiblokir())
                .alasanBlokir(blokir == null ? null : blokir.getAlasan())
                .limitHarian(limit)
                .adaLimit(limit != null)
                .menuDiblokir(menuDiblokir)
                .kategoriDiblokir(kategoriDiblokir)
                .item(item.stream().map(BlokirItemResponse::dari).toList())
                .build();
    }

    /**
     * Tempelkan kontrol kantin-be (blokir/limit/blokir item) ke identitas kartu.
     *
     * <p>Dipanggil di jalur tap <b>setiap</b> permintaan (tanpa cache — PRD §11.11).
     * Bila kartu tidak dikenal / tanpa subjek, dikembalikan apa adanya. Bila ada
     * baris kontrol, nilai kantin-be <b>menang</b> (sumber kebenaran blokir/limit);
     * bila tidak ada, nilai dari port dibiarkan.
     *
     * <p>Limit &amp; blokir item hanya diterapkan untuk siswa (§9.4).
     */
    @Transactional(readOnly = true)
    public InfoKartu terapkan(Long sekolahId, InfoKartu kartu) {
        if (kartu == null || !kartu.isDikenal()
                || kartu.getSubjekTipe() == null || kartu.getSubjekId() == null) {
            return kartu;
        }
        SubjekTipe tipe = kartu.getSubjekTipe();
        Long subjekId = kartu.getSubjekId();

        // Blokir kartu — berlaku untuk siswa & Kartu Tamu (§9.4 kartu hilang).
        Boolean diblokir = blokirRepo
                .findBySekolahIdAndSubjekTipeAndSubjekId(sekolahId, tipe, subjekId)
                .map(BlokirKartu::isDiblokir)
                .orElse(kartu.isDiblokir());

        Long limit = kartu.getLimitHarian();
        Set<Long> menu = kartu.getMenuDiblokir() == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(kartu.getMenuDiblokir());
        Set<Long> kategori = kartu.getKategoriDiblokir() == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(kartu.getKategoriDiblokir());

        if (kartu.siswa()) {
            // Limit: baris ada ⇒ nilai kantin-be menang (termasuk "tanpa limit").
            var limitBaris = limitRepo.findBySekolahIdAndSubjekTipeAndSubjekId(sekolahId, tipe, subjekId);
            if (limitBaris.isPresent()) {
                limit = limitBaris.get().getNominal();
            }
            // Blokir item/kategori (hanya baris aktif).
            for (BlokirItem b : itemRepo.cariAktif(sekolahId, tipe, subjekId)) {
                if (b.getMenuId() != null) {
                    menu.add(b.getMenuId());
                }
                if (b.getKategoriId() != null) {
                    kategori.add(b.getKategoriId());
                }
            }
        }

        return InfoKartu.builder()
                .dikenal(true)
                .diblokir(diblokir)
                .subjekTipe(tipe)
                .subjekId(subjekId)
                .nama(kartu.getNama())
                .kelas(kartu.getKelas())
                .fotoUrl(kartu.getFotoUrl())
                .limitHarian(limit)
                .menuDiblokir(menu.isEmpty() ? null : menu)
                .kategoriDiblokir(kategori.isEmpty() ? null : kategori)
                .build();
    }

    private void validasiSubjek(SubjekTipe subjekTipe, Long subjekId) {
        if (subjekTipe == null) {
            throw new InvalidOperationException("Tipe subjek wajib diisi (SISWA/KARTU_TAMU)");
        }
        if (subjekId == null) {
            throw new InvalidOperationException("ID subjek wajib diisi");
        }
    }
}
