package com.asqi.scholia_kantin_be.service.integrasi;

/**
 * Port lookup kartu RFID → pemilik (siswa / Kartu Tamu) + status kontrol.
 *
 * <p><b>Kenapa port (interface)?</b> Implementasi nyata bergantung pada
 * kontrak API internal admin-be (OPEN-QUESTIONS <b>Q7</b>) yang belum final.
 * Dengan memisahkan port, seluruh alur tap di {@code TapService} bisa ditulis,
 * diuji (dengan fake), dan <b>tidak terblokir</b>. Ketika Q7 terjawab, cukup
 * ganti implementasi port ini (mis. {@code SiswaKartuClient} berbasis REST)
 * tanpa menyentuh logika bisnis.
 *
 * <p><b>Wajib</b> mematuhi PRD §11.11: status blokir <b>tidak</b> boleh
 * di-cache — implementasi harus memeriksa server setiap pemanggilan.
 */
public interface KartuLookupPort {

    /**
     * Cari pemilik kartu berdasarkan UID.
     *
     * @param sekolahId tenant pemanggil (PRD §11.4 — kartu sekolah lain =
     *                  tidak dikenal)
     * @param rfidUid   UID kartu dari bridge RFID
     * @return info kartu; {@link InfoKartu#tidakDikenal()} bila tidak ada
     */
    InfoKartu cariBerdasarkanUid(Long sekolahId, String rfidUid);
}
