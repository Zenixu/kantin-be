package com.asqi.scholia_kantin_be.service.integrasi;

import com.asqi.scholia_kantin_be.enums.JenisNotifikasi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * Perintah pengiriman satu notifikasi ke orang tua — INTEGRATIONS.md §6.
 *
 * <p>Objek nilai agar service kantin tidak peduli bagaimana notifikasi dikirim
 * (REST internal mobile-be). Menyembunyikan ketergantungan pada kontrak final
 * mobile-be (OPEN-QUESTIONS <b>Q5</b>).
 *
 * <p>Notifikasi bersifat <b>best-effort</b> (PRD §8.4): kegagalan pengiriman
 * tidak boleh membatalkan transaksi/top-up yang sudah tercatat di ledger.
 */
@Getter
@Builder
public class PerintahNotifikasi {

    /** Tenant pemilik peristiwa (scoping PRD §11.4). */
    private final Long sekolahId;

    /** Jenis peristiwa (memilih template di sisi mobile-be). */
    private final JenisNotifikasi jenis;

    /** Jenis subjek pemilik saldo (notifikasi hanya untuk {@code SISWA}). */
    private final SubjekTipe subjekTipe;

    /** ID siswa pemilik kartu. */
    private final Long subjekId;

    /** Nominal peristiwa dalam rupiah (boleh {@code null} bila tak relevan). */
    private final Long nominal;

    /** Saldo subjek setelah peristiwa (rupiah). */
    private final Long saldoSetelah;

    /** Referensi peristiwa (mis. id transaksi / refId PG / nomor bukti). */
    private final String referensiId;

    /** Ringkasan teks siap tampil (mis. "Budi belanja Rp8.000 di Kantin: ..."). */
    private final String ringkasan;

    /** Pelaku (petugas/bendahara); {@code null} untuk aksi sistem (webhook). */
    private final Long aktorId;

    /** Waktu peristiwa menurut zona sekolah (boleh {@code null}). */
    private final OffsetDateTime waktu;
}
