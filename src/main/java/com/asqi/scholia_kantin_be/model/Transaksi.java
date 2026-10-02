package com.asqi.scholia_kantin_be.model;

import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Transaksi kasir (header) — PRD §6.2.
 *
 * <p><b>Bukan ledger.</b> Void boleh meng-UPDATE {@code status} di sini, tetapi
 * <b>tidak pernah</b> mengubah/menghapus baris {@code saldo_ledger} atau
 * {@code mutasi_stok} — kompensasi lewat mutasi pembalik baru (PRD §11.1).
 *
 * <p>{@code id} dibuat oleh <b>klien kasir</b>; {@code idempotency_key} UNIQUE
 * mencegah tap ganda memotong saldo dua kali (PRD §11.3).
 */
@Entity
@Table(name = "transaksi")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaksi implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * Penanda transient (tidak disimpan) untuk membedakan entitas baru.
     *
     * <p>{@code transaksi.id} di-assign aplikasi (bukan autoincrement), jadi
     * Hibernate <b>tidak</b> tahu apakah baris sudah ada. Tanpa penanda ini,
     * {@code save()} memakai {@code merge()} → SELECT baris yang belum ada →
     * error. {@link #isNew()} mengembalikan {@code true} saat baru (INSERT),
     * {@code false} setelah dimuat/di-persist (UPDATE untuk void).
     */
    @Transient
    @Builder.Default
    private boolean baru = true;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    @Column(name = "sesi_kasir_id", nullable = false)
    private Long sesiKasirId;

    @Column(name = "titik_kasir_id", nullable = false)
    private Long titikKasirId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subjek_tipe", nullable = false, length = 20)
    private SubjekTipe subjekTipe;

    @Column(name = "subjek_id", nullable = false)
    private Long subjekId;

    /** Kartu fisik yang dipakai (UID) — untuk audit sengketa. */
    @Column(name = "kartu_uid", length = 64)
    private String kartuUid;

    /** Akun petugas yang memproses (PRD §6.6). */
    @Column(name = "petugas_id", nullable = false)
    private Long petugasId;

    /** Total belanja (rupiah integer). */
    @Column(name = "total", nullable = false)
    private Long total;

    /** Σ HPP snapshot item — dasar laba kotor (PRD §5). */
    @Column(name = "total_hpp", nullable = false)
    private Long totalHpp;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private StatusTransaksi status;

    @Column(name = "alasan_void", length = 255)
    private String alasanVoid;

    @Column(name = "void_at")
    private OffsetDateTime voidAt;

    @Column(name = "void_oleh")
    private Long voidOleh;

    @Column(name = "waktu", nullable = false)
    private OffsetDateTime waktu;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Rincian item (snapshot). Cascade hanya untuk INSERT anak saat transaksi dibuat. */
    @OneToMany(mappedBy = "transaksi", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TransaksiItem> items = new ArrayList<>();

    /** Tambah satu item &amp; jaga dua arah relasi tetap sinkron. */
    public void tambahItem(TransaksiItem item) {
        item.setTransaksi(this);
        this.items.add(item);
    }

    public boolean voided() {
        return status == StatusTransaksi.VOID;
    }

    @PrePersist
    void isiWaktu() {
        OffsetDateTime now = OffsetDateTime.now();
        if (waktu == null) {
            waktu = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    /** Hanya INSERT saat baru; setelah dimuat/di-persist menjadi UPDATE (void). */
    @Override
    public boolean isNew() {
        return baru;
    }

    @PostPersist
    @PostLoad
    void tandaiSudahTersimpan() {
        this.baru = false;
    }
}
