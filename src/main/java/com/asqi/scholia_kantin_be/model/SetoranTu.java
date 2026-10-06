package com.asqi.scholia_kantin_be.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Setoran kas TU harian (PRD §9.2) — <b>append-only</b>.
 *
 * <p>Rekap top-up tunai <b>per petugas per hari</b> dikonfirmasi bendahara saat
 * uang disetor. Selisih kas (<b>dicatat, tidak dihapus</b>) dihitung kolom
 * generated {@code selisih = total_topup − jumlah_disetor}.
 *
 * <p>{@link Immutable} + trigger DB ({@code trg_setoran_tu_append_only})
 * memastikan Hibernate tidak pernah menerbitkan {@code UPDATE}/{@code DELETE};
 * sama seperti {@link SaldoLedger}/{@link AuditLog}. Koreksi = baris baru.
 *
 * <p>Tidak ada setter publik: nilai ditetapkan sekali via builder. {@code id}
 * dari aplikasi ({@code IdGenerator}), bukan autoincrement.
 */
@Entity
@Table(name = "setoran_tu")
@Immutable
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SetoranTu implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    /** Tenant (sekolah) pemilik baris — wajib untuk scoping (PRD §11.4). */
    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    /** Hari rekap (zona waktu sekolah). */
    @Column(name = "tanggal", nullable = false)
    private LocalDate tanggal;

    /** Petugas TU yang melakukan top-up tunai hari itu (aktor_id). */
    @Column(name = "petugas_id", nullable = false)
    private Long petugasId;

    /** Rekap top-up tunai petugas pada {@link #tanggal} (dari saldo_ledger). */
    @Column(name = "total_topup", nullable = false)
    private Long totalTopup;

    /** Uang fisik yang benar-benar disetor ke bendahara. */
    @Column(name = "jumlah_disetor", nullable = false)
    private Long jumlahDisetor;

    /**
     * {@code total_topup − jumlah_disetor}. Kolom <b>GENERATED</b> di DB —
     * read-only di ORM ({@code insertable=false, updatable=false}); nilai pada
     * objek yang baru disimpan dihitung aplikasi (lihat
     * {@code SetoranTuService}), sedangkan pembacaan dari DB mengisi nilai asli
     * hasil hitung database.
     */
    @Column(name = "selisih", insertable = false, updatable = false)
    private Long selisih;

    /** Nomor berita acara setoran — UNIQUE per sekolah (idempotency). */
    @Column(name = "referensi_id", nullable = false, length = 60)
    private String referensiId;

    /** Catatan bebas (mis. alasan selisih). */
    @Column(name = "catatan", length = 500)
    private String catatan;

    /** Bendahara yang mengonfirmasi setoran (aktor). */
    @Column(name = "dikonfirmasi_oleh", nullable = false)
    private Long dikonfirmasiOleh;

    @Column(name = "waktu", nullable = false)
    private OffsetDateTime waktu;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** Isi default waktu bila pemanggil lupa (kolom NOT NULL di DB). */
    @PrePersist
    void isiWaktu() {
        OffsetDateTime now = OffsetDateTime.now();
        if (waktu == null) {
            waktu = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }

    /**
     * Selalu {@code true}: setoran hanya INSERT. {@code save()} memakai
     * {@code persist()}, bukan {@code merge()} (tanpa UPDATE).
     */
    @Override
    public boolean isNew() {
        return true;
    }
}
