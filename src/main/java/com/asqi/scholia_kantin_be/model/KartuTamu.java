package com.asqi.scholia_kantin_be.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Kartu Tamu: kartu RFID untuk non-siswa (guru/staf/tamu) — PRD §9.4.
 *
 * <p>Saldo terikat ke <b>nomor kartu</b> (bukan orang). Kartu bisa dipinjamkan,
 * dikembalikan, di-top-up di TU. UID RFID <b>UNIQUE global</b> untuk mencegah
 * tabrakan dengan {@code rfid_uid} siswa di admin-be.
 *
 * <p>Contoh:
 * <ul>
 *   <li>Kartu KT-001 → untuk guru matematika, saldo Rp 50.000</li>
 *   <li>Kartu KT-005 → kartu tamu umum, saldo Rp 0 (perlu top-up)</li>
 * </ul>
 */
@Entity
@Table(name = "kartu_tamu")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KartuTamu {

    @Id
    private Long id;

    /** Tenant — sekolah pemilik kartu. */
    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    /**
     * Nomor kartu human-readable (KT-001, KT-002, dll).
     * UNIQUE per sekolah.
     */
    @Column(name = "nomor_kartu", nullable = false, length = 20)
    private String nomorKartu;

    /**
     * UID RFID kartu fisik (nullable jika belum di-bind).
     * UNIQUE global untuk anti-tabrakan dengan siswa.
     */
    @Column(name = "rfid_uid", length = 50)
    private String rfidUid;

    /**
     * Status kartu. {@code false} = kartu nonaktif (tidak bisa dipakai tap).
     * Soft delete.
     */
    @Column(nullable = false)
    private Boolean aktif;

    /** Catatan bebas (untuk siapa, keperluan apa, dll). */
    @Column(columnDefinition = "TEXT")
    private String catatan;

    /**
     * Label pemegang kartu (PRD §9.4) — nama guru/staf, atau "Tamu".
     * Opsional; <b>dikosongkan</b> saat kartu dikembalikan (siap dipakai ulang).
     */
    @Column(name = "label_pemegang", length = 150)
    private String labelPemegang;

    // ────────────────────────────────────────────────────────────────
    // AUDIT
    // ────────────────────────────────────────────────────────────────

    @Column(name = "dibuat_oleh", nullable = false)
    private Long dibuatOleh;

    @Column(name = "dibuat_pada", nullable = false)
    private Instant dibuatPada;

    @Column(name = "diubah_oleh")
    private Long diubahOleh;

    @Column(name = "diubah_pada")
    private Instant diubahPada;
}
