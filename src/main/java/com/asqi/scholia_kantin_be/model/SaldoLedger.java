package com.asqi.scholia_kantin_be.model;

import com.asqi.scholia_kantin_be.enums.ArahMutasi;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
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
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;

/**
 * Mutasi saldo — baris buku besar (ledger) saldo siswa / Kartu Tamu.
 *
 * <p><b>Append-only (PRD §11.1, ADR-0003):</b> entitas ini {@link Immutable} —
 * Hibernate tidak akan pernah menerbitkan {@code UPDATE}/{@code DELETE} untuknya.
 * Kunci database (trigger {@code trg_saldo_ledger_append_only}) menjadi lapisan
 * kedua. Koreksi dilakukan dengan <b>baris baru</b> ber-{@code arah} berlawanan,
 * bukan mengubah baris lama.
 *
 * <p>Tidak ada setter publik: nilai ditetapkan sekali saat pembuatan.
 * Kolom {@code id} berasal dari aplikasi ({@code Constants.sortableIdGenerator()}),
 * bukan autoincrement (CONVENTIONS.md §4).
 */
@Entity
@Table(name = "saldo_ledger")
@Immutable
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaldoLedger implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    /** Tenant (sekolah) pemilik baris — wajib untuk scoping (PRD §11.4). */
    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subjek_tipe", nullable = false, length = 20)
    private SubjekTipe subjekTipe;

    /** ID siswa (lokal) atau ID kartu tamu, tergantung {@link #subjekTipe}. */
    @Column(name = "subjek_id", nullable = false)
    private Long subjekId;

    @Enumerated(EnumType.STRING)
    @Column(name = "arah", nullable = false, length = 10)
    private ArahMutasi arah;

    @Enumerated(EnumType.STRING)
    @Column(name = "jenis", nullable = false, length = 40)
    private JenisMutasiSaldo jenis;

    /** Selalu positif; tanda ditentukan {@link #arah}. */
    @Column(name = "nominal", nullable = false)
    private Long nominal;

    /** Snapshot saldo setelah mutasi — untuk audit &amp; deteksi drift. */
    @Column(name = "saldo_setelah", nullable = false)
    private Long saldoSetelah;

    /** Terisi bila mutasi berasal dari transaksi kasir. */
    @Column(name = "transaksi_id")
    private Long transaksiId;

    /** UNIQUE — mencegah tap/retry ganda memotong saldo dua kali (PRD §11.3). */
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "referensi_tipe", length = 40)
    private String referensiTipe;

    @Column(name = "referensi_id", length = 64)
    private String referensiId;

    @Column(name = "keterangan", length = 255)
    private String keterangan;

    /** User yang memicu mutasi (audit). */
    @Column(name = "aktor_id")
    private Long aktorId;

    @Column(name = "waktu", nullable = false)
    private OffsetDateTime waktu;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** Isi default waktu bila pemanggil lupa (kolom NOT NULL tanpa default di ORM). */
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
     * Selalu {@code true}: ledger hanya boleh INSERT. Dengan ini
     * {@code JpaRepository.save()} memakai {@code persist()}, tidak
     * {@code merge()} — tidak ada UPDATE pada baris ledger (PRD §11.1).
     */
    @Override
    public boolean isNew() {
        return true;
    }
}
