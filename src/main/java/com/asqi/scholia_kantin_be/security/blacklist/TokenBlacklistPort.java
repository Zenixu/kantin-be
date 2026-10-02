package com.asqi.scholia_kantin_be.security.blacklist;

/**
 * Port pencabutan (revokasi) token akses.
 *
 * <p><b>Konteks (ADR-0002):</b> kantin-be tidak punya login/logout sendiri —
 * token diterbitkan admin-be/mobile-be. Karena itu blacklist di sini berfungsi
 * untuk <b>mencabut token yang dikelola platform</b>, misalnya:
 * <ul>
 *   <li>akun dinonaktifkan / karyawan keluar (dicabut admin sekolah),</li>
 *   <li>token dilaporkan bocor,</li>
 *   <li>sesi dipaksa berakhir dari sisi SKOOLIA.</li>
 * </ul>
 *
 * <p>Implementasi WAJIB <b>fail-open</b>: bila penyimpanan (Redis) tak dapat
 * dihubungi, token <b>dianggap belum dicabut</b> — gangguan infra tidak boleh
 * melumpuhkan kantin. Ini konsisten dengan kebijakan rate limit (B27).
 */
public interface TokenBlacklistPort {

    /**
     * Cabut sebuah token sampai waktu kedaluwarsanya.
     *
     * @param token             token akses mentah (JWT)
     * @param detikSampaiKedaluwarsa sisa umur token dalam detik; bila {@code <= 0}
     *                          tidak ada yang disimpan (token toh sudah mati)
     */
    void cabut(String token, long detikSampaiKedaluwarsa);

    /**
     * Periksa apakah token sudah dicabut.
     *
     * @return {@code true} bila token ada di blacklist; {@code false} bila tidak
     *         ada <b>atau</b> penyimpanan sedang tidak dapat dihubungi (fail-open)
     */
    boolean tercabut(String token);
}
