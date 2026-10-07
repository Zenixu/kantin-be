package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.dto.CekUidKartuTamuResponse;
import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import com.asqi.scholia_kantin_be.service.kartu.KartuTamuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint <b>internal</b> kantin-be (mesin-ke-mesin) — issue <b>#29</b>.
 *
 * <p>Bukan endpoint ber-token user: autentikasinya <b>HMAC-SHA256 + anti-replay</b>
 * yang dijalankan {@code InternalSignatureFilter} sebelum request sampai sini
 * ({@code /api/internal/**}, rahasia {@code KANTIN_INTERNAL_SECRET}).
 *
 * <p>Kegunaan: admin-be menanyakan apakah sebuah {@code rfid_uid} sudah dipakai
 * <b>Kartu Tamu</b>, agar {@code SiswaService} menolak UID yang sudah dipakai
 * Kartu Tamu — melengkapi anti-tabrakan UID dua arah (INTEGRATIONS.md §4.2).
 */
@RestController
@RequestMapping("api/internal")
@RequiredArgsConstructor
public class InternalController {

    private final KartuTamuService kartuTamuService;

    /**
     * Cek apakah {@code rfidUid} sudah dipakai Kartu Tamu (aktif maupun tidak).
     *
     * <p>UID bersifat UNIQUE global ⇒ pengecekan lintas sekolah.
     *
     * @param rfidUid UID yang mau dicek
     * @param sekolahId tenant asal permintaan (untuk jejak/diagnostik)
     */
    @GetMapping("kartu-tamu/cek-uid")
    public ResponseEntity<Response<CekUidKartuTamuResponse>> cekUidKartuTamu(
            @RequestParam String rfidUid,
            @RequestParam(required = false) Long sekolahId) {
        boolean dipakai = kartuTamuService.dipakaiKartuTamu(rfidUid);
        CekUidKartuTamuResponse hasil = CekUidKartuTamuResponse.builder()
                .sekolahId(sekolahId)
                .rfidUid(rfidUid)
                .dipakai(dipakai)
                .build();
        return CommonResponse.data(hasil);
    }
}
