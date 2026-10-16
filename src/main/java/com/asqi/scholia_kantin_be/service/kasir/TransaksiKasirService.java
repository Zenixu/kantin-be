package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.dto.HalamanResponse;
import com.asqi.scholia_kantin_be.dto.TransaksiKasirItem;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.KartuTamu;
import com.asqi.scholia_kantin_be.model.TitikKasir;
import com.asqi.scholia_kantin_be.model.Transaksi;
import com.asqi.scholia_kantin_be.repository.KartuTamuRepository;
import com.asqi.scholia_kantin_be.repository.TitikKasirRepository;
import com.asqi.scholia_kantin_be.repository.TransaksiRepository;
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
 * Sisi <b>baca</b> transaksi kasir — daftar transaksi untuk koreksi bendahara
 * (PRD §6.3, §9.2; issue <b>#212</b>).
 *
 * <p><b>Latar.</b> Aksi koreksi sudah ada
 * ({@code POST /api/kasir/transaksi/{transaksiId}/koreksi}), tetapi bendahara
 * tidak punya cara menemukan {@code transaksiId}: FE memanggil endpoint daftar
 * yang belum ada ({@code GET /api/transaksi/sesi-tutup},
 * {@code GET /api/v1/kasir/transaksi-lampau}) dan menerima <b>404</b>. Kelas ini
 * menutup celah tersebut.
 *
 * <p><b>Tenant-scoped</b> — selalu difilter {@code sekolah_id} dari token
 * (PRD §11.4), bukan dari parameter klien.
 *
 * <p><b>Filter dinamis via Criteria API</b>, bukan JPQL
 * {@code :x IS NULL OR ...}: pola terakhir membuat Hibernate mengirim parameter
 * {@code null} tanpa tipe dan PostgreSQL menolaknya
 * ({@code could not determine data type of parameter}) — akar bug #198
 * (endpoint daftar selalu 500). Lihat juga {@code RiwayatStokIT}.
 */
@Service
@RequiredArgsConstructor
public class TransaksiKasirService {

    /** Batas atas ukuran halaman — sama dengan pola {@code /api/stok/riwayat}. */
    private static final int UKURAN_MAKS = 200;
    private static final int UKURAN_DEFAULT = 20;

    private final TransaksiRepository transaksiRepo;
    private final TitikKasirRepository titikKasirRepo;
    private final KartuTamuRepository kartuTamuRepo;
    private final JamKantin jam;

    /**
     * Daftar transaksi satu sekolah, terbaru dulu, berhalaman.
     *
     * @param sekolahId tenant dari token (wajib)
     * @param sesiId    filter sesi kasir (opsional)
     * @param tanggal   filter satu hari menurut zona sekolah (opsional)
     * @param status    filter {@code SUKSES}/{@code VOID} (opsional)
     */
    @Transactional(readOnly = true)
    public HalamanResponse<TransaksiKasirItem> daftar(Long sekolahId, Long sesiId, LocalDate tanggal,
                                                      StatusTransaksi status, int halaman, int ukuran) {
        int ukuranAman = (ukuran <= 0 || ukuran > UKURAN_MAKS) ? UKURAN_DEFAULT : ukuran;
        int halamanAman = Math.max(0, halaman);
        Pageable pageable = PageRequest.of(halamanAman, ukuranAman,
                Sort.by(Sort.Direction.DESC, "waktu").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<Transaksi> page = transaksiRepo.findAll(
                filter(sekolahId, sesiId, tanggal, status), pageable);

        // Nama untuk tampilan — diambil BATCH (satu query per jenis), bukan per baris.
        Map<Long, String> namaTitik = namaTitikKasir(sekolahId, page.getContent());
        Map<Long, String> namaSubjek = namaPemegangKartu(sekolahId, page.getContent());

        return HalamanResponse.<Transaksi, TransaksiKasirItem>dari(page, t -> {
            TransaksiKasirItem item = TransaksiKasirItem.dari(t);
            item.setTitikKasirNama(namaTitik.get(t.getTitikKasirId()));
            if (t.getSubjekTipe() == SubjekTipe.KARTU_TAMU) {
                item.setSubjekNama(namaSubjek.get(t.getSubjekId()));
            }
            return item;
        });
    }

    // ────────────────────────────────────────────────────────────────
    // HELPER
    // ────────────────────────────────────────────────────────────────

    /**
     * Predicate filter daftar transaksi (Criteria API) — <b>hanya</b> menambahkan
     * syarat untuk filter yang benar-benar terisi.
     *
     * <p>Kenapa bukan JPQL {@code :x IS NULL OR ...}: lihat javadoc kelas
     * (bug #198 — PostgreSQL menolak parameter {@code null} tanpa tipe).
     */
    private Specification<Transaksi> filter(Long sekolahId, Long sesiId,
                                            LocalDate tanggal, StatusTransaksi status) {
        return (root, query, cb) -> {
            List<Predicate> syarat = new ArrayList<>();
            syarat.add(cb.equal(root.get("sekolahId"), sekolahId));   // tenant-scoped
            if (sesiId != null) {
                syarat.add(cb.equal(root.get("sesiKasirId"), sesiId));
            }
            if (status != null) {
                syarat.add(cb.equal(root.get("status"), status));
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

    /** Peta {@code titikKasirId → nama} untuk baris di halaman ini (satu query). */
    private Map<Long, String> namaTitikKasir(Long sekolahId, List<Transaksi> baris) {
        Set<Long> ids = baris.stream()
                .map(Transaksi::getTitikKasirId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return titikKasirRepo.findAllById(ids).stream()
                .filter(t -> t.getSekolahId().equals(sekolahId))
                .collect(Collectors.toMap(TitikKasir::getId, TitikKasir::getNama, (a, b) -> a));
    }

    /**
     * Peta {@code kartuTamuId → label pemegang} untuk subjek {@code KARTU_TAMU}
     * di halaman ini (satu query). Label kosong jatuh ke nomor kartu.
     */
    private Map<Long, String> namaPemegangKartu(Long sekolahId, List<Transaksi> baris) {
        Set<Long> ids = baris.stream()
                .filter(t -> t.getSubjekTipe() == SubjekTipe.KARTU_TAMU)
                .map(Transaksi::getSubjekId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return kartuTamuRepo.findAllById(ids).stream()
                .filter(k -> k.getSekolahId().equals(sekolahId))
                .collect(Collectors.toMap(KartuTamu::getId, TransaksiKasirService::labelKartu, (a, b) -> a));
    }

    private static String labelKartu(KartuTamu k) {
        String label = k.getLabelPemegang();
        return (label == null || label.isBlank()) ? k.getNomorKartu() : label;
    }
}
