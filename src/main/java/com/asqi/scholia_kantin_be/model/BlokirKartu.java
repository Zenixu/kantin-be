package com.asqi.scholia_kantin_be.model;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
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
 * Status blokir kartu per subjek — PRD §6.1 tahap 2, §8.3 (kontrol ortu).
 *
 * <p>Kunci = {@code (sekolah_id, subjek_tipe, subjek_id)}: blokir melekat ke
 * siswa (saldo terikat siswa) atau Kartu Tamu (PRD §9.4).
 *
 * <p><b>Berlaku instan / tanpa cache (PRD §11.11):</b> baris ini dibaca server
 * pada <b>setiap</b> tap — tidak ada cache berbasis waktu kedaluwarsa. Karena
 * itu {@code TapValidator} selalu menolak tap setelah blokir tersimpan.
 *
 * <p>{@code id} di-assign aplikasi ({@code IdGenerator}) → memakai
 * {@link Persistable} agar Hibernate tahu INSERT vs UPDATE.
 */
@Entity
@Table(name = "blokir_kartu")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlokirKartu implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    /** Penanda transient: true = baru (INSERT), false = sudah dimuat (UPDATE). */
    @Transient
    @Builder.Default
    private boolean baru = true;

    @Column(name = "sekolah_id", nullable = false)
    private Long sekolahId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subjek_tipe", nullable = false, length = 20)
    private SubjekTipe subjekTipe;

    @Column(name = "subjek_id", nullable = false)
    private Long subjekId;

    /** {@code true} = kartu diblokir (tap ditolak). */
    @Column(name = "diblokir", nullable = false)
    private boolean diblokir;

    /** Alasan blokir (audit/tampilan). */
    @Column(name = "alasan", length = 500)
    private String alasan;

    @Column(name = "diubah_oleh")
    private Long diubahOleh;

    @Column(name = "diubah_pada", nullable = false)
    private OffsetDateTime diubahPada;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

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
