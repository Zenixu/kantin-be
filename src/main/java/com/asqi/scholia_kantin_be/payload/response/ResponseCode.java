package com.asqi.scholia_kantin_be.payload.response;

import lombok.Getter;

/**
 * Kode respons internal kantin-be.
 *
 * <p>Pola mengikuti {@code admin-be} (AGENTS.md §5) agar FE/layanan lain yang
 * sudah terbiasa dengan SKOOLIA tidak perlu belajar format baru.
 *
 * <p><b>CATATAN PENTING (temuan bug admin-be):</b> {@code admin-be} punya DUA
 * enum bernama nyaris sama — {@code library.ResponseCode} dan
 * {@code payload.response.ResponseCode} — dengan nilai berbeda. Itu membingungkan
 * dan pernah membuat handler salah peta. Di kantin-be hanya ada SATU
 * {@code ResponseCode}, yaitu kelas ini. Jangan buat duplikatnya.
 */
@Getter
public enum ResponseCode {
    SUCCESS(200, "OK"),
    NO_END_POINT(404, "ENDPOINT NOT FOUND"),
    NO_DATA(101, "DATA TIDAK DITEMUKAN"),
    VALIDATION(100, "ERROR MAPPING"),
    TYPE_MISMATCH(103, "ERROR MAPPING"),
    BAD_REQUEST(400, "BAD REQUEST"),
    UNAUTHORIZED(401, "Token tidak valid atau kedaluwarsa"),
    FORBIDDEN(403, "FORBIDDEN"),
    CONFLICT(409, "KONFLIK DATA"),
    TOO_MANY_REQUESTS(429, "TERLALU BANYAK PERMINTAAN"),
    SERVER_ERROR(500, "INTERNAL SERVER ERROR"),
    DATABASE_ERROR(501, "INTERNAL SERVER ERROR");

    private final Integer code;
    private final String message;

    ResponseCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
