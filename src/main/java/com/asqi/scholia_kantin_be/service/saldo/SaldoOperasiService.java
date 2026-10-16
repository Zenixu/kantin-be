package com.asqi.scholia_kantin_be.service.saldo;

import com.asqi.scholia_kantin_be.dto.HalamanResponse;
import com.asqi.scholia_kantin_be.dto.MutasiKoreksiItem;
import com.asqi.scholia_kantin_be.dto.MutasiSaldoItem;
import com.asqi.scholia_kantin_be.dto.SaldoResponse;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.KartuTamu;
import com.asqi.scholia_kantin_be.model.SaldoLedger;
import com.asqi.scholia_kantin_be.repository.KartuTamuRepository;
import com.asqi.scholia_kantin_be.repository.SaldoLedgerRepository;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Facade baca saldo untuk HTTP: menggabungkan saldo berjalan, belanja hari ini,
 * dan mutasi terbaru menjadi satu {@link SaldoResponse} (PRD §9.3), serta
 * menyediakan <b>daftar koreksi bendahara</b> (PRD §9.2; issue #213).
 *
 * <p>Baca-saja; tidak memodifikasi ledger. Tetap tenant-scoped lewat
 * {@link LedgerSaldoService}.
 */
@Service
@RequiredArgsConstructor
public class SaldoOperasiService {

    private static final int MAKS_MUTASI = 50;
    /** Batas atas ukuran halaman — sama dengan pola daftar lain. */
    private static final int UKURAN_MAKS = 200;
    private static final int UKURAN_DEFAULT = 20;

    private final LedgerSaldoService ledgerSaldo;
    private final SaldoLedgerRepository ledgerRepo;
    private final KartuTamuRepository kartuTamuRepo;
    private final JamKantin jam;

    /** Ringkasan saldo + belanja hari ini + mutasi terbaru. */
    @Transactional(readOnly = true)
    public SaldoResponse lihat(Long sekolahId, SubjekTipe subjekTipe, Long subjekId, int batasMutasi) {
        int batas = (batasMutasi <= 0 || batasMutasi > MAKS_MUTASI) ? 20 : batasMutasi;
        long saldo = ledgerSaldo.saldo(sekolahId, subjekTipe, subjekId);
        long belanjaHariIni = (subjekTipe == SubjekTipe.SISWA)
                ? ledgerSaldo.belanjaHariIni(sekolahId, subjekTipe, subjekId)
                : 0L;

        List<MutasiSaldoItem> mutasi = ledgerSaldo
                .riwayatTerbaru(sekolahId, subjekTipe, subjekId, batas)
                .stream()
                .map(MutasiSaldoItem::dari)
                .toList();

        return SaldoResponse.builder()
                .subjekTipe(subjekTipe)
                .subjekId(subjekId)
                .saldo(saldo)
                .belanjaHariIni(belanjaHariIni)
                .mutasiTerbaru(mutasi)
                .build();
    }

    /** Hitung ulang saldo dari ledger — verifikasi/rekonsiliasi cache. */
    @Transactional(readOnly = true)
    public long hitungUlangDariLedger(Long sekolahId, SubjekTipe subjekTipe, Long subjekId) {
        return ledgerSaldo.hitungUlangDariLedger(sekolahId, subjekTipe, subjekId);
    }

    /**
     * <b>Daftar mutasi koreksi saldo</b> (PRD §9.2; issue #213) — bendahara
     * perlu <i>membaca kembali</i> koreksi yang pernah dilakukan. Hanya mutasi
     * berjenis {@link JenisMutasiSaldo#KOREKSI}, terbaru dulu, berhalaman.
     *
     * <p>Data koreksi tidak pernah diedit/dihapus (PRD §9.2), jadi daftar ini
     * murni proyeksi ledger.
     *
     * @param sekolahId  tenant dari token (wajib)
     * @param tanggal    filter satu hari menurut zona sekolah (opsional)
     * @param subjekTipe filter jenis subjek, mis. {@code SISWA}/{@code KARTU_TAMU} (opsional)
     * @param subjekId   filter subjek tertentu, dipakai bersama {@code subjekTipe} (opsional)
     */
    @Transactional(readOnly = true)
    public HalamanResponse<MutasiKoreksiItem> daftarKoreksi(Long sekolahId, LocalDate tanggal,
                                                            SubjekTipe subjekTipe, Long subjekId,
                                                            int halaman, int ukuran) {
        int ukuranAman = (ukuran <= 0 || ukuran > UKURAN_MAKS) ? UKURAN_DEFAULT : ukuran;
        int halamanAman = Math.max(0, halaman);
        Pageable pageable = PageRequest.of(halamanAman, ukuranAman,
                Sort.by(Sort.Direction.DESC, "waktu").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<SaldoLedger> page = ledgerRepo.findAll(
                filterKoreksi(sekolahId, tanggal, subjekTipe, subjekId), pageable);

        // Nama subjek (kartu tamu) diambil BATCH — hindari N+1.
        Map<Long, String> namaSubjek = namaPemegangKartu(sekolahId, page.getContent());

        return HalamanResponse.<SaldoLedger, MutasiKoreksiItem>dari(page, l -> {
            MutasiKoreksiItem item = MutasiKoreksiItem.dari(l);
            if (l.getSubjekTipe() == SubjekTipe.KARTU_TAMU) {
                item.setSubjekNama(namaSubjek.get(l.getSubjekId()));
            }
            return item;
        });
    }

    // ────────────────────────────────────────────────────────────────
    // HELPER
    // ────────────────────────────────────────────────────────────────

    /**
     * Predicate daftar koreksi (Criteria API) — <b>hanya</b> menambahkan syarat
     * untuk filter yang terisi.
     *
     * <p>Kenapa bukan JPQL {@code :x IS NULL OR ...}: pola itu membuat Hibernate
     * mengirim parameter {@code null} tanpa tipe dan PostgreSQL menolaknya
     * ({@code could not determine data type of parameter}) — akar bug #198
     * (endpoint daftar selalu 500).
     */
    private Specification<SaldoLedger> filterKoreksi(Long sekolahId, LocalDate tanggal,
                                                     SubjekTipe subjekTipe, Long subjekId) {
        return (root, query, cb) -> {
            List<Predicate> syarat = new ArrayList<>();
            syarat.add(cb.equal(root.get("sekolahId"), sekolahId));              // tenant-scoped
            syarat.add(cb.equal(root.get("jenis"), JenisMutasiSaldo.KOREKSI));   // inti endpoint
            if (subjekTipe != null) {
                syarat.add(cb.equal(root.get("subjekTipe"), subjekTipe));
            }
            if (subjekId != null) {
                syarat.add(cb.equal(root.get("subjekId"), subjekId));
            }
            if (tanggal != null) {
                // Batas hari menurut zona sekolah (JamKantin), bukan zona server.
                OffsetDateTime awal = tanggal.atStartOfDay(jam.zona()).toOffsetDateTime();
                syarat.add(cb.greaterThanOrEqualTo(root.get("waktu"), awal));
                syarat.add(cb.lessThan(root.get("waktu"), awal.plusDays(1)));
            }
            return cb.and(syarat.toArray(new Predicate[0]));
        };
    }

    /** Peta {@code subjekId → label pemegang kartu} untuk baris KARTU_TAMU di halaman ini. */
    private Map<Long, String> namaPemegangKartu(Long sekolahId, List<SaldoLedger> baris) {
        Set<Long> ids = baris.stream()
                .filter(l -> l.getSubjekTipe() == SubjekTipe.KARTU_TAMU)
                .map(SaldoLedger::getSubjekId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return kartuTamuRepo.findAllById(ids).stream()
                .filter(k -> k.getSekolahId().equals(sekolahId))
                .collect(Collectors.toMap(KartuTamu::getId, SaldoOperasiService::labelKartu, (a, b) -> a));
    }

    private static String labelKartu(KartuTamu k) {
        String label = k.getLabelPemegang();
        return (label == null || label.isBlank()) ? k.getNomorKartu() : label;
    }
}
