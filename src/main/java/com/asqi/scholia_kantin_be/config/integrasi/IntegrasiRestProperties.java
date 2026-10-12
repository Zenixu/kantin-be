package com.asqi.scholia_kantin_be.config.integrasi;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfigurasi adapter REST nyata untuk 6 port integrasi (issue <b>#160</b>).
 *
 * <p><b>Kenapa terpisah dari {@code kantin.notifikasi} / {@code kantin.aktivasi-modul}?</b>
 * Properti lama itu adalah <i>saklar fitur</i> (mis. {@code kantin.notifikasi.enabled}
 * menentukan apakah fasad mengirim atau melewati). Blok ini khusus
 * <b>menyalakan adapter REST</b> pengganti {@code *Fallback}. Karena itu tiap
 * adapter punya saklar {@code kantin.integrasi.<nama>.enabled} yang
 * <b>default {@code false}</b>:
 * <ul>
 *   <li>{@code false} (default) ⇒ adapter <b>tidak</b> didaftarkan sebagai bean,
 *       sehingga {@code *Fallback} tetap dipakai — perilaku dev/demo/test tidak
 *       berubah, dan tidak ada panggilan keluar tak terduga;</li>
 *   <li>{@code true} ⇒ adapter REST didaftarkan sebagai {@code @Primary} dan
 *       menang atas {@code *Fallback}.</li>
 * </ul>
 *
 * <p><b>Tanpa ubah logika bisnis:</b> URL/endpoint/timeout seluruhnya di sini —
 * menyalakan adapter cukup lewat environment, tidak menyentuh kode service.
 *
 * <p>Endpoint default mengikuti kontrak yang sudah diputuskan tim BE
 * (OPEN-QUESTIONS Q3–Q8). Nilai bisa di-override lewat env
 * ({@code KANTIN_INTEGRASI_*}).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "kantin.integrasi")
public class IntegrasiRestProperties {

    /** Adapter posting Buku Kas ke admin-be (Q3, INTEGRATIONS.md §3). */
    private Endpoint bukukas = new Endpoint();

    /** Adapter kirim notifikasi ke mobile-be (Q5, §6). */
    private Endpoint notifikasi = new Endpoint();

    /** Adapter lookup kartu RFID → siswa di admin-be (Q7, §4). */
    private Endpoint kartuLookup = new Endpoint();

    /** Adapter status siswa + blokir kartu di admin-be (Q7, §4.4). */
    private StatusSiswa statusSiswa = new StatusSiswa();

    /** Adapter anti-tabrakan UID ke admin-be (Q7, §4.2). */
    private Endpoint uidSiswa = new Endpoint();

    /** Adapter status aktivasi modul & fee platform ke internal-be (Q6, §7). */
    private Endpoint aktivasi = new Endpoint();

    /**
     * Penjaga kesiapan produksi (issue <b>#164</b>). Bila {@code true}, saat
     * aplikasi siap ({@code ApplicationReadyEvent}) kantin-be <b>menolak start</b>
     * kecuali <b>keenam</b> adapter REST aktif + {@code base-url} terisi
     * <b>dan</b> public key RS256 admin-be &amp; mobile-be terpasang.
     *
     * <p>Default {@code false} (dev/demo/test tetap memakai {@code *Fallback}).
     * Set {@code true} di <b>staging/produksi</b> agar tidak ada {@code *Fallback}
     * yang diam-diam aktif. Lihat {@link PemeriksaIntegrasiProduksi}.
     */
    private boolean wajibAktif = false;

    /**
     * Satu endpoint REST yang bisa dinyalakan/dimatikan.
     *
     * <p>{@link #path} relatif terhadap {@link #baseUrl}. {@link #timeoutMs}
     * dipakai untuk <i>connect</i> &amp; <i>read</i> timeout. {@link #maxAttempts}
     * &gt; 1 mengaktifkan retry berbatas untuk kegagalan jaringan/5xx (dengan
     * backoff linear {@link #backoffMs}); default 1 = sekali coba (aman untuk
     * jalur tap yang sensitif latensi).
     */
    @Getter
    @Setter
    public static class Endpoint {

        /** Saklar adapter. Default {@code false} ⇒ {@code *Fallback} tetap dipakai. */
        private boolean enabled = false;

        /** Base URL layanan tujuan (mis. {@code https://admin-be.internal}). */
        private String baseUrl = "";

        /** Path endpoint relatif terhadap {@link #baseUrl}. */
        private String path = "";

        /** Timeout (ms) connect &amp; read. */
        private int timeoutMs = 3000;

        /** Jumlah percobaan maksimum (>=1). 1 = tanpa retry. */
        private int maxAttempts = 1;

        /** Jeda dasar (ms) antar percobaan; percobaan ke-n menunggu {@code n * backoffMs}. */
        private long backoffMs = 200;

        /**
         * Token Bearer opsional (mis. JWT mesin-ke-mesin). Bila diisi, adapter
         * menambahkan header {@code Authorization: Bearer <token>}. Kosong =
         * tanpa header (endpoint publik / belum butuh auth).
         */
        private String bearerToken = "";

        /**
         * Rahasia bersama HMAC-SHA256 opsional (pola endpoint internal, issue #29).
         * Bila diisi, adapter menandatangani {@code timestamp + "." + body}
         * (body kosong untuk GET) dan mengirim header {@code X-Internal-Timestamp}
         * &amp; {@code X-Internal-Signature}. Kosong = tanpa tanda tangan.
         */
        private String secret = "";
    }

    /** Konfigurasi adapter status siswa — dua endpoint (baca status &amp; blokir kartu). */
    @Getter
    @Setter
    public static class StatusSiswa extends Endpoint {

        /** Path blokir kartu siswa (best-effort). Default mengikuti usulan kontrak Q7. */
        private String blokirPath = "/api/siswa/kartu/blokir";
    }
}
