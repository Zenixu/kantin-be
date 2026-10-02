package com.asqi.scholia_kantin_be.helper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Sumber waktu tunggal untuk modul kantin — <b>zona waktu sekolah</b>
 * (PRD §11.9, CONVENTIONS.md §4).
 *
 * <p>Dipisah menjadi bean agar mudah di-mock pada test (mis. menguji reset limit
 * harian pukul 00:00 tanpa menunggu tengah malam). Semua service <b>wajib</b>
 * memakai ini alih-alih {@code LocalDateTime.now()} langsung.
 *
 * <p><b>Catatan tipe waktu:</b> skema memakai {@code TIMESTAMPTZ} (waktu absolut),
 * sehingga entitas memakai {@link OffsetDateTime}. Zona hanya memengaruhi
 * nilai yang <i>ditampilkan/di-parse</i> (mis. batas hari), bukan penyimpanan.
 */
@Component
public class JamKantin {

    private final ZoneId zona;

    public JamKantin(@Value("${kantin.zona-waktu:Asia/Jakarta}") String zonaWaktu) {
        this.zona = ZoneId.of(zonaWaktu);
    }

    /** Waktu sekarang (dengan offset zona sekolah). */
    public OffsetDateTime sekarang() {
        return OffsetDateTime.now(zona);
    }

    /** Tanggal hari ini menurut zona sekolah — dasar sesi kasir &amp; limit harian. */
    public LocalDate hariIni() {
        return LocalDate.now(zona);
    }

    /** Awal hari (00:00) pada zona sekolah, untuk menghitung "belanja hari ini". */
    public OffsetDateTime awalHariIni() {
        return hariIni().atStartOfDay(zona).toOffsetDateTime();
    }

    /** Zona waktu yang dipakai. */
    public ZoneId zona() {
        return zona;
    }
}
