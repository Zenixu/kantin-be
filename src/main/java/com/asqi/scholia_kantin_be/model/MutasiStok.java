package com.asqi.scholia_kantin_be.model;

import com.asqi.scholia_kantin_be.enums.ArahStok;
import com.asqi.scholia_kantin_be.enums.JenisMutasiStok;
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
 * Mutasi stok — baris buku besar (ledger) stok per menu.
 *
 * <p><b>Append-only (PRD §11.1):</b> {@link Immutable} — tidak ada UPDATE/DELETE.
 * Trigger DB {@code trg_mutasi_stok_append_only} menjadi lapisan kedua.
 *
 * <p>{@code hpp_snapshot} menyimpan HPP/unit saat mutasi agar laba lama tidak
 * berubah dan void bisa mengembalikan stok dengan HPP yang benar (PRD §7.4).
 */
@Entity
@Table(name = "mutasi_stok")
@Immutable
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MutasiStok implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Enumerated(EnumType.STRING)
    @Column(name = "arah", nullable = false, length = 10)
    private ArahStok arah;

    @Enumerated(EnumType.STRING)
    @Column(name = "jenis", nullable = false, length = 40)
    private JenisMutasiStok jenis;

    /** Selalu positif; tanda ditentukan {@link #arah}. */
    @Column(name = "qty", nullable = false)
    private Integer qty;

    /** Snapshot stok setelah mutasi — audit &amp; deteksi drift. */
    @Column(name = "stok_setelah", nullable = false)
    private Integer stokSetelah;

    /** HPP/unit saat mutasi (rupiah integer). */
    @Column(name = "hpp_snapshot")
    private Long hppSnapshot;

    /**
     * Harga beli/unit (khusus {@code BARANG_MASUK}) — dasar koreksi pembalik
     * agar HPP rata-rata bisa dihitung ulang dengan harga beli aslinya.
     */
    @Column(name = "harga_beli_satuan")
    private Long hargaBeliSatuan;

    /**
     * Baris {@code BARANG_MASUK} yang dibalik — terisi hanya untuk mutasi
     * {@code BARANG_MASUK_PEMBALIK} (jejak koreksi, PRD §7.2).
     */
    @Column(name = "mutasi_asal_id")
    private Long mutasiAsalId;

    @Column(name = "transaksi_id")
    private Long transaksiId;

    @Column(name = "referensi_tipe", length = 40)
    private String referensiTipe;

    @Column(name = "referensi_id", length = 64)
    private String referensiId;

    /** Wajib untuk penyesuaian/opname (PRD §7.3). */
    @Column(name = "alasan", length = 255)
    private String alasan;

    @Column(name = "aktor_id")
    private Long aktorId;

    /**
     * Snapshot nama aktor saat mutasi dicatat (klaim JWT {@code nama}).
     * {@code null} untuk aksi sistem/scheduler &amp; baris lama (issue #99).
     */
    @Column(name = "aktor_nama", length = 150)
    private String aktorNama;

    @Column(name = "waktu", nullable = false)
    private OffsetDateTime waktu;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

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
     * Selalu {@code true}: ledger stok hanya boleh INSERT (PRD §11.1). Membuat
     * {@code save()} memakai {@code persist()}, bukan {@code merge()}.
     */
    @Override
    public boolean isNew() {
        return true;
    }
}
