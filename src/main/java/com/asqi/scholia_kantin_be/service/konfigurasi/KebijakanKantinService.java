package com.asqi.scholia_kantin_be.service.konfigurasi;

import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.dto.KebijakanKantinResponse;
import com.asqi.scholia_kantin_be.enums.KebijakanSaldoMengendap;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.KebijakanKantin;
import com.asqi.scholia_kantin_be.repository.KebijakanKantinRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kebijakan kantin per sekolah (DEMO, OPEN-QUESTIONS Q16 / #25).
 *
 * <p><b>Masalah (Q16):</b> belum ada kebijakan untuk saldo mengendap siswa
 * lulus/keluar yang tak diklaim (refund? pindah saudara? dibiarkan?).
 *
 * <p><b>Solusi demo:</b> sekolah menyimpan kebijakannya di sini dengan default
 * aman {@link KebijakanSaldoMengendap#REFUND} (kembalikan ke ortu, PRD §9.3).
 * Fitur refund/pindah saldo (issue #38) tetap jadi mekanisme eksekusinya.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KebijakanKantinService {

    /** Default: kembalikan ke ortu (paling aman & sesuai PRD §9.3). */
    private static final KebijakanSaldoMengendap DEFAULT_KEBIJAKAN = KebijakanSaldoMengendap.REFUND;

    /** Default ambang hari: 90 hari setelah lulus. */
    private static final int DEFAULT_AMBANG_HARI = 90;

    private final KebijakanKantinRepository kebijakanRepo;
    private final JamKantin jam;
    private final AuditLogger auditLogger;

    /**
     * Ambil kebijakan kantin; bila belum ada, kembalikan default (tanpa menyimpan)
     * — sekolah tidak wajib mengisi apa pun agar sistem tetap jalan.
     */
    @Transactional(readOnly = true)
    public KebijakanKantinResponse ambil(Long sekolahId) {
        return kebijakanRepo.findById(sekolahId)
                .map(KebijakanKantinResponse::dari)
                .orElseGet(() -> KebijakanKantinResponse.builder()
                        .sekolahId(sekolahId)
                        .kebijakanSaldoMengendap(DEFAULT_KEBIJAKAN.name())
                        .ambangHari(DEFAULT_AMBANG_HARI)
                        .catatan("Default (belum diubah sekolah)")
                        .build());
    }

    /**
     * Ubah kebijakan kantin (upsert). Perubahan dicatat di audit (PRD §11.7).
     */
    @Transactional
    public KebijakanKantinResponse ubah(Long sekolahId, KebijakanSaldoMengendap kebijakan,
                                        int ambangHari, String catatan, Long aktorId) {
        KebijakanSaldoMengendap baru = (kebijakan == null) ? DEFAULT_KEBIJAKAN : kebijakan;
        int ambang = Math.max(0, ambangHari);

        KebijakanKantin baris = kebijakanRepo.findById(sekolahId)
                .orElseGet(() -> KebijakanKantin.builder()
                        .sekolahId(sekolahId)
                        .build());
        String lama = baris.getKebijakanSaldoMengendap();
        baris.setKebijakanSaldoMengendap(baru.name());
        baris.setAmbangHari(ambang);
        baris.setCatatan(catatan);
        baris.setDiperbaruiOleh(aktorId);
        baris.setUpdatedAt(jam.sekarang());
        kebijakanRepo.save(baris);

        auditLogger.catat(aktorId, sekolahId, "UBAH_KEBIJAKAN_KANTIN", "KebijakanKantin",
                String.valueOf(sekolahId), catatan,
                lama == null ? DEFAULT_KEBIJAKAN.name() : lama, baru.name());
        log.info("Kebijakan kantin sekolah={} diubah → saldoMengendap={} ambangHari={}",
                sekolahId, baru, ambang);
        return KebijakanKantinResponse.dari(baris);
    }
}
