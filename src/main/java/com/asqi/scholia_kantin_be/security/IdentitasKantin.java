package com.asqi.scholia_kantin_be.security;

import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.enums.SumberToken;
import lombok.Builder;
import lombok.Getter;

import java.security.Principal;

/**
 * Identitas terverifikasi dari JWT SKOOLIA — "principal" kantin-be.
 *
 * <p>kantin-be <b>tidak</b> punya tabel user (ADR-0002). Objek ini dibangun
 * dari klaim token + hasil resolusi tenant, lalu dipasang ke
 * {@code SecurityContext}. Semua controller menerimanya via
 * {@code @AuthenticationPrincipal IdentitasKantin}.
 *
 * <p><b>Implementasi {@link Principal}:</b> {@code getName()} mengembalikan
 * {@code userId} sebagai string, supaya Spring Security bisa mengaitkan
 * autentikasi dengan benar (audit, logging, {@code sessionManagement}).
 *
 * <p>⚠️ <b>Status Q1 (diperbarui):</b> admin-be kini <i>sudah</i> menyetel klaim
 * {@code sekolah_id} &amp; {@code role} (lihat {@code admin-be/JwtUtils.buildToken()}),
 * tetapi <b>belum</b> menyetel {@code iss}. Karena itu {@code sekolahId} &amp;
 * {@code peran} tetap <i>nullable</i> (fail-closed bila absen) dan decoder
 * menerima {@code iss} yang absen — lihat
 * {@code KantinJwtDecoder.verifikasi()} &amp; {@code KompatibilitasTokenStafAdminTest}.
 */
@Getter
@Builder
public class IdentitasKantin implements Principal {

    /** ID user di SKOOLIA (klaim {@code sub} atau {@code user_id}). */
    private final String userId;

    /** Nama tampilan (bila ada di klaim). */
    private final String nama;

    /** ID sekolah (tenant). null bila token tidak membawa informasi sekolah. */
    private final Long sekolahId;

    /** Peran kantin hasil pemetaan klaim role. */
    private final AktorKantin peran;

    /** Role mentah dari token (untuk audit/diagnostik). */
    private final String roleMentah;

    /** Dari penerbit mana token berasal. */
    private final SumberToken sumber;

    /** ID siswa bila token ortu terikat ke satu anak (mobile-be). */
    private final Long siswaId;

    @Override
    public String getName() {
        return userId;
    }

    /** Apakah identitas sudah punya konteks sekolah yang sah. */
    public boolean punyaSekolah() {
        return sekolahId != null;
    }

    /** Apakah token berasal dari orang tua. */
    public boolean orangTua() {
        return sumber == SumberToken.MOBILE;
    }

    /**
     * ID petugas sebagai {@code Long} untuk kolom audit/transaksi
     * ({@code aktor_id}, {@code void_oleh}, dst).
     *
     * <p>Token SKOOLIA membawa {@code userId} sebagai string, sedangkan skema
     * kantin menyimpan aktor sebagai BIGINT. Konversi ini <b>dipusatkan di sini</b>
     * (bukan diulang di setiap controller) — mencegah duplikasi &amp; inkonsistensi.
     *
     * @throws IllegalStateException bila userId kosong
     * @throws IllegalArgumentException bila userId bukan numerik
     */
    public Long aktorIdWajib() {
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("Identitas petugas tidak tersedia (userId kosong)");
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ID petugas tidak valid pada token: " + userId);
        }
    }
}
