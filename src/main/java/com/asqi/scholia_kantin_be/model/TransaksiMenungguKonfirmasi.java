package com.asqi.scholia_kantin_be.model;

import com.asqi.scholia_kantin_be.enums.StatusPendingTap;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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

/**
 * Tap yang menunggu <b>konfirmasi manual</b> petugas (PRD §6.1, §9.1).
 *
 * <p>Dibuat saat sekolah mengaktifkan langkah konfirmasi manual: tap tidak
 * langsung memotong saldo/stok, melainkan menyimpan permintaan di sini. Petugas
 * menekan "Konfirmasi" (commit) atau membatalkannya. Baris ini <b>bukan ledger</b>
 * — tidak menyentuh {@code saldo_ledger}/{@code mutasi_stok} sampai dikonfirmasi.
 *
 * <p>{@code itemsJson} menyimpan snapshot item {@code {menuId,qty}} agar saat
 * konfirmasi item divalidasi ulang (stok/saldo bisa berubah antara tap &amp;
 * konfirmasi).
 */
@Entity
@Table(name = "transaksi_menunggu_konfirmasi")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransaksiMenungguKonfirmasi implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    /** Penanda transient: id di-assign aplikasi → cegah merge() mencoba SELECT. */
    @Transient
    @Builder.Default
    private boolean baru = true;

    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    /** Idempotency key dari klien kasir (UNIQUE per sekolah). */
    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "rfid_uid", nullable = false, length = 64)
    private String rfidUid;

    @Column(name = "titik_kasir_id", nullable = false)
    private Long titikKasirId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subjek_tipe", length = 20)
    private SubjekTipe subjekTipe;

    @Column(name = "subjek_id")
    private Long subjekId;

    @Column(name = "petugas_id", nullable = false)
    private Long petugasId;

    /** Total belanja yang dihitung saat tap (rupiah). */
    @Column(name = "total", nullable = false)
    private Long total;

    /** Snapshot item {@code [{menuId,qty}, ...]} (JSON). */
    @Column(name = "items_json", nullable = false, columnDefinition = "TEXT")
    private String itemsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private StatusPendingTap status;

    /** ID transaksi hasil konfirmasi (null sampai dikonfirmasi). */
    @Column(name = "transaksi_id")
    private Long transaksiId;

    @Column(name = "dibuat_at", nullable = false)
    private OffsetDateTime dibuatAt;

    @Column(name = "kedaluwarsa_at")
    private OffsetDateTime kedaluwarsaAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void isiWaktu() {
        OffsetDateTime now = OffsetDateTime.now();
        if (dibuatAt == null) {
            dibuatAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    public boolean menunggu() {
        return status == StatusPendingTap.MENUNGGU;
    }

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
