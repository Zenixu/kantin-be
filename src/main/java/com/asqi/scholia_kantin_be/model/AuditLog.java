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

import java.time.OffsetDateTime;

/**
 * Baris jejak audit (PRD §11.7) — <b>append-only</b>.
 *
 * <p>{@link Immutable} + trigger DB ({@code trg_audit_log_append_only})
 * memastikan Hibernate tidak pernah menerbitkan {@code UPDATE}/{@code DELETE}
 * untuk entitas ini; sama seperti {@link SaldoLedger}/{@link MutasiStok}.
 *
 * <p>Tidak ada setter publik: nilai ditetapkan sekali via builder. {@code id}
 * berasal dari aplikasi ({@code IdGenerator}), bukan autoincrement.
 */
@Entity
@Table(name = "audit_log")
@Immutable
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    /** Tenant (sekolah) pemilik baris — wajib untuk scoping (PRD §11.4). */
    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    /** User pelaku; {@code null} = aksi sistem/scheduler. */
    @Column(name = "aktor_id")
    private Long aktorId;

    /** Nama aksi, mis. {@code VOID_TRANSAKSI}, {@code OPNAME_STOK}. */
    @Column(name = "aksi", nullable = false, length = 60)
    private String aksi;

    /** Nama entitas, mis. {@code Transaksi}, {@code Stok}. */
    @Column(name = "entitas", nullable = false, length = 60)
    private String entitas;

    @Column(name = "entitas_id", length = 64)
    private String entitasId;

    /** Nilai sebelum aksi (teks bebas, boleh {@code null}). */
    @Column(name = "nilai_lama", columnDefinition = "text")
    private String nilaiLama;

    /** Nilai sesudah aksi (teks bebas, boleh {@code null}). */
    @Column(name = "nilai_baru", columnDefinition = "text")
    private String nilaiBaru;

    /** Alasan — wajib untuk void/koreksi/opname (PRD §6.3, §7.3). */
    @Column(name = "alasan", length = 500)
    private String alasan;

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
     * Selalu {@code true}: audit hanya INSERT. {@code save()} memakai
     * {@code persist()}, bukan {@code merge()} (tanpa UPDATE).
     */
    @Override
    public boolean isNew() {
        return true;
    }
}
