package com.asqi.scholia_kantin_be.model;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Saldo berjalan (cache) per subjek — <b>hot row</b> yang dikunci
 * ({@code SELECT ... FOR UPDATE}) saat debit (ADR-0003).
 *
 * <p>Turunan yang <b>selalu</b> dapat dihitung ulang dari {@link SaldoLedger}
 * (PRD §11.1). Nilainya bukan sumber kebenaran — ledger-lah sumbernya.
 *
 * <p>PK gabungan {@code (subjek_tipe, subjek_id)} via {@link IdClass}.
 * Entity ini <b>mutable</b> (satu-satunya kolom berubah: {@code saldo}) karena
 * memang cache, bukan ledger.
 */
@Entity
@Table(name = "saldo_cache")
@IdClass(SaldoCache.SaldoCacheId.class)
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaldoCache {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "subjek_tipe", nullable = false, length = 20)
    private SubjekTipe subjekTipe;

    @Id
    @Column(name = "subjek_id", nullable = false)
    private Long subjekId;

    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    /** Saldo berjalan (rupiah integer). Tidak boleh minus (CHECK di DB). */
    @Column(name = "saldo", nullable = false)
    private Long saldo;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Kunci gabungan {@code (subjek_tipe, subjek_id)}. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaldoCacheId implements Serializable {

        private SubjekTipe subjekTipe;

        private Long subjekId;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SaldoCacheId other)) {
                return false;
            }
            return subjekTipe == other.subjekTipe && Objects.equals(subjekId, other.subjekId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subjekTipe, subjekId);
        }
    }
}
