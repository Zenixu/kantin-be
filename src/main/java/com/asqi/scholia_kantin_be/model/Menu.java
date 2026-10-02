package com.asqi.scholia_kantin_be.model;

import com.asqi.scholia_kantin_be.enums.SatuanMenu;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
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
 * Item katalog kantin (PRD §7.1).
 *
 * <p>Soft delete lewat {@code isActive}: menu nonaktif tidak boleh dijual.
 * Mengubah {@code hargaJual} <b>tidak</b> mengubah transaksi lama — transaksi
 * menyimpan snapshot harga (PRD §6.2); perubahan harga wajib tercatat di audit
 * log (dilakukan di layer service, PRD §7.1, §11.7).
 *
 * <p>{@code id} di-assign aplikasi ({@code IdGenerator}) → memakai
 * {@link Persistable} agar Hibernate tahu INSERT vs UPDATE.
 */
@Entity
@Table(name = "menu")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    /** Penanda transient: true = baru (INSERT), false = sudah dimuat (UPDATE). */
    @Transient
    @Builder.Default
    private boolean baru = true;

    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    @Column(name = "kategori_id")
    private Long kategoriId;

    @Column(name = "nama", nullable = false, length = 150)
    private String nama;

    /** Harga jual per unit, rupiah integer (PRD §11.6). */
    @Column(name = "harga_jual", nullable = false)
    private Long hargaJual;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "satuan", nullable = false, length = 20)
    private SatuanMenu satuan = SatuanMenu.PCS;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    /** Ambang peringatan restock (PRD §7.1, §7.5). */
    @Builder.Default
    @Column(name = "stok_minimum", nullable = false)
    private Integer stokMinimum = 0;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return baru;
    }

    @PostLoad
    @PostPersist
    void tandaiLama() {
        this.baru = false;
    }
}
