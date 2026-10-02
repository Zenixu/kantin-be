package com.asqi.scholia_kantin_be.model;

import com.asqi.scholia_kantin_be.enums.StatusSesiKasir;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Sesi kasir — periode transaksi satu titik kasir dalam satu hari (PRD §6.4).
 *
 * <p>Satu titik kasir hanya boleh punya satu sesi per tanggal
 * (UNIQUE {@code (titik_kasir_id, tanggal)}). Sesi ditutup manual oleh petugas
 * atau otomatis pada jam yang dikonfigurasi sekolah.
 *
 * <p>{@code posting_buku_kas} + {@code referensi_buku_kas} menjaga posting Buku
 * Kas tetap idempoten (INTEGRATIONS.md §3.4).
 */
@Entity
@Table(name = "sesi_kasir")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SesiKasir {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    @Column(name = "titik_kasir_id", nullable = false)
    private Long titikKasirId;

    /** Tanggal sesi menurut zona waktu sekolah. */
    @Column(name = "tanggal", nullable = false)
    private LocalDate tanggal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private StatusSesiKasir status;

    @Column(name = "total_bruto", nullable = false)
    private Long totalBruto;

    @Column(name = "total_void", nullable = false)
    private Long totalVoid;

    @Column(name = "total_bersih", nullable = false)
    private Long totalBersih;

    @Column(name = "dibuka_at", nullable = false)
    private OffsetDateTime dibukaAt;

    @Column(name = "ditutup_at")
    private OffsetDateTime ditutupAt;

    @Column(name = "ditutup_oleh")
    private Long ditutupOleh;

    @Column(name = "auto_tutup", nullable = false)
    private boolean autoTutup;

    @Column(name = "posting_buku_kas", nullable = false)
    private boolean postingBukuKas;

    @Column(name = "referensi_buku_kas", length = 64)
    private String referensiBukuKas;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void isiDefault() {
        OffsetDateTime now = OffsetDateTime.now();
        if (dibukaAt == null) {
            dibukaAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    /** Sesi masih terbuka (menerima transaksi &amp; void). */
    public boolean terbuka() {
        return status == StatusSesiKasir.TERBUKA;
    }
}
