package com.asqi.scholia_kantin_be.service.integrasi;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

/**
 * Informasi pemilik kartu hasil lookup — gabungan data identitas (dari
 * admin-be) &amp; data kontrol kantin (blokir/limit, milik kantin-be).
 *
 * <p>Dipisah sebagai objek nilai agar {@code TapService} tidak peduli dari mana
 * data berasal (API internal admin-be vs tabel kantin). Ini menyembunyikan
 * ketergantungan pada Q7 (kontrak lookup kartu).
 */
@Getter
@Builder
public class InfoKartu {

    /** Kartu terdaftar (siswa aktif / Kartu Tamu aktif) di sekolah ini. */
    private final boolean dikenal;

    /** Kartu diblokir (ortu/admin/TU) — diperiksa server tiap tap (PRD §11.11). */
    private final boolean diblokir;

    private final SubjekTipe subjekTipe;

    /** ID siswa (lokal) atau ID Kartu Tamu. */
    private final Long subjekId;

    private final String nama;

    /** Kelas (khusus siswa; null untuk Kartu Tamu). */
    private final String kelas;

    private final String fotoUrl;

    /**
     * Limit belanja harian (rupiah) untuk kartu ini; {@code null} = tanpa limit
     * (Kartu Tamu selalu {@code null} — tidak ada limit harian, PRD §9.4).
     */
    private final Long limitHarian;

    /** ID menu yang diblokir orang tua (khusus siswa, PRD §6.1 tahap 3). */
    private final Set<Long> menuDiblokir;

    /** ID kategori yang diblokir orang tua (khusus siswa). */
    private final Set<Long> kategoriDiblokir;

    public static InfoKartu tidakDikenal() {
        return InfoKartu.builder().dikenal(false).build();
    }

    /** Kartu siswa (punya limit &amp; blokir item). */
    public boolean siswa() {
        return subjekTipe == SubjekTipe.SISWA;
    }
}
