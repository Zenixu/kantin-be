package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import lombok.Builder;
import lombok.Getter;

/**
 * Perintah satu mutasi saldo — masukan {@link LedgerSaldoService}.
 *
 * <p>Objek nilai (immutable) agar aturan bisnis ledger terpusat di service,
 * bukan tersebar di pemanggil. {@code nominal} selalu positif; tanda ditentukan
 * oleh method yang dipanggil ({@code kredit}/{@code debit}).
 */
@Getter
@Builder
public class PerintahMutasiSaldo {

    /** Tenant (sekolah) — wajib (PRD §11.4). */
    private final Long sekolahId;

    private final SubjekTipe subjekTipe;

    /** ID siswa atau ID Kartu Tamu. */
    private final Long subjekId;

    private final JenisMutasiSaldo jenis;

    /** Nominal rupiah integer &gt; 0. */
    private final long nominal;

    /**
     * Batas saldo maksimum setelah mutasi (rupiah); {@code null} = tanpa batas
     * (PRD §8.2/§9.1/§9.4, issue #112). Bila diisi, kredit yang membuat saldo
     * melebihi batas ditolak di dalam seksi terkunci (bebas race).
     */
    private final Long batasSaldoMaksimum;

    /** UNIQUE — anti tap/retry ganda (PRD §11.3). Boleh null untuk mutasi manual tanpa retry. */
    private final String idempotencyKey;

    /** Terisi bila mutasi berasal dari transaksi kasir. */
    private final Long transaksiId;

    private final String referensiTipe;

    private final String referensiId;

    private final String keterangan;

    /** User yang memicu (audit). */
    private final Long aktorId;
}
