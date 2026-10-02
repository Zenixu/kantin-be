package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.model.SaldoLedger;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/** Satu baris mutasi saldo untuk ditampilkan pada riwayat (PRD §9.3). */
@Data
@Builder
public class MutasiSaldoItem {

    private Long id;
    private JenisMutasiSaldo jenis;
    private ArahMutasi arah;
    private long nominal;
    private long saldoSetelah;
    private String keterangan;
    private OffsetDateTime waktu;

    /** Proyeksi entitas ledger → DTO (menyembunyikan kolom internal). */
    public static MutasiSaldoItem dari(SaldoLedger l) {
        return MutasiSaldoItem.builder()
                .id(l.getId())
                .jenis(l.getJenis())
                .arah(l.getArah())
                .nominal(l.getNominal())
                .saldoSetelah(l.getSaldoSetelah())
                .keterangan(l.getKeterangan())
                .waktu(l.getWaktu())
                .build();
    }
}
