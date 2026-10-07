package com.asqi.scholia_kantin_be.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Pengaturan kantin per sekolah (PRD §9.1, issue <b>#42</b>).
 *
 * <p>PK = {@code sekolah_id} (satu baris per sekolah). Menyimpan: nama kantin,
 * jam tutup kasir otomatis, konfirmasi manual, durasi foto, min/maks top-up,
 * serta batas saldo maksimum per siswa &amp; per Kartu Tamu.
 *
 * <p>Entity <b>mutable</b> — pengaturan boleh berubah; setiap perubahan dicatat
 * di {@code audit_log} (PRD §11.7).
 *
 * <p>Aktivasi modul &amp; fee platform (§10) <b>tidak</b> disimpan di sini —
 * itu milik internal-be (OPEN-QUESTIONS Q6), diakses lewat port.
 */
@Entity
@Table(name = "sekolah_kantin_config")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SekolahKantinConfig {

    @Id
    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    @Column(name = "nama_kantin", length = 150)
    private String namaKantin;

    /** Jam tutup kasir otomatis (zona sekolah) — PRD §6.4. */
    @Column(name = "jam_tutup_otomatis", nullable = false)
    private LocalTime jamTutupOtomatis;

    /** Langkah konfirmasi manual sebelum transaksi tercatat (default nonaktif, §6.1). */
    @Column(name = "konfirmasi_manual", nullable = false)
    private boolean konfirmasiManual;

    /** Durasi tampil foto/identitas setelah transaksi (detik) — default 3 (§6.1). */
    @Column(name = "durasi_foto_detik", nullable = false)
    private int durasiFotoDetik;

    /** Minimum per top-up (rupiah); {@code null} = tanpa batas minimum. */
    @Column(name = "min_topup")
    private Long minTopup;

    /** Maksimum per top-up (rupiah); {@code null} = tanpa batas maksimum. */
    @Column(name = "maks_topup")
    private Long maksTopup;

    /** Batas saldo maksimum per siswa (rupiah); {@code null} = tanpa batas. */
    @Column(name = "batas_saldo_siswa")
    private Long batasSaldoSiswa;

    /** Batas saldo maksimum per Kartu Tamu (rupiah); {@code null} = tanpa batas. */
    @Column(name = "batas_saldo_kartu_tamu")
    private Long batasSaldoKartuTamu;

    @Column(name = "diperbarui_oleh")
    private Long diperbaruiOleh;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
