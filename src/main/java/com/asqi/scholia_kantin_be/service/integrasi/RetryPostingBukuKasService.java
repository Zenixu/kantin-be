package com.asqi.scholia_kantin_be.service.integrasi;

import com.asqi.scholia_kantin_be.enums.StatusSesiKasir;
import com.asqi.scholia_kantin_be.model.SesiKasir;
import com.asqi.scholia_kantin_be.repository.SesiKasirRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retry massal posting Buku Kas untuk sesi yang tertunggak (issue #33).
 *
 * <p>Posting saat tutup kasir bersifat <b>fail-open</b>: bila integrasi
 * admin-be gagal, sesi tetap DITUTUP dengan {@code posting_buku_kas=false}
 * sehingga entri Buku Kas belum terkirim. Kelas ini menyapu semua sesi
 * tertunggak dan mencoba memposting ulang — dipanggil penjadwal berkala
 * ({@link com.asqi.scholia_kantin_be.scheduler.RetryPostingBukuKasScheduler}).
 *
 * <p><b>Kenapa service terpisah (bukan method di {@code BukuKasPostingService}):</b>
 * tiap sesi diposting lewat panggilan <b>lintas-bean</b> ke
 * {@link BukuKasPostingService#postingUlangSistem}, sehingga proxy Spring
 * menerapkan {@code @Transactional} per sesi — satu sesi gagal tidak
 * me-rollback sesi lain (pola sama seperti auto-tutup lintas-tenant).
 * Bila digabung dalam satu method, seluruh sweep jadi satu transaksi dan
 * kegagalan satu entri akan membatalkan yang lain.
 *
 * <p><b>Idempoten &amp; tenant-safe:</b> sesi yang sudah terposting dilewati
 * ({@code postingSesi} memeriksa flag); tiap sesi memakai {@code sekolah_id}-nya
 * sendiri dari baris sesi, bukan konteks global.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetryPostingBukuKasService {

    private final SesiKasirRepository sesiRepo;
    private final BukuKasPostingService bukuKasPosting;

    /**
     * Sapu &amp; posting ulang semua sesi DITUTUP yang belum terposting.
     *
     * @return ringkasan hasil satu kali sweep
     */
    public HasilSweep postingTertunggak() {
        List<SesiKasir> tertunggak = sesiRepo.cariBelumTerposting(StatusSesiKasir.DITUTUP);
        if (tertunggak.isEmpty()) {
            log.debug("Retry posting Buku Kas: tidak ada entri tertunggak");
            return new HasilSweep(0, 0, 0, 0);
        }

        int sukses = 0;
        int gagal = 0;
        int dilewati = 0;
        for (SesiKasir sesi : tertunggak) {
            try {
                // Lintas-bean → transaksi sendiri per sesi (lihat catatan kelas).
                HasilPostingBukuKas hasil =
                        bukuKasPosting.postingUlangSistem(sesi.getSekolahId(), sesi.getId());
                if (hasil.sukses()) {
                    sukses++;
                } else if (hasil.dilewati()) {
                    dilewati++;
                } else {
                    gagal++;
                }
            } catch (RuntimeException e) {
                // Satu sesi gagal jangan menggagalkan sesi lain — dicoba lagi nanti.
                gagal++;
                log.error("Retry posting Buku Kas gagal sesi={} sekolah={}: {}",
                        sesi.getId(), sesi.getSekolahId(), e.getMessage(), e);
            }
        }

        // Metrik/log jumlah entri tertunggak (issue #33). Log terstruktur agar
        // mudah diagregasi tanpa menambah dependensi metrik baru.
        Map<String, Object> ringkas = new LinkedHashMap<>();
        ringkas.put("sweep", "retryPostingBukuKas");
        ringkas.put("tertunggak", tertunggak.size());
        ringkas.put("sukses", sukses);
        ringkas.put("gagal", gagal);
        ringkas.put("dilewati", dilewati);
        if (gagal > 0) {
            log.warn("SWEEP {}", ringkas);
        } else {
            log.info("SWEEP {}", ringkas);
        }
        return new HasilSweep(tertunggak.size(), sukses, gagal, dilewati);
    }

    /** Ringkasan satu kali sweep retry posting Buku Kas (issue #33). */
    public record HasilSweep(int tertunggak, int sukses, int gagal, int dilewati) {
    }
}
