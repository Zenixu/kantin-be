package com.asqi.scholia_kantin_be.security;

import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.enums.SumberToken;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Membangun {@link IdentitasKantin} dari klaim JWT.
 *
 * <p>Karena format klaim SKOOLIA belum final (OPEN-QUESTIONS Q1/Q2), resolver
 * ini <b>toleran</b>: menerima beberapa nama klaim alternatif yang lazim, lalu
 * menormalkannya. Setelah format dikonfirmasi, persempit ke satu nama.
 *
 * <p><b>Fail-closed:</b> peran tak dikenal → {@link AktorKantin#TIDAK_DIKENAL};
 * sekolah tak diketahui → {@code sekolahId=null} (bukan menebak).
 */
@Component
@Slf4j
public class KlaimResolver {

    private static final List<String> KLAIM_USER_ID = List.of("user_id", "userId", "uid", "sub");
    private static final List<String> KLAIM_SEKOLAH_ID = List.of("sekolah_id", "sekolahId", "school_id", "schoolId");
    private static final List<String> KLAIM_ROLE = List.of("role", "roles", "authority", "peran");
    private static final List<String> KLAIM_NAMA = List.of("nama", "name", "full_name", "fullName");
    private static final List<String> KLAIM_SISWA_ID = List.of("siswa_id", "siswaId", "student_id", "studentId");

    public IdentitasKantin bangun(Claims claims, SumberToken sumber) {
        String userId = ambilString(claims, KLAIM_USER_ID);
        Long sekolahId = ambilLong(claims, KLAIM_SEKOLAH_ID);
        String roleMentah = ambilString(claims, KLAIM_ROLE);
        String nama = ambilString(claims, KLAIM_NAMA);
        Long siswaId = ambilLong(claims, KLAIM_SISWA_ID);

        AktorKantin peran = petakanPeran(roleMentah, sumber);

        if (userId == null) {
            // jangan lempar di sini — biarkan filter memutuskan (401)
            log.warn("Token {} tidak memuat user id yang dikenali. Klaim: {}", sumber, claims.keySet());
        }
        if (sekolahId == null) {
            log.warn("Token {} tidak memuat sekolah_id. Modul ter-scope tenant akan menolak akses.", sumber);
        }

        return IdentitasKantin.builder()
                .userId(userId)
                .nama(nama)
                .sekolahId(sekolahId)
                .peran(peran)
                .roleMentah(roleMentah)
                .sumber(sumber)
                .siswaId(siswaId)
                .build();
    }

    /**
     * Petakan string peran SKOOLIA → {@link AktorKantin}.
     *
     * <p>Pencocokan tidak peka huruf besar/kecil dan toleran variasi
     * (mis. {@code "Admin Sekolah"}, {@code "ADMIN_SEKOLAH"}).
     */
    AktorKantin petakanPeran(String role, SumberToken sumber) {
        if (sumber == SumberToken.MOBILE) {
            return AktorKantin.ORANG_TUA;
        }
        if (role == null || role.isBlank()) {
            return AktorKantin.TIDAK_DIKENAL;
        }
        String r = role.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        if (r.contains("PENGELOLA") || r.contains("KANTIN")) {
            return AktorKantin.PENGELOLA_KANTIN;
        }
        if (r.contains("BENDAHARA") || r.contains("TATA_USAHA") || r.equals("TU")) {
            return AktorKantin.TU_SEKOLAH;
        }
        if (r.contains("ADMIN") || r.contains("KEPSEK") || r.contains("KEPALA_SEKOLAH")) {
            return AktorKantin.ADMIN_SEKOLAH;
        }
        if (r.contains("PETUGAS") || r.contains("KASIR")) {
            return AktorKantin.PETUGAS_KANTIN;
        }
        if (r.contains("ORANG_TUA") || r.contains("ORTU") || r.contains("PARENT")) {
            return AktorKantin.ORANG_TUA;
        }
        return AktorKantin.TIDAK_DIKENAL;
    }

    // ────────────────────────────────────────────────────────────────
    // Util klaim
    // ────────────────────────────────────────────────────────────────

    private String ambilString(Claims claims, List<String> kandidat) {
        for (String k : kandidat) {
            Object v = claims.get(k);
            if (v != null) {
                String s = stringDari(v);
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }

    /**
     * Ubah nilai klaim menjadi string, menormalkan angka.
     *
     * <p><b>Kenapa perlu:</b> jjwt-gson mendeserialisasi angka JSON apa pun
     * menjadi {@link Double} — klaim {@code user_id} bernilai {@code 42} dari
     * admin-be terbaca sebagai {@code 42.0}. Akibatnya {@code String.valueOf}
     * menghasilkan {@code "42.0"} dan {@code Long.valueOf("42.0")} di
     * {@code IdentitasKantin.aktorIdWajib()} GAGAL, sehingga seluruh endpoint
     * tulis (saldo, kartu tamu, katalog) error dengan token staf yang sah.
     * Angka integral dinormalkan ke bentuk tanpa pecahan ({@code "42"}).
     */
    private String stringDari(Object v) {
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (!Double.isNaN(d) && !Double.isInfinite(d)
                    && d == Math.rint(d)
                    && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
                return String.valueOf((long) d);
            }
            return String.valueOf(v).trim();
        }
        return String.valueOf(v).trim();
    }

    private Long ambilLong(Claims claims, List<String> kandidat) {
        for (String k : kandidat) {
            Object v = claims.get(k);
            if (v == null) {
                continue;
            }
            try {
                if (v instanceof Number n) {
                    return n.longValue();
                }
                return Long.valueOf(String.valueOf(v).trim());
            } catch (NumberFormatException ignored) {
                // lanjut ke kandidat berikutnya
            }
        }
        return null;
    }
}
