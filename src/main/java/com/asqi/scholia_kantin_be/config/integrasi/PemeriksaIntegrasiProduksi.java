package com.asqi.scholia_kantin_be.config.integrasi;

import com.asqi.scholia_kantin_be.config.integrasi.IntegrasiRestProperties.Endpoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Penjaga kesiapan integrasi produksi (issue <b>#164</b>).
 *
 * <p>Menegakkan acceptance criteria #164 — <i>"tidak ada {@code *Fallback} yang
 * aktif di profil staging/produksi"</i> — tanpa menunggu kredensial tim SKOOLIA.
 * Saat {@code kantin.integrasi.wajib-aktif=true}, aplikasi <b>menolak start</b>
 * bila salah satu dari berikut belum siap:
 * <ol>
 *   <li>salah satu dari <b>6 adapter REST</b> belum aktif
 *       ({@code kantin.integrasi.<nama>.enabled=false}) — artinya {@code *Fallback}
 *       masih dipakai; atau</li>
 *   <li>adapter aktif tetapi {@code base-url} kosong (panggilan akan gagal); atau</li>
 *   <li>public key <b>RS256</b> admin-be / mobile-be belum terpasang
 *       ({@code jwt.admin-public-key} / {@code jwt.mobile-public-key}) — token
 *       produksi tidak dapat diverifikasi (PRD §11.10).</li>
 * </ol>
 *
 * <p><b>Default {@code wajib-aktif=false}</b> agar dev/demo/test (yang memang
 * memakai {@code *Fallback} karena kontrak belum final) tetap berjalan. Bila
 * {@code false}, penjaga ini hanya mencatat INFO dan tidak menghalangi start.
 *
 * <p>Pola mengikuti {@code PemeriksaTopologiDatabase} (ADR-0010/Q11): kegagalan
 * bersifat <b>fail-closed</b> — lebih baik gagal start daripada diam-diam
 * memakai fallback di produksi.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PemeriksaIntegrasiProduksi {

    private final IntegrasiRestProperties properties;
    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void periksa() {
        if (!properties.isWajibAktif()) {
            log.info("Penjaga integrasi produksi NONAKTIF (kantin.integrasi.wajib-aktif=false). "
                    + "Aktifkan di staging/produksi agar tidak ada *Fallback yang dipakai (issue #164).");
            return;
        }

        List<String> masalah = new ArrayList<>();

        for (Adapter adapter : adapter()) {
            if (!adapter.endpoint().isEnabled()) {
                masalah.add("adapter '" + adapter.nama() + "' belum aktif "
                        + "(kantin.integrasi." + adapter.kunci() + ".enabled=false)");
            } else if (kosong(adapter.endpoint().getBaseUrl())) {
                masalah.add("adapter '" + adapter.nama() + "' aktif tetapi base-url kosong "
                        + "(kantin.integrasi." + adapter.kunci() + ".base-url)");
            }
        }

        // Nama properti WAJIB kebab (jwt.admin-public-key): itulah nama yang mengikat ke
        // JwtProperties.adminPublicKey. Bentuk dotted (jwt.admin.public-key) dianggap
        // nested dan TIDAK pernah terisi -> penjaga ini akan salah lapor (bug #204).
        if (kosong(environment.getProperty("jwt.admin-public-key"))) {
            masalah.add("public key RS256 admin-be kosong (jwt.admin-public-key / JWT_ADMIN_PUBLIC_KEY)");
        }
        if (kosong(environment.getProperty("jwt.mobile-public-key"))) {
            masalah.add("public key RS256 mobile-be kosong (jwt.mobile-public-key / JWT_MOBILE_PUBLIC_KEY)");
        }

        if (!masalah.isEmpty()) {
            throw new IllegalStateException(
                    "Integrasi produksi BELUM lengkap (kantin.integrasi.wajib-aktif=true). "
                            + "kantin-be masih akan memakai *Fallback untuk: " + String.join("; ", masalah)
                            + ". Lengkapi kredensial/kontrak dari tim SKOOLIA (issue #164), "
                            + "atau matikan kantin.integrasi.wajib-aktif di profil non-produksi.");
        }

        log.info("Penjaga integrasi produksi: {} adapter REST aktif + public key RS256 (admin & mobile) "
                + "terpasang — tidak ada *Fallback yang dipakai (issue #164).", adapter().size());
    }

    private List<Adapter> adapter() {
        return List.of(
                new Adapter("Buku Kas", "bukukas", properties.getBukukas()),
                new Adapter("Notifikasi", "notifikasi", properties.getNotifikasi()),
                new Adapter("Lookup kartu", "kartu-lookup", properties.getKartuLookup()),
                new Adapter("Status siswa", "status-siswa", properties.getStatusSiswa()),
                new Adapter("UID siswa", "uid-siswa", properties.getUidSiswa()),
                new Adapter("Aktivasi modul", "aktivasi", properties.getAktivasi()));
    }

    private static boolean kosong(String nilai) {
        return nilai == null || nilai.isBlank();
    }

    /** Satu adapter integrasi: nama tampil, kunci properti, dan konfigurasi endpoint-nya. */
    private record Adapter(String nama, String kunci, Endpoint endpoint) {
    }
}
