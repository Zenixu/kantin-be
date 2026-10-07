package com.asqi.scholia_kantin_be.service.integrasi;

import com.asqi.scholia_kantin_be.config.notifikasi.NotifikasiProperties;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Fasad pengiriman notifikasi ke orang tua (PRD §8.4, INTEGRATIONS.md §6).
 *
 * <p>Satu-satunya pintu yang boleh dipanggil service kantin untuk mengirim
 * notifikasi. Membungkus {@link NotifikasiPort} dengan jaminan
 * <b>best-effort / fail-open</b>:
 * <ul>
 *   <li>Pengiriman hanya untuk subjek {@link SubjekTipe#SISWA} (notifikasi ke
 *       ortu; subjek lain tidak punya ortu).</li>
 *   <li>Bila {@code kantin.notifikasi.enabled=false} → <b>dilewati</b> tanpa
 *       memanggil port (berguna untuk dev/test).</li>
 *   <li>Exception apa pun dari port <b>ditangkap</b> dan diubah menjadi
 *       {@link HasilKirimNotifikasi#gagal} — kegagalan notifikasi
 *       <b>tidak pernah</b> membatalkan transaksi/top-up/refund yang sudah
 *       tercatat (PRD §8.4, INTEGRATIONS.md §6).</li>
 * </ul>
 *
 * <p>Kenapa di {@code service/integrasi}? Semua panggilan lintas-sistem
 * <b>wajib</b> lewat paket ini (INTEGRATIONS.md pembuka).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotifikasiService {

    private final NotifikasiPort notifikasiPort;
    private final NotifikasiProperties properties;

    /**
     * Kirim satu notifikasi ke ortu — <b>selalu</b> mengembalikan hasil,
     * tidak pernah melempar.
     */
    public HasilKirimNotifikasi kirim(PerintahNotifikasi perintah) {
        if (perintah == null || perintah.getJenis() == null) {
            return HasilKirimNotifikasi.dilewati("Perintah notifikasi tidak lengkap");
        }
        if (!properties.isEnabled()) {
            return HasilKirimNotifikasi.dilewati(
                    "Notifikasi dinonaktifkan (kantin.notifikasi.enabled=false)");
        }
        // PRD §8.4: notifikasi hanya untuk siswa (memiliki orang tua).
        if (perintah.getSubjekTipe() != SubjekTipe.SISWA) {
            return HasilKirimNotifikasi.dilewati("Bukan subjek SISWA — tanpa ortu");
        }

        try {
            HasilKirimNotifikasi hasil = notifikasiPort.kirim(perintah);
            if (hasil == null) {
                return HasilKirimNotifikasi.dilewati("Port notifikasi mengembalikan null");
            }
            if (hasil.status() == HasilKirimNotifikasi.Status.GAGAL) {
                log.warn("Notifikasi {} GAGAL (best-effort, transaksi tetap sah): {}",
                        perintah.getJenis(), hasil.pesan());
            }
            return hasil;
        } catch (RuntimeException e) {
            // Fail-open: apa pun yang terjadi pada integrasi, transaksi tetap sah.
            log.warn("Notifikasi {} gagal (best-effort, transaksi tidak dibatalkan): {}",
                    perintah.getJenis(), e.getMessage());
            return HasilKirimNotifikasi.gagal(e.getMessage());
        }
    }
}
