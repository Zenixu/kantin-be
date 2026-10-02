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
 * <p>⚠️ <b>Temuan (docs/spesifikasi-fase4-ledger.md §5.3):</b> token dari
 * admin-be <b>tidak</b> membawa {@code sekolah_id} maupun {@code role}. Karena
 * itu {@code sekolahId} &amp; {@code peran} di sini <i>nullable</i> dan diisi
 * oleh {@code TenantResolver} bila berhasil; bila tidak, akses modul terblokir
 * (fail-closed) sampai Q1/Q2 terjawab.
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
}
