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
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;

/**
 * Penanda <b>posting Buku Kas</b> untuk sebuah mutasi stok (PRD §5.1, §7.2).
 *
 * <p>Dipakai sebagai kunci idempotency posting: Buku Kas admin-be
 * <b>tidak</b> idempoten (INTEGRATIONS.md §3.4), jadi kantin-be menyimpan satu
 * baris per posting sukses dan menolak posting ulang dengan {@code referensiId}
 * (refId deterministik) yang sama.
 *
 * <p>Berbeda dari {@code mutasi_stok}/{@code saldo_ledger} (append-only), tabel
 * ini adalah <b>penanda</b>: baris hanya ditulis saat posting sukses. {@code id}
 * dari aplikasi ({@code IdGenerator}), bukan autoincrement.
 */
@Entity
@Table(name = "posting_buku_kas")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostingBukuKas implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    /** Tenant (sekolah) pemilik entri — scoping PRD §11.4. */
    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    /** refId deterministik entri Buku Kas — kunci idempotency. */
    @Column(name = "referensi_id", nullable = false, length = 120)
    private String referensiId;

    /**
     * Jenis sumber posting: {@code BARANG_MASUK} / {@code BARANG_MASUK_PEMBALIK}
     * (dari {@code mutasi_stok}) atau {@code KOREKSI_SALDO} (dari
     * {@code saldo_ledger}, PRD §5.1, §9.2).
     */
    @Column(name = "entitas", nullable = false, length = 40)
    private String entitas;

    /**
     * ID entitas sumber — baris {@code mutasi_stok} untuk posting stok, atau
     * baris {@code saldo_ledger} untuk koreksi saldo. {@code null} bila sumber
     * tidak ber-ID mutasi stok (V14 melonggarkan NOT NULL).
     */
    @Column(name = "mutasi_id")
    private Long mutasiId;

    /** Referensi entri dari Buku Kas (bila dikembalikan admin-be). */
    @Column(name = "ref_buku_kas", length = 120)
    private String refBukuKas;

    /** Jumlah rupiah integer (dikirim sebagai {@code BigDecimal} tanpa pecahan). */
    @Column(name = "jumlah", nullable = false)
    private Long jumlah;

    /** Hasil posting: {@code SUKSES} (baris hanya ditulis saat sukses). */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

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
     * Selalu {@code true}: baris penanda ditulis sekali. {@code save()} memakai
     * {@code persist()} (INSERT), bukan {@code merge()}.
     */
    @Override
    public boolean isNew() {
        return true;
    }
}
