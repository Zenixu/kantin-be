package com.asqi.scholia_kantin_be.config.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Utilitas tanda tangan webhook — HMAC-SHA256 (SECURITY.md §5, BUGS-DITEMUKAN B34).
 *
 * <p>Skema yang dipakai (pola standar, mis. Stripe/GitHub):
 * <pre>
 *   payload ditandatangani = timestamp + "." + badan_request_mentah
 *   X-Webhook-Signature    = hex( HMAC-SHA256(rahasia, payload) )
 * </pre>
 * Timestamp ikut ditandatangani sehingga penyerang tidak bisa memakai ulang
 * signature sah dengan timestamp baru (menggeser jendela anti-replay).
 *
 * <p>Perbandingan signature memakai {@link MessageDigest#isEqual} —
 * <b>konstan-waktu</b>, agar tidak bocor lewat timing attack.
 */
public final class TandaTanganWebhook {

    private static final String ALGORITMA_HMAC = "HmacSHA256";
    private static final String PREFIX_SHA256 = "sha256=";

    private TandaTanganWebhook() {
    }

    /** Hitung HMAC-SHA256 hex atas {@code timestamp + "." + badan}. */
    public static String hitung(String rahasia, String timestamp, byte[] badan) {
        try {
            Mac mac = Mac.getInstance(ALGORITMA_HMAC);
            mac.init(new SecretKeySpec(rahasia.getBytes(StandardCharsets.UTF_8), ALGORITMA_HMAC));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(badan == null ? new byte[0] : badan);
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 dijamin ada; kunci kosong sudah dicegah verifier.
            throw new IllegalStateException("HMAC-SHA256 tidak dapat dipakai", e);
        }
    }

    /** SHA-256 hex badan request — sidik jari untuk deteksi payload berbeda. */
    public static String hashPayload(byte[] badan) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(badan == null ? new byte[0] : badan));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 tidak tersedia", e);
        }
    }

    /**
     * Bandingkan dua signature secara <b>konstan-waktu</b>. Menerima bentuk
     * polos {@code <hex>} maupun ber-prefix {@code sha256=<hex>}, tidak peduli
     * besar/kecil huruf.
     */
    public static boolean cocok(String diharapkan, String diberikan) {
        if (diharapkan == null || diberikan == null) {
            return false;
        }
        byte[] a = normalisasi(diharapkan).getBytes(StandardCharsets.US_ASCII);
        byte[] b = normalisasi(diberikan).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(a, b);
    }

    private static String normalisasi(String nilai) {
        String v = nilai.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith(PREFIX_SHA256)) {
            v = v.substring(PREFIX_SHA256.length()).trim();
        }
        return v;
    }
}
