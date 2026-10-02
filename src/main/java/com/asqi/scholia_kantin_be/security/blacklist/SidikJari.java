package com.asqi.scholia_kantin_be.security.blacklist;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Util sidik jari token.
 *
 * <p>Blacklist <b>tidak menyimpan token mentah</b> — hanya SHA-256-nya. Alasannya:
 * bila Redis bocor/diintip, penyerang tidak langsung mendapat token yang bisa
 * dipakai (berbeda dari menyimpan token apa adanya). TTL sudah membatasi umur
 * entri, jadi tanpa salt pun aman untuk kasus ini.
 */
public final class SidikJari {

    private SidikJari() {
    }

    /** SHA-256 heksadesimal dari token. */
    public static String dari(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 dijamin ada di setiap JVM — ini tak seharusnya terjadi.
            throw new IllegalStateException("SHA-256 tidak tersedia", e);
        }
    }
}
