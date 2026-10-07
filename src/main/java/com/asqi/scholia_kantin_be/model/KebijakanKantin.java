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

import java.time.OffsetDateTime;

/**
 * Kebijakan kantin per sekolah (DEMO, OPEN-QUESTIONS <b>Q16</b> / issue #25).
 *
 * <p>Menyimpan kebijakan saldo mengendap siswa lulus/keluar yang tak diklaim.
 * Default {@code REFUND}. Entity ini <b>mutable</b> (kebijakan boleh berubah) —
 * perubahan wajib dicatat di {@code audit_log}.
 */
@Entity
@Table(name = "kebijakan_kantin")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KebijakanKantin {

    /** PK = sekolah_id: satu baris kebijakan per sekolah. */
    @Id
    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    /** Kebijakan saldo mengendap (nama enum {@link com.asqi.scholia_kantin_be.enums.KebijakanSaldoMengendap}). */
    @Column(name = "kebijakan_saldo_mengendap", nullable = false, length = 30)
    private String kebijakanSaldoMengendap;

    /** Ambang hari (mis. berapa hari setelah lulus saldo ditindaklanjuti). */
    @Column(name = "ambang_hari", nullable = false)
    private Integer ambangHari;

    @Column(name = "catatan", length = 500)
    private String catatan;

    /** Aktor yang terakhir mengubah kebijakan (audit). */
    @Column(name = "diperbarui_oleh")
    private Long diperbaruiOleh;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
