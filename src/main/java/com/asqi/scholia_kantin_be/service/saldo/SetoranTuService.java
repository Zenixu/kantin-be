package com.asqi.scholia_kantin_be.service.saldo;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.dto.RekapSetoranTuItem;
import com.asqi.scholia_kantin_be.dto.SetoranTuResponse;
import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.SetoranTu;
import com.asqi.scholia_kantin_be.repository.SaldoLedgerRepository;
import com.asqi.scholia_kantin_be.repository.SetoranTuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Setoran kas TU harian (PRD §9.2, issue #39).
 *
 * <p><b>Alur:</b> rekap top-up tunai <b>per petugas per hari</b> dari
 * {@code saldo_ledger} (jenis {@code TOPUP_TUNAI}) → bendahara mengonfirmasi
 * uang fisik yang disetor. <b>Selisih kas dicatat, tidak dihapus</b>
 * (append-only): selisih dihitung kolom generated
 * {@code total_topup − jumlah_disetor}, tidak pernah mengubah baris lama.
 *
 * <p>Idempoten lewat {@code referensiId} (nomor berita acara) yang UNIQUE per
 * sekolah — double-submit tidak mencatat setoran dua kali. Semua aksi diaudit
 * (PRD §11.7).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SetoranTuService {

    private final SaldoLedgerRepository ledgerRepo;
    private final SetoranTuRepository setoranRepo;
    private final AuditLogger auditLogger;
    private final IdGenerator idGenerator;
    private final JamKantin jam;

    /**
     * Rekap setoran TU pada satu tanggal: per petugas, total top-up tunai,
     * uang yang sudah dikonfirmasi disetor, dan selisihnya (PRD §9.2).
     *
     * <p>Baca-saja; tenant-scoped (PRD §11.4). {@code tanggal} {@code null} =
     * hari ini menurut zona kantin.
     */
    @Transactional(readOnly = true)
    public List<RekapSetoranTuItem> rekap(Long sekolahId, LocalDate tanggal) {
        LocalDate hari = (tanggal != null) ? tanggal : jam.hariIni();
        OffsetDateTime sejak = hari.atStartOfDay(jam.zona()).toOffsetDateTime();
        OffsetDateTime sampai = sejak.plusDays(1);

        List<SaldoLedgerRepository.RekapTopupPetugas> rekapTopup =
                ledgerRepo.rekapTopupTunaiPerPetugas(sekolahId, JenisMutasiSaldo.TOPUP_TUNAI,
                        ArahMutasi.KREDIT, sejak, sampai);
        List<SetoranTu> setoran = setoranRepo
                .findBySekolahIdAndTanggalOrderByPetugasIdAsc(sekolahId, hari);

        return rekapTopup.stream().map(r -> {
            long total = r.getTotal() == null ? 0L : r.getTotal();
            SetoranTu s = setoran.stream()
                    .filter(x -> x.getPetugasId().equals(r.getPetugasId()))
                    .findFirst().orElse(null);
            long disetor = (s == null || s.getJumlahDisetor() == null) ? 0L : s.getJumlahDisetor();
            return RekapSetoranTuItem.builder()
                    .petugasId(r.getPetugasId())
                    .tanggal(hari)
                    .totalTopup(total)
                    .jumlahDisetor(disetor)
                    .selisih(total - disetor)
                    .referensiId(s == null ? null : s.getReferensiId())
                    .build();
        }).toList();
    }

    /**
     * Konfirmasi setoran kas TU oleh bendahara (PRD §9.2).
     *
     * <p>Mencatat baris {@code setoran_tu} append-only: {@code totalTopup}
     * diambil dari rekap ledger saat konfirmasi (sumber kebenaran), lalu
     * {@code selisih = totalTopup − jumlahDisetor} dihitung DB. Selisih
     * <b>dicatat, tidak dihapus</b>.
     *
     * <p>Idempoten: bila {@code referensiId} sudah pernah dipakai (per sekolah),
     * mengembalikan baris lama tanpa mencatat ulang.
     *
     * @param referensiId nomor berita acara setoran (unik, idempotency)
     * @param aktorId     bendahara yang mengonfirmasi (audit)
     */
    @Transactional
    public SetoranTuResponse konfirmasi(Long sekolahId, LocalDate tanggal, Long petugasId,
                                        long jumlahDisetor, String referensiId, String catatan,
                                        Long aktorId) {
        if (tanggal == null) {
            throw new InvalidOperationException("Tanggal rekap setoran wajib diisi");
        }
        if (petugasId == null) {
            throw new InvalidOperationException("ID petugas TU wajib diisi");
        }
        if (jumlahDisetor < 0) {
            throw new InvalidOperationException("Jumlah disetor tidak boleh negatif");
        }
        if (referensiId == null || referensiId.isBlank()) {
            throw new InvalidOperationException(
                    "Nomor berita acara setoran wajib diisi (idempotency)");
        }

        // Idempotency: berita acara sama (per sekolah) → kembalikan baris lama.
        var lama = setoranRepo.findBySekolahIdAndReferensiId(sekolahId, referensiId);
        if (lama.isPresent()) {
            log.debug("Setoran TU replay referensi={} → id={}", referensiId, lama.get().getId());
            return SetoranTuResponse.dari(lama.get());
        }

        OffsetDateTime sejak = tanggal.atStartOfDay(jam.zona()).toOffsetDateTime();
        OffsetDateTime sampai = sejak.plusDays(1);
        Long total = ledgerRepo.hitungTopupTunaiPetugas(sekolahId, JenisMutasiSaldo.TOPUP_TUNAI,
                ArahMutasi.KREDIT, petugasId, sejak, sampai);
        long totalTopup = total == null ? 0L : total;

        long selisih = totalTopup - jumlahDisetor;
        if (selisih != 0 && (catatan == null || catatan.isBlank())) {
            throw new InvalidOperationException(
                    "Selisih kas Rp" + selisih + " — catatan alasan selisih wajib diisi (PRD §9.2)");
        }

        OffsetDateTime now = jam.sekarang();
        SetoranTu baris = SetoranTu.builder()
                .id(idGenerator.berikutnyaLong())
                .sekolahId(sekolahId)
                .tanggal(tanggal)
                .petugasId(petugasId)
                .totalTopup(totalTopup)
                .jumlahDisetor(jumlahDisetor)
                .referensiId(referensiId)
                .catatan(catatan)
                .dikonfirmasiOleh(aktorId)
                .waktu(now)
                .createdAt(now)
                .build();
        SetoranTu tersimpan = setoranRepo.save(baris);

        auditLogger.catat(aktorId, sekolahId, "KONFIRMASI_SETORAN_TU", "SetoranTu",
                String.valueOf(tersimpan.getId()),
                catatan == null || catatan.isBlank() ? "Setoran sesuai" : catatan,
                String.valueOf(totalTopup), String.valueOf(jumlahDisetor));

        log.info("Setoran TU sekolah={} tanggal={} petugas={} topup={} disetor={} selisih={}",
                sekolahId, tanggal, petugasId, totalTopup, jumlahDisetor, selisih);
        return SetoranTuResponse.dari(tersimpan);
    }
}
