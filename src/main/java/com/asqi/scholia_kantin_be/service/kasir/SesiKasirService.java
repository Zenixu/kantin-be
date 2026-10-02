package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.enums.StatusSesiKasir;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.SesiKasir;
import com.asqi.scholia_kantin_be.repository.SesiKasirRepository;
import com.asqi.scholia_kantin_be.repository.TransaksiRepository;
import com.asqi.scholia_kantin_be.security.SekolahGuard;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service sesi kasir — buka &amp; tutup kasir, rekap penjualan (PRD §6.4).
 *
 * <p>Satu titik kasir hanya punya satu sesi per tanggal (UNIQUE di DB). Sesi
 * baru dibuka otomatis pada transaksi pertama hari berikutnya; sesi lama harus
 * ditutup (manual atau otomatis) agar transaksi terkunci dan total bersih bisa
 * diposting ke Buku Kas.
 *
 * <p><b>Belum termasuk posting Buku Kas</b> — itu bagian
 * {@code service/integrasi} (BukuKasClient) yang menunggu Q3. Service ini hanya
 * menandai {@code posting_buku_kas=false} &amp; menyiapkan angka rekapnya.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SesiKasirService {

    private final SesiKasirRepository sesiRepo;
    private final TransaksiRepository transaksiRepo;
    private final SekolahGuard sekolahGuard;
    private final IdGenerator idGenerator;
    private final JamKantin jam;

    /**
     * Ambil sesi TERBUKA untuk titik kasir hari ini; buka baru bila belum ada.
     *
     * <p>Dipakai jalur tap. Berjalan {@code MANDATORY} agar berada dalam
     * transaksi kasir yang sama.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public SesiKasir sesiTerbukaAtauBuka(Long sekolahId, Long titikKasirId) {
        LocalDate hariIni = jam.hariIni();
        var ada = sesiRepo.kunciBerdasarkanTitikTanggal(sekolahId, titikKasirId, hariIni);
        if (ada.isPresent()) {
            SesiKasir sesi = ada.get();
            if (!sesi.getSekolahId().equals(sekolahId)) {
                throw new NotFoundEntity("Sesi kasir tidak ditemukan");
            }
            if (!sesi.terbuka()) {
                throw new InvalidOperationException("Sesi kasir hari ini sudah ditutup");
            }
            return sesi;
        }
        return buatSesiBaru(sekolahId, titikKasirId, hariIni);
    }

    /** Buka sesi eksplisit (mis. saat petugas mulai shift). */
    @Transactional
    public SesiKasir bukaSesi(Long sekolahId, Long titikKasirId) {
        LocalDate hariIni = jam.hariIni();
        var ada = sesiRepo.kunciBerdasarkanTitikTanggal(sekolahId, titikKasirId, hariIni);
        if (ada.isPresent()) {
            SesiKasir sesi = ada.get();
            if (!sesi.getSekolahId().equals(sekolahId)) {
                throw new NotFoundEntity("Sesi kasir tidak ditemukan");
            }
            if (!sesi.terbuka()) {
                throw new InvalidOperationException("Sesi kasir hari ini sudah ditutup");
            }
            return sesi;
        }
        return buatSesiBaru(sekolahId, titikKasirId, hariIni);
    }

    private SesiKasir buatSesiBaru(Long sekolahId, Long titikKasirId, LocalDate tanggal) {
        OffsetDateTime now = jam.sekarang();
        SesiKasir sesi = SesiKasir.builder()
                .id(idGenerator.berikutnyaLong())
                .sekolahId(sekolahId)
                .titikKasirId(titikKasirId)
                .tanggal(tanggal)
                .status(StatusSesiKasir.TERBUKA)
                .totalBruto(0L)
                .totalVoid(0L)
                .totalBersih(0L)
                .dibukaAt(now)
                .autoTutup(false)
                .postingBukuKas(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        log.info("Buka sesi kasir titik={} tanggal={}", titikKasirId, tanggal);
        return sesiRepo.save(sesi);
    }

    /**
     * Hitung rekap sesi tanpa menutupnya (pratinjau tutup kasir, PRD §6.4).
     *
     * @return rekap bruto/void/bersih
     */
    @Transactional(readOnly = true)
    public RekapSesi rekap(Long sekolahId, Long sesiId) {
        SesiKasir sesi = sesiRepo.findById(sesiId)
                .orElseThrow(() -> new NotFoundEntity("Sesi kasir tidak ditemukan"));
        sekolahGuard.pastikanMilikSekolah(sesi.getSekolahId(), "Sesi kasir");
        if (!sesi.getSekolahId().equals(sekolahId)) {
            throw new NotFoundEntity("Sesi kasir tidak ditemukan");
        }
        return hitungRekap(sekolahId, sesi);
    }

    /**
     * Tutup sesi: hitung rekap, tandai DITUTUP, kunci transaksi sesi ini
     * (PRD §6.4). Setelah ditutup, void tidak lagi diizinkan pada sesi ini.
     *
     * @param oleh user penutup (audit)
     * @param auto true bila ditutup otomatis oleh sistem (mis. 23:59)
     */
    @Transactional
    public SesiKasir tutupSesi(Long sekolahId, Long sesiId, Long oleh, boolean auto) {
        SesiKasir sesi = sesiRepo.kunciUntukUpdate(sekolahId, sesiId)
                .orElseThrow(() -> new NotFoundEntity("Sesi kasir tidak ditemukan"));
        sekolahGuard.pastikanMilikSekolah(sesi.getSekolahId(), "Sesi kasir");
        if (!sesi.getSekolahId().equals(sekolahId)) {
            throw new NotFoundEntity("Sesi kasir tidak ditemukan");
        }
        if (!sesi.terbuka()) {
            throw new InvalidOperationException("Sesi kasir sudah ditutup");
        }

        RekapSesi rekap = hitungRekap(sekolahId, sesi);
        OffsetDateTime now = jam.sekarang();

        sesi.setTotalBruto(rekap.getTotalBruto());
        sesi.setTotalVoid(rekap.getTotalVoid());
        sesi.setTotalBersih(rekap.getTotalBersih());
        sesi.setStatus(StatusSesiKasir.DITUTUP);
        sesi.setDitutupAt(now);
        sesi.setDitutupOleh(oleh);
        sesi.setAutoTutup(auto);
        sesi.setUpdatedAt(now);

        log.info("Tutup sesi kasir id={} bersih={} void={} auto={}",
                sesiId, rekap.getTotalBersih(), rekap.getTotalVoid(), auto);
        return sesiRepo.save(sesi);
    }

    /**
     * Auto-tutup semua sesi terbuka yang tertinggal (mis. dijalankan scheduler
     * pada jam yang dikonfigurasi sekolah, default 23:59 — PRD §6.4).
     *
     * @return jumlah sesi yang ditutup
     */
    @Transactional
    public int tutupOtomatis(Long sekolahId) {
        List<SesiKasir> terbuka = sesiRepo.findBySekolahIdAndStatus(sekolahId, StatusSesiKasir.TERBUKA);
        int jumlah = 0;
        for (SesiKasir sesi : terbuka) {
            RekapSesi rekap = hitungRekap(sekolahId, sesi);
            OffsetDateTime now = jam.sekarang();
            sesi.setTotalBruto(rekap.getTotalBruto());
            sesi.setTotalVoid(rekap.getTotalVoid());
            sesi.setTotalBersih(rekap.getTotalBersih());
            sesi.setStatus(StatusSesiKasir.DITUTUP);
            sesi.setDitutupAt(now);
            sesi.setAutoTutup(true);
            sesi.setUpdatedAt(now);
            sesiRepo.save(sesi);
            jumlah++;
        }
        if (jumlah > 0) {
            log.info("Auto-tutup {} sesi kasir sekolah={}", jumlah, sekolahId);
        }
        return jumlah;
    }

    @Transactional(readOnly = true)
    public SesiKasir ambil(Long sekolahId, Long sesiId) {
        SesiKasir sesi = sesiRepo.findById(sesiId)
                .orElseThrow(() -> new NotFoundEntity("Sesi kasir tidak ditemukan"));
        sekolahGuard.pastikanMilikSekolah(sesi.getSekolahId(), "Sesi kasir");
        if (!sesi.getSekolahId().equals(sekolahId)) {
            throw new NotFoundEntity("Sesi kasir tidak ditemukan");
        }
        return sesi;
    }

    private RekapSesi hitungRekap(Long sekolahId, SesiKasir sesi) {
        long brutoSukses = 0L;
        long brutoVoid = 0L;
        long jumlahSukses = 0L;
        long jumlahVoid = 0L;

        for (Object[] baris : transaksiRepo.rekapSesi(sekolahId, sesi.getId())) {
            StatusTransaksi status = (StatusTransaksi) baris[0];
            long jumlah = ((Number) baris[1]).longValue();
            long total = ((Number) baris[2]).longValue();
            if (status == StatusTransaksi.SUKSES) {
                brutoSukses = total;
                jumlahSukses = jumlah;
            } else if (status == StatusTransaksi.VOID) {
                brutoVoid = total;
                jumlahVoid = jumlah;
            }
        }

        return RekapSesi.builder()
                .sesiKasirId(sesi.getId())
                .jumlahTransaksi(jumlahSukses)
                .jumlahVoid(jumlahVoid)
                .totalBersih(brutoSukses)
                .totalVoid(brutoVoid)
                .totalBruto(brutoSukses + brutoVoid)
                .build();
    }

    /**
     * Rekap sesi kasir (PRD §6.4): bruto = bersih + void.
     *
     * <p>{@code totalBersih} = Σ transaksi SUKSES — inilah yang diposting ke
     * Buku Kas pos "Pendapatan Kantin" saat tutup kasir (PRD §5.1).
     */
    @Getter
    @Builder
    public static class RekapSesi {
        private final Long sesiKasirId;
        private final long jumlahTransaksi;
        private final long jumlahVoid;
        private final long totalBruto;
        private final long totalVoid;
        private final long totalBersih;
    }
}
