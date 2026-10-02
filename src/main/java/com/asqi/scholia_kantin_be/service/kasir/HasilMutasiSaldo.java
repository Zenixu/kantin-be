package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.model.SaldoLedger;
import lombok.Builder;
import lombok.Getter;

/**
 * Hasil satu mutasi saldo.
 *
 * <p>{@code idempotentReplay} = {@code true} bila permintaan ini adalah
 * <b>pengulangan</b> idempotency key yang sama (tap ganda/retry jaringan):
 * tidak ada mutasi baru, hasilnya dikembalikan apa adanya (PRD §11.3).
 */
@Getter
@Builder
public class HasilMutasiSaldo {

    private final SaldoLedger mutasi;

    /** Saldo subjek setelah mutasi. */
    private final long saldoSetelah;

    private final boolean idempotentReplay;

    public static HasilMutasiSaldo baru(SaldoLedger mutasi, long saldoSetelah) {
        return HasilMutasiSaldo.builder()
                .mutasi(mutasi)
                .saldoSetelah(saldoSetelah)
                .idempotentReplay(false)
                .build();
    }

    public static HasilMutasiSaldo replay(SaldoLedger mutasi) {
        return HasilMutasiSaldo.builder()
                .mutasi(mutasi)
                .saldoSetelah(mutasi.getSaldoSetelah())
                .idempotentReplay(true)
                .build();
    }
}
