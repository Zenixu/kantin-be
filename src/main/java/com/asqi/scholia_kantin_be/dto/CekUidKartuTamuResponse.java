package com.asqi.scholia_kantin_be.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Hasil cek apakah sebuah {@code rfidUid} dipakai Kartu Tamu (issue <b>#29</b>).
 *
 * <p>Dipakai endpoint internal {@code GET /api/internal/kartu-tamu/cek-uid} yang
 * dipanggil admin-be (mesin-ke-mesin, HMAC) agar {@code SiswaService} menolak
 * {@code rfid_uid} yang sudah dipakai Kartu Tamu (anti-tabrakan UID).
 */
@Data
@Builder
public class CekUidKartuTamuResponse {

    /** Tenant yang dicek. */
    private Long sekolahId;

    /** UID yang dicek. */
    private String rfidUid;

    /** {@code true} bila UID sudah dipakai sebuah Kartu Tamu (aktif maupun tidak). */
    private boolean dipakai;
}
