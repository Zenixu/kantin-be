package com.asqi.scholia_kantin_be.service.stok;

import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
import lombok.Builder;
import lombok.Getter;

/**
 * Perintah satu mutasi stok — masukan {@link LedgerStokService}.
 *
 * <p>{@code qty} selalu positif; tanda ditentukan method yang dipanggil
 * ({@code masuk}/{@code keluar}). {@code hppSnapshot} wajib untuk mutasi yang
 * memengaruhi HPP/void (PRD §7.4).
 */
@Getter
@Builder
public class PerintahMutasiStok {

    private final Long sekolahId;

    private final Long menuId;

    private final JenisMutasiStok jenis;

    /** Selalu positif (&gt; 0). */
    private final int qty;

    /** HPP/unit saat mutasi (boleh null untuk mutasi yang tidak menyentuh HPP). */
    private final Long hppSnapshot;

    private final Long transaksiId;

    private final String referensiTipe;

    private final String referensiId;

    /** Wajib untuk opname/penyesuaian (PRD §7.3). */
    private final String alasan;

    private final Long aktorId;
}
