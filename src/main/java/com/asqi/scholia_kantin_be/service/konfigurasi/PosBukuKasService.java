package com.asqi.scholia_kantin_be.service.konfigurasi;

import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.dto.PosBukuKasResponse;
import com.asqi.scholia_kantin_be.helper.Constants;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.PosBukuKas;
import com.asqi.scholia_kantin_be.repository.PosBukuKasRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Pos Buku Kas kantin — <b>seeding otomatis</b> (DEMO, OPEN-QUESTIONS Q8 / #21).
 *
 * <p><b>Masalah (Q8):</b> belum dipastikan apakah admin-be membuat pos
 * "Pendapatan Kantin" &amp; "Belanja Stok Kantin" otomatis saat modul diaktifkan.
 *
 * <p><b>Solusi demo:</b> kantin-be mendaftarkan sendiri pos standar yang ia
 * butuhkan per sekolah, idempoten lewat UNIQUE {@code (sekolah_id, nama)}.
 * Saat Q8 terjawab, posting tetap lewat {@code BukuKasPort}; tabel ini menjadi
 * rujukan/cache dan seeding dapat dimatikan.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PosBukuKasService {

    /** Pos standar kantin + tipe + keterangan (selaras INTEGRATIONS.md §3.3). */
    private static final String[][] POS_STANDAR = {
            {Constants.KATEGORI_PENDAPATAN_KANTIN, "MASUK",
                    "Pemasukan penjualan kantin (total bersih sesi kasir)"},
            {Constants.KATEGORI_BELANJA_STOK_KANTIN, "KELUAR",
                    "Pengeluaran belanja stok kantin (barang masuk)"},
            {Constants.KATEGORI_PENYESUAIAN_KANTIN, "MASUK",
                    "Entri penyesuaian/koreksi saldo & pembalik barang masuk"},
    };

    private final PosBukuKasRepository posRepo;
    private final IdGenerator idGenerator;
    private final JamKantin jam;
    private final AuditLogger auditLogger;

    /**
     * Pastikan pos standar kantin ada untuk sekolah ini (idempoten).
     *
     * <p>Aman dipanggil berkali-kali: pos yang sudah ada tidak dibuat ulang.
     * Dipanggil saat "aktivasi modul" atau saat pertama posting.
     *
     * @return daftar pos setelah seeding
     */
    @Transactional
    public List<PosBukuKasResponse> pastikanPosStandar(Long sekolahId, Long aktorId) {
        int dibuat = 0;
        for (String[] pos : POS_STANDAR) {
            String nama = pos[0];
            if (posRepo.findBySekolahIdAndNama(sekolahId, nama).isEmpty()) {
                OffsetDateTime now = jam.sekarang();
                posRepo.save(PosBukuKas.builder()
                        .id(idGenerator.berikutnyaLong())
                        .sekolahId(sekolahId)
                        .nama(nama)
                        .tipe(pos[1])
                        .keterangan(pos[2])
                        .aktif(true)
                        .createdAt(now)
                        .build());
                dibuat++;
            }
        }
        if (dibuat > 0) {
            auditLogger.catat(aktorId, sekolahId, "SEED_POS_BUKU_KAS", "PosBukuKas",
                    String.valueOf(sekolahId), "Aktivasi modul kantin",
                    "0", String.valueOf(dibuat));
            log.info("Seeding pos Buku Kas sekolah={} — {} pos baru dibuat.", sekolahId, dibuat);
        }
        return daftar(sekolahId);
    }

    /** Daftar pos satu sekolah (tenant-scoped, PRD §11.4). */
    @Transactional(readOnly = true)
    public List<PosBukuKasResponse> daftar(Long sekolahId) {
        return posRepo.findBySekolahIdOrderByNamaAsc(sekolahId).stream()
                .map(PosBukuKasResponse::dari)
                .toList();
    }
}
