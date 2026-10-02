package com.asqi.scholia_kantin_be.dto;

import com.asqi.scholia_kantin_be.validation.UidKartuValid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Permintaan satu kali tap di kasir (PRD §6.1).
 *
 * <p><b>Idempotency (PRD §11.3):</b> {@code idempotencyKey} dibuat oleh
 * <b>klien kasir</b>, unik per tap. Tap ganda dengan key sama → dikembalikan
 * hasil yang sama (tidak memotong saldo dua kali).
 */
@Data
public class TapRequest {

    /** UID kartu yang dibaca bridge RFID. */
    @NotBlank(message = "UID kartu wajib diisi")
    @UidKartuValid
    private String rfidUid;

    /** Titik kasir tempat tap terjadi (wajib untuk audit & sesi). */
    @NotNull(message = "Titik kasir wajib diisi")
    private Long titikKasirId;

    /** Daftar item yang dibeli (id menu + qty). */
    @NotEmpty(message = "Minimal satu item harus dipilih")
    private List<ItemTap> items;

    /**
     * Kunci idempotency dari klien. Bila kosong, server menolak (wajib) —
     * jangan biarkan server membuatnya, karena retry klien tak akan idempoten.
     */
    @NotBlank(message = "Idempotency key wajib diisi")
    @Size(max = 64, message = "Idempotency key maksimal 64 karakter")
    private String idempotencyKey;

    /** Satu baris item dalam tap. */
    @Data
    public static class ItemTap {
        @NotNull(message = "ID menu wajib diisi")
        private Long menuId;

        @NotNull(message = "Jumlah wajib diisi")
        private Integer qty;
    }
}
