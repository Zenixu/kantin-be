package com.asqi.scholia_kantin_be.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Hasil refund/pemindahan sisa saldo siswa keluar (PRD §9.3, issue #38).
 */
@Data
@Builder
public class HasilRefundResponse {

    /** Subjek yang saldonya dikosongkan (siswa keluar). */
    private Long subjekId;

    /** Subjek penerima (saudara) — {@code null} untuk refund. */
    private Long tujuanSubjekId;

    /** Nominal yang dipindahkan/direfund (rupiah). */
    private long nominal;

    /** Nomor bukti/berita acara (idempotency). */
    private String referensiId;

    /** Saldo subjek sumber setelah operasi (selalu 0 bila berhasil). */
    private long saldoSetelah;

    /**
     * Apakah kartu sumber diminta diblokir. {@code false} bila pemblokiran
     * dilewati (integrasi Q7 belum siap) atau operasi ini replay idempoten.
     */
    private boolean kartuDiblokir;

    /**
     * Apakah label pemegang dikosongkan (PRD §9.4, pengembalian Kartu Tamu).
     * Hanya relevan untuk refund Kartu Tamu; {@code false} untuk operasi lain.
     */
    private boolean labelDikosongkan;

    /** {@code true} bila ini pengulangan idempotency (tidak ada mutasi baru). */
    private boolean idempoten;
}
