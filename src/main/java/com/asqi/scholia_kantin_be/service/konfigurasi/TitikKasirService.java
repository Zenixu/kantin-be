package com.asqi.scholia_kantin_be.service.konfigurasi;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.dto.TitikKasirResponse;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.TitikKasir;
import com.asqi.scholia_kantin_be.repository.SesiKasirRepository;
import com.asqi.scholia_kantin_be.repository.TitikKasirRepository;
import com.asqi.scholia_kantin_be.enums.StatusSesiKasir;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * CRUD titik kasir (PRD §9.1, §6.6, issue <b>#42</b>).
 *
 * <p>Satu kantin bisa punya banyak titik kasir. Titik dinonaktifkan (soft delete)
 * agar riwayat sesi/transaksi tetap terjaga — titik dengan sesi terbuka tidak
 * boleh dinonaktifkan. Semua tenant-scoped (PRD §11.4); perubahan dicatat di
 * audit (§11.7).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TitikKasirService {

    private final TitikKasirRepository repo;
    private final SesiKasirRepository sesiRepo;
    private final IdGenerator idGenerator;
    private final JamKantin jam;
    private final AuditLogger auditLogger;

    /** Daftar titik kasir satu sekolah (tenant-scoped). */
    @Transactional(readOnly = true)
    public List<TitikKasirResponse> daftar(Long sekolahId, boolean hanyaAktif) {
        List<TitikKasir> titik = hanyaAktif
                ? repo.findBySekolahIdAndAktifTrue(sekolahId)
                : repo.findBySekolahId(sekolahId);
        return titik.stream().map(TitikKasirResponse::dari).toList();
    }

    /** Detail satu titik kasir (sekolah lain ⇒ 404). */
    @Transactional(readOnly = true)
    public TitikKasirResponse detail(Long sekolahId, Long titikId) {
        return TitikKasirResponse.dari(ambilMilikSekolah(sekolahId, titikId));
    }

    /** Buat titik kasir baru. */
    @Transactional
    public TitikKasirResponse buat(Long sekolahId, String nama, String kode, Long aktorId) {
        if (nama == null || nama.isBlank()) {
            throw new InvalidOperationException("Nama titik kasir wajib diisi");
        }
        String kodeBersih = (kode == null || kode.isBlank()) ? null : kode.trim();
        if (kodeBersih != null && repo.existsBySekolahIdAndKode(sekolahId, kodeBersih)) {
            throw new ConflictException("Kode titik kasir " + kodeBersih + " sudah digunakan");
        }
        OffsetDateTime now = jam.sekarang();
        TitikKasir titik = TitikKasir.builder()
                .id(idGenerator.berikutnyaLong())
                .sekolahId(sekolahId)
                .nama(nama.trim())
                .kode(kodeBersih)
                .aktif(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        repo.save(titik);

        auditLogger.catat(aktorId, sekolahId, "BUAT_TITIK_KASIR", "TitikKasir",
                String.valueOf(titik.getId()), nama.trim(), null, "AKTIF");
        log.info("Titik kasir dibuat sekolah={} id={} nama={}", sekolahId, titik.getId(), nama);
        return TitikKasirResponse.dari(titik);
    }

    /** Ubah titik kasir (nama, kode, status aktif). */
    @Transactional
    public TitikKasirResponse ubah(Long sekolahId, Long titikId, String nama, String kode,
                                   Boolean aktif, Long aktorId) {
        TitikKasir titik = ambilMilikSekolah(sekolahId, titikId);
        String lama = "nama=" + titik.getNama() + ";kode=" + titik.getKode() + ";aktif=" + titik.isAktif();

        if (nama != null && !nama.isBlank()) {
            titik.setNama(nama.trim());
        }
        if (kode != null) {
            String kodeBersih = kode.isBlank() ? null : kode.trim();
            boolean berubah = kodeBersih == null
                    ? titik.getKode() != null
                    : !kodeBersih.equals(titik.getKode());
            if (berubah && kodeBersih != null && repo.existsBySekolahIdAndKode(sekolahId, kodeBersih)) {
                throw new ConflictException("Kode titik kasir " + kodeBersih + " sudah digunakan");
            }
            titik.setKode(kodeBersih);
        }
        if (aktif != null) {
            if (!aktif) {
                pastikanTakAdaSesiTerbuka(sekolahId, titikId);
            }
            titik.setAktif(aktif);
        }
        titik.setUpdatedAt(jam.sekarang());
        repo.save(titik);

        String baru = "nama=" + titik.getNama() + ";kode=" + titik.getKode() + ";aktif=" + titik.isAktif();
        auditLogger.catat(aktorId, sekolahId, "UBAH_TITIK_KASIR", "TitikKasir",
                String.valueOf(titikId), "Ubah titik kasir", lama, baru);
        log.info("Titik kasir id={} sekolah={} diperbarui", titikId, sekolahId);
        return TitikKasirResponse.dari(titik);
    }

    /** Nonaktifkan titik kasir (soft delete) — titik dengan sesi terbuka ditolak. */
    @Transactional
    public TitikKasirResponse nonaktifkan(Long sekolahId, Long titikId, Long aktorId) {
        return ubah(sekolahId, titikId, null, null, false, aktorId);
    }

    private void pastikanTakAdaSesiTerbuka(Long sekolahId, Long titikId) {
        boolean adaTerbuka = !sesiRepo
                .findBySekolahIdAndStatus(sekolahId, StatusSesiKasir.TERBUKA).stream()
                .filter(s -> titikId.equals(s.getTitikKasirId()))
                .toList().isEmpty();
        if (adaTerbuka) {
            throw new ConflictException(
                    "Titik kasir masih punya sesi terbuka — tutup kasir dulu sebelum menonaktifkan");
        }
    }

    private TitikKasir ambilMilikSekolah(Long sekolahId, Long titikId) {
        return repo.findByIdAndSekolahId(titikId, sekolahId)
                .orElseThrow(() -> new NotFoundEntity("Titik kasir tidak ditemukan"));
    }
}
