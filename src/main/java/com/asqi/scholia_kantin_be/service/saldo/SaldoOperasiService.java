package com.asqi.scholia_kantin_be.service.saldo;

import com.asqi.scholia_kantin_be.dto.MutasiSaldoItem;
import com.asqi.scholia_kantin_be.dto.SaldoResponse;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Facade baca saldo untuk HTTP: menggabungkan saldo berjalan, belanja hari ini,
 * dan mutasi terbaru menjadi satu {@link SaldoResponse} (PRD §9.3).
 *
 * <p>Baca-saja; tidak memodifikasi ledger. Tetap tenant-scoped lewat
 * {@link LedgerSaldoService}.
 */
@Service
@RequiredArgsConstructor
public class SaldoOperasiService {

    private static final int MAKS_MUTASI = 50;

    private final LedgerSaldoService ledgerSaldo;

    /** Ringkasan saldo + belanja hari ini + mutasi terbaru. */
    @Transactional(readOnly = true)
    public SaldoResponse lihat(Long sekolahId, SubjekTipe subjekTipe, Long subjekId, int batasMutasi) {
        int batas = (batasMutasi <= 0 || batasMutasi > MAKS_MUTASI) ? 20 : batasMutasi;
        long saldo = ledgerSaldo.saldo(sekolahId, subjekTipe, subjekId);
        long belanjaHariIni = (subjekTipe == SubjekTipe.SISWA)
                ? ledgerSaldo.belanjaHariIni(sekolahId, subjekTipe, subjekId)
                : 0L;

        List<MutasiSaldoItem> mutasi = ledgerSaldo
                .riwayatTerbaru(sekolahId, subjekTipe, subjekId, batas)
                .stream()
                .map(MutasiSaldoItem::dari)
                .toList();

        return SaldoResponse.builder()
                .subjekTipe(subjekTipe)
                .subjekId(subjekId)
                .saldo(saldo)
                .belanjaHariIni(belanjaHariIni)
                .mutasiTerbaru(mutasi)
                .build();
    }

    /** Hitung ulang saldo dari ledger — verifikasi/rekonsiliasi cache. */
    @Transactional(readOnly = true)
    public long hitungUlangDariLedger(Long sekolahId, SubjekTipe subjekTipe, Long subjekId) {
        return ledgerSaldo.hitungUlangDariLedger(sekolahId, subjekTipe, subjekId);
    }
}
