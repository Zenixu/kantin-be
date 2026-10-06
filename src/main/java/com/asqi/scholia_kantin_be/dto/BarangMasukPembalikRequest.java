package com.asqi.scholia_kantin_be.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Permintaan <b>barang masuk pembalik</b> (PRD §7.2) — koreksi barang masuk
 * yang salah input. Data asal <b>tidak</b> diedit/dihapus; sistem mencatat
 * mutasi pembalik baru.
 *
 * <p>{@code mutasiId} menunjuk baris barang masuk yang dibatalkan (didapat dari
 * {@code GET /api/stok/riwayat} atau hasil {@code POST /api/stok/barang-masuk}).
 * {@code qty} opsional: bila dikosongkan, seluruh sisa yang belum dibalik akan
 * dibatalkan (pembatalan penuh); bila diisi, hanya sejumlah itu (koreksi
 * sebagian, mis. salah input qty).
 *
 * <p>{@code referensiId} = nomor bukti pembalik (idempotency) — retry jaringan
 * dengan bukti sama tidak membalik dua kali.
 */
@Data
public class BarangMasukPembalikRequest {

    @NotNull(message = "ID barang masuk (mutasiId) wajib diisi")
    private Long mutasiId;

    /** Jumlah yang dibalik; kosong = balik seluruh sisa. */
    @Min(value = 1, message = "Qty pembalik harus > 0")
    private Integer qty;

    @NotBlank(message = "Alasan pembalik wajib diisi (PRD §7.2)")
    @Size(max = 255)
    private String alasan;

    @NotBlank(message = "Nomor bukti pembalik wajib diisi (idempotency)")
    @Size(max = 60)
    private String referensiId;
}
