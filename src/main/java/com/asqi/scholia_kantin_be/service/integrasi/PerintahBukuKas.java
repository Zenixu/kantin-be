package com.asqi.scholia_kantin_be.service.integrasi;

import com.asqi.scholia_kantin_be.enums.MetodeBukuKas;
import com.asqi.scholia_kantin_be.enums.TipeBukuKas;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Perintah pencatatan satu entri Buku Kas ke admin-be — INTEGRATIONS.md §3.
 *
 * <p>Objek nilai agar service kantin tidak peduli bagaimana entri dikirim
 * (REST/internal). Menyembunyikan ketergantungan pada kontrak final admin-be
 * (OPEN-QUESTIONS <b>Q3</b>).
 *
 * <p><b>Uang:</b> kantin menyimpan rupiah integer ({@code Long}), sedangkan Buku
 * Kas memakai {@code BigDecimal(15,2)} (INTEGRATIONS.md §3.4 poin 3). Konversi
 * dilakukan di sisi pemanggil lewat {@link #jumlah} — selalu
 * {@code BigDecimal.valueOf(rupiahLong)} (tanpa pecahan).
 */
@Getter
@Builder
public class PerintahBukuKas {

    /** Tenant pemilik entri (scoping PRD §11.4). */
    private final Long sekolahId;

    /** Waktu transaksi menurut zona sekolah. */
    private final OffsetDateTime tanggal;

    private final TipeBukuKas tipe;

    /** Kategori pos Buku Kas (mis. {@code "Pendapatan Kantin"}). */
    private final String kategori;

    /** Jumlah rupiah sebagai desimal (Buku Kas memakai NUMERIC(15,2)). */
    private final BigDecimal jumlah;

    private final MetodeBukuKas metode;

    /** Keterangan bebas untuk laporan. */
    private final String keterangan;

    /**
     * Referensi unik entri kantin (mis. {@code "KANTIN-SESI-<id>"}).
     *
     * <p>Dipakai admin-be untuk menautkan entri ke dokumen asal. <b>Wajib unik
     * &amp; deterministik</b> agar posting ganda bisa dicegah/diidentifikasi.
     */
    private final String refId;

    /**
     * Modul referensi.
     *
     * <p><b>Mitigasi Q3 (INTEGRATIONS.md §3.4 poin 1):</b> nilai modul kantin
     * belum dikenal {@code migrateBukuKas()} admin-be, sehingga entri berisiko
     * dianggap <i>orphan</i>/dihapus saat migrasi. Sampai tim admin-be menambah
     * case kantin, kirim {@code null} — entri masuk {@code remainingBks} dan
     * <b>tidak</b> dihapus. Jangan isi nilai kantin sebelum Q3 terjawab.
     */
    private final String refModul;
}
