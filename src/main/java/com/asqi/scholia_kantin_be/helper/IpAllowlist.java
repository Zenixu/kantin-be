package com.asqi.scholia_kantin_be.helper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Daftar izin (allowlist) alamat IP / rentang CIDR untuk webhook
 * (SECURITY.md §5, BUGS-DITEMUKAN B34).
 *
 * <p>Mendukung alamat IPv4, IPv6, dan CIDR ({@code 10.0.0.0/8}). Daftar kosong
 * berarti <b>tidak membatasi</b> — verifikasi signature tetap menjadi kontrol
 * utama; allowlist hanyalah lapisan tambahan bila alamat pengirim tetap.
 *
 * <p>Hanya menerima <b>literal</b> IP (tanpa DNS lookup) agar tidak ada
 * ketergantungan jaringan saat memeriksa request.
 */
public final class IpAllowlist {

    private static final Pattern IPV4 =
            Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private final List<Aturan> aturan;

    private IpAllowlist(List<Aturan> aturan) {
        this.aturan = aturan;
    }

    /** Bangun allowlist dari daftar entri (IP atau CIDR); entri tak valid diabaikan. */
    public static IpAllowlist dari(List<String> entri) {
        List<Aturan> hasil = new ArrayList<>();
        if (entri != null) {
            for (String e : entri) {
                if (e == null || e.isBlank()) {
                    continue;
                }
                Aturan a = Aturan.dari(e.trim());
                if (a != null) {
                    hasil.add(a);
                }
            }
        }
        return new IpAllowlist(hasil);
    }

    /** {@code true} bila tidak ada aturan (berarti semua IP lolos). */
    public boolean kosong() {
        return aturan.isEmpty();
    }

    /** Apakah alamat IP diizinkan. Allowlist kosong ⇒ selalu {@code true}. */
    public boolean mengizinkan(String ip) {
        if (aturan.isEmpty()) {
            return true;
        }
        if (ip == null || ip.isBlank()) {
            return false;
        }
        byte[] b = dariLiteral(ip.trim());
        if (b == null) {
            return false;
        }
        for (Aturan a : aturan) {
            if (a.cocok(b)) {
                return true;
            }
        }
        return false;
    }

    /** Satu aturan = jaringan (byte) + panjang prefiks (bit). */
    private record Aturan(byte[] jaringan, int prefiks) {

        static Aturan dari(String entri) {
            int slash = entri.indexOf('/');
            String ipBagian = slash >= 0 ? entri.substring(0, slash) : entri;
            byte[] j = dariLiteral(ipBagian);
            if (j == null) {
                return null;
            }
            int prefiks = j.length * 8;
            if (slash >= 0) {
                try {
                    prefiks = Integer.parseInt(entri.substring(slash + 1).trim());
                } catch (NumberFormatException e) {
                    return null;
                }
                if (prefiks < 0 || prefiks > j.length * 8) {
                    return null;
                }
            }
            return new Aturan(j, prefiks);
        }

        boolean cocok(byte[] b) {
            if (b.length != jaringan.length) {
                return false;
            }
            int bytePenuh = prefiks / 8;
            int sisaBit = prefiks % 8;
            for (int i = 0; i < bytePenuh; i++) {
                if (jaringan[i] != b[i]) {
                    return false;
                }
            }
            if (sisaBit == 0) {
                return true;
            }
            int mask = (0xFF << (8 - sisaBit)) & 0xFF;
            return (jaringan[bytePenuh] & mask) == (b[bytePenuh] & mask);
        }
    }

    /** Parse literal IP → byte[] (4 atau 16 byte); {@code null} bila bukan literal. */
    static byte[] dariLiteral(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        Matcher m = IPV4.matcher(ip);
        if (m.matches()) {
            byte[] out = new byte[4];
            for (int i = 1; i <= 4; i++) {
                int v = Integer.parseInt(m.group(i));
                if (v > 255) {
                    return null;
                }
                out[i - 1] = (byte) v;
            }
            return out;
        }
        // IPv6 literal selalu memuat ':' → getByName tidak melakukan DNS.
        if (ip.indexOf(':') >= 0) {
            try {
                return normalkan(InetAddress.getByName(ip).getAddress());
            } catch (UnknownHostException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * IPv4-mapped IPv6 ({@code ::ffff:a.b.c.d}) → 4 byte, agar sebanding dengan
     * aturan IPv4 (beberapa stack dual-stack mengembalikan bentuk ini).
     */
    private static byte[] normalkan(byte[] b) {
        if (b.length == 16) {
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                if (b[i] != 0) {
                    mapped = false;
                    break;
                }
            }
            if (mapped && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF) {
                return new byte[]{b[12], b[13], b[14], b[15]};
            }
        }
        return b;
    }
}
