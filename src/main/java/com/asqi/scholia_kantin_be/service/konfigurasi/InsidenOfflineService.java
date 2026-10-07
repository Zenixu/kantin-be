package com.asqi.scholia_kantin_be.service.konfigurasi;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.dto.InsidenOfflineResponse;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.InsidenOffline;
import com.asqi.scholia_kantin_be.repository.InsidenOfflineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Insiden offline kasir — <b>prosedur darurat</b> (DEMO, OPEN-QUESTIONS Q14 / #23).
 *
 * <p><b>Masalah (Q14):</b> saat internet/server mati, kantin 100% cashless tak
 * bisa jualan. Butuh prosedur darurat + data untuk memutuskan apakah mode
 * offline penuh perlu dimajukan.
 *
 * <p><b>Solusi demo (selaras ADR-0006):</b> kantin menampilkan banner offline
 * (PRD §6.5) dan petugas mencatat <b>insiden</b> (waktu mulai, titik kasir,
 * kronologi). Saat pulih, insiden ditutup dengan durasi. Append-only
 * (PRD §11.1) — insiden tidak diedit/dihapus.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InsidenOfflineService {

    private final InsidenOfflineRepository insidenRepo;
    private final IdGenerator idGenerator;
    private final JamKantin jam;
    private final AuditLogger auditLogger;

    /**
     * Catat insiden offline baru (mulai berlangsung).
     *
     * @param keterangan kronologi singkat (wajib)
     * @return insiden tersimpan
     */
    @Transactional
    public InsidenOfflineResponse lapor(Long sekolahId, Long titikKasirId, String keterangan,
                                        Long aktorId) {
        if (keterangan == null || keterangan.isBlank()) {
            throw new InvalidOperationException("Keterangan insiden wajib diisi");
        }
        OffsetDateTime now = jam.sekarang();
        InsidenOffline baris = InsidenOffline.builder()
                .id(idGenerator.berikutnyaLong())
                .sekolahId(sekolahId)
                .titikKasirId(titikKasirId)
                .mulai(now)
                .keterangan(keterangan)
                .dilaporkanOleh(aktorId)
                .createdAt(now)
                .build();
        InsidenOffline tersimpan = insidenRepo.save(baris);

        auditLogger.catat(aktorId, sekolahId, "LAPOR_INSIDEN_OFFLINE", "InsidenOffline",
                String.valueOf(tersimpan.getId()), keterangan, null, "MULAI");
        log.warn("Insiden offline dicatat sekolah={} titik={} id={}",
                sekolahId, titikKasirId, tersimpan.getId());
        return InsidenOfflineResponse.dari(tersimpan);
    }

    /**
     * Daftar insiden satu sekolah, terbaru dulu (tenant-scoped).
     */
    @Transactional(readOnly = true)
    public List<InsidenOfflineResponse> daftar(Long sekolahId) {
        return insidenRepo.findBySekolahIdOrderByMulaiDesc(sekolahId).stream()
                .map(InsidenOfflineResponse::dari)
                .toList();
    }

    /**
     * Hitung durasi insiden (menit) dari {@code mulai} ke {@code selesai}.
     * Dipakai tampilan rekap; tidak mengubah baris (append-only).
     */
    static Integer durasiMenit(OffsetDateTime mulai, OffsetDateTime selesai) {
        if (mulai == null || selesai == null) {
            return null;
        }
        return (int) Duration.between(mulai, selesai).toMinutes();
    }
}
