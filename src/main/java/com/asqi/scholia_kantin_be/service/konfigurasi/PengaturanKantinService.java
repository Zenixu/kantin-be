package com.asqi.scholia_kantin_be.service.konfigurasi;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.dto.PengaturanKantinRequest;
import com.asqi.scholia_kantin_be.dto.PengaturanKantinResponse;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.SekolahKantinConfig;
import com.asqi.scholia_kantin_be.repository.SekolahKantinConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Pengaturan kantin per sekolah (PRD §9.1, issue <b>#42</b>).
 *
 * <p>Menyimpan nama kantin, jam tutup kasir otomatis (dipakai penjadwal
 * auto-close §6.4), langkah konfirmasi manual, durasi foto, min/maks top-up,
 * serta batas saldo maksimum per siswa &amp; per Kartu Tamu.
 *
 * <p><b>Default aman:</b> bila sekolah belum pernah menyimpan, {@link #ambil}
 * mengembalikan default (tanpa menyimpan) — jam tutup 23:59, konfirmasi manual
 * nonaktif, durasi foto 3 detik (PRD §6.1/§6.4). Perubahan dicatat di audit
 * (§11.7).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PengaturanKantinService {

    /** Default jam tutup kasir otomatis (PRD §6.4). */
    public static final LocalTime DEFAULT_JAM_TUTUP = LocalTime.of(23, 59);
    /** Default durasi tampil foto setelah transaksi (PRD §6.1). */
    public static final int DEFAULT_DURASI_FOTO_DETIK = 3;

    private final SekolahKantinConfigRepository repo;
    private final JamKantin jam;
    private final AuditLogger auditLogger;

    /**
     * Ambil pengaturan kantin; bila belum ada, kembalikan default aman
     * (tanpa menyimpan) — sekolah tidak wajib mengisi apa pun.
     */
    @Transactional(readOnly = true)
    public PengaturanKantinResponse ambil(Long sekolahId) {
        return repo.findById(sekolahId)
                .map(PengaturanKantinResponse::dari)
                .orElseGet(() -> PengaturanKantinResponse.builder()
                        .sekolahId(sekolahId)
                        .namaKantin(null)
                        .jamTutupOtomatis(DEFAULT_JAM_TUTUP)
                        .konfirmasiManual(false)
                        .durasiFotoDetik(DEFAULT_DURASI_FOTO_DETIK)
                        .disimpan(false)
                        .build());
    }

    /**
     * Jam tutup kasir otomatis efektif untuk sekolah (default 23:59 bila belum
     * diatur). Dipakai penjadwal auto-tutup (PRD §6.4).
     */
    @Transactional(readOnly = true)
    public LocalTime jamTutupOtomatis(Long sekolahId) {
        return repo.findById(sekolahId)
                .map(SekolahKantinConfig::getJamTutupOtomatis)
                .orElse(DEFAULT_JAM_TUTUP);
    }

    /**
     * Ubah pengaturan kantin (upsert). Field {@code null} = pertahankan nilai
     * lama. Perubahan dicatat di audit (PRD §11.7).
     */
    @Transactional
    public PengaturanKantinResponse ubah(Long sekolahId, PengaturanKantinRequest request, Long aktorId) {
        OffsetDateTime now = jam.sekarang();

        SekolahKantinConfig baris = repo.findById(sekolahId)
                .orElseGet(() -> SekolahKantinConfig.builder()
                        .sekolahId(sekolahId)
                        .jamTutupOtomatis(DEFAULT_JAM_TUTUP)
                        .konfirmasiManual(false)
                        .durasiFotoDetik(DEFAULT_DURASI_FOTO_DETIK)
                        .createdAt(now)
                        .build());

        Long minTopup = request.getMinTopup() != null ? request.getMinTopup() : baris.getMinTopup();
        Long maksTopup = request.getMaksTopup() != null ? request.getMaksTopup() : baris.getMaksTopup();
        if (minTopup != null && maksTopup != null && minTopup > maksTopup) {
            throw new InvalidOperationException(
                    "Minimum top-up tidak boleh melebihi maksimum top-up");
        }

        String lama = ringkas(baris);
        if (request.getNamaKantin() != null) {
            baris.setNamaKantin(request.getNamaKantin());
        }
        if (request.getJamTutupOtomatis() != null) {
            baris.setJamTutupOtomatis(request.getJamTutupOtomatis());
        }
        if (request.getKonfirmasiManual() != null) {
            baris.setKonfirmasiManual(request.getKonfirmasiManual());
        }
        if (request.getDurasiFotoDetik() != null) {
            baris.setDurasiFotoDetik(request.getDurasiFotoDetik());
        }
        baris.setMinTopup(minTopup);
        baris.setMaksTopup(maksTopup);
        if (request.getBatasSaldoSiswa() != null) {
            baris.setBatasSaldoSiswa(request.getBatasSaldoSiswa());
        }
        if (request.getBatasSaldoKartuTamu() != null) {
            baris.setBatasSaldoKartuTamu(request.getBatasSaldoKartuTamu());
        }
        baris.setDiperbaruiOleh(aktorId);
        baris.setUpdatedAt(now);
        repo.save(baris);

        auditLogger.catat(aktorId, sekolahId, "UBAH_PENGATURAN_KANTIN", "SekolahKantinConfig",
                String.valueOf(sekolahId), "Ubah pengaturan kantin", lama, ringkas(baris));
        log.info("Pengaturan kantin sekolah={} diperbarui oleh={}", sekolahId, aktorId);
        return PengaturanKantinResponse.dari(baris);
    }

    /** Ringkasan pengaturan untuk audit (nilai lama → baru). */
    private String ringkas(SekolahKantinConfig c) {
        return "nama=" + c.getNamaKantin()
                + ";jamTutup=" + c.getJamTutupOtomatis()
                + ";konfirmasiManual=" + c.isKonfirmasiManual()
                + ";durasiFoto=" + c.getDurasiFotoDetik()
                + ";minTopup=" + c.getMinTopup()
                + ";maksTopup=" + c.getMaksTopup()
                + ";batasSaldoSiswa=" + c.getBatasSaldoSiswa()
                + ";batasSaldoKartuTamu=" + c.getBatasSaldoKartuTamu();
    }
}
