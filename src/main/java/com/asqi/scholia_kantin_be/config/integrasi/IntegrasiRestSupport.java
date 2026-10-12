package com.asqi.scholia_kantin_be.config.integrasi;

import com.asqi.scholia_kantin_be.config.integrasi.IntegrasiRestProperties.Endpoint;
import com.asqi.scholia_kantin_be.config.webhook.TandaTanganWebhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Dasar bersama 6 adapter REST integrasi (issue <b>#160</b>).
 *
 * <p>Menyatukan tiga hal yang sama di semua adapter:
 * <ol>
 *   <li><b>Membangun {@link RestClient}</b> dari {@link Endpoint} (base URL +
 *       timeout connect/read) — URL tidak dikunci di kode.</li>
 *   <li><b>Retry berbatas</b> untuk kegagalan jaringan/timeout &amp; 5xx (dengan
 *       backoff linear). 4xx <b>tidak</b> di-retry (salah permintaan — percobaan
 *       ulang sia-sia).</li>
 *   <li><b>Parsing JSON</b> (Jackson 3) dengan pembacaan {@code data} yang
 *       toleran: admin-be membungkus payload di {@code {code,message,data}},
 *       sedangkan internal-be memakai bentuk datar — helper ini menerima
 *       keduanya.</li>
 * </ol>
 *
 * <p><b>Kontrak penting:</b> adapter <b>tidak pernah</b> membatalkan transaksi
 * kasir. Semua kegagalan integrasi diterjemahkan menjadi hasil
 * <i>fail-open/fail-safe</i> di masing-masing adapter (PRD §8.4, §11.11) —
 * kelas ini menyediakan {@link #coba} agar pola itu konsisten.
 */
@Slf4j
public abstract class IntegrasiRestSupport {

    /** Mapper JSON bersama (Jackson 3 — paket {@code tools.jackson}). */
    protected static final JsonMapper JSON = JsonMapper.builder().build();

    /** Nama adapter untuk log (mis. {@code BukuKas}). */
    private final String nama;

    /** Konfigurasi endpoint adapter. */
    private final Endpoint konfigurasi;

    /** Klien HTTP siap pakai (base URL + timeout sudah terpasang). */
    private final RestClient klien;

    protected IntegrasiRestSupport(String nama, Endpoint konfigurasi) {
        this.nama = nama;
        this.konfigurasi = konfigurasi;
        this.klien = bangun(nama, konfigurasi);
    }

    private static RestClient bangun(String nama, Endpoint cfg) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(Math.max(1, cfg.getTimeoutMs()));
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    protected RestClient klien() {
        return klien;
    }

    protected Endpoint konfigurasi() {
        return konfigurasi;
    }

    /** Path efektif: {@code konfigurasi.path} bila diisi, jika tidak {@code bawaan}. */
    protected String path(String bawaan) {
        String p = konfigurasi.getPath();
        return (p == null || p.isBlank()) ? bawaan : p;
    }

    /**
     * Jalankan panggilan HTTP dengan retry berbatas.
     *
     * <p>Retry hanya untuk {@link ResourceAccessException} (timeout/I-O) dan
     * {@link HttpServerErrorException} (5xx). 4xx langsung dilempar keluar (tidak
     * di-retry). Percobaan ke-{@code n} menunggu {@code n * backoffMs}.
     *
     * @throws RuntimeException exception terakhir bila semua percobaan gagal
     */
    protected <T> T coba(String operasi, Supplier<T> panggilan) {
        int maks = Math.max(1, konfigurasi.getMaxAttempts());
        RuntimeException terakhir = null;
        for (int attempt = 1; attempt <= maks; attempt++) {
            try {
                return panggilan.get();
            } catch (HttpClientErrorException e) {
                // 4xx — salah permintaan; tidak ada gunanya diulang.
                throw e;
            } catch (HttpServerErrorException | ResourceAccessException e) {
                terakhir = e;
                log.warn("[{}] {} gagal (percobaan {}/{}): {}", nama, operasi, attempt, maks,
                        e.getMessage());
                if (attempt < maks) {
                    jeda(attempt);
                }
            }
        }
        throw terakhir;
    }

    private void jeda(int attempt) {
        try {
            Thread.sleep(Math.max(0, konfigurasi.getBackoffMs()) * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry integrasi terputus", e);
        }
    }

    /** Baca {@code data} dari amplop admin-be; bila tak ada, kembalikan root apa adanya. */
    protected JsonNode data(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode data = root.get("data");
        return (data == null || data.isNull()) ? root : data;
    }

    /** Bangun badan JSON sambil membuang field bernilai {@code null}. */
    protected static Map<String, Object> badan() {
        return new LinkedHashMap<>();
    }

    /** Tambah field bila nilainya tidak {@code null}. */
    protected static void isi(Map<String, Object> badan, String key, Object nilai) {
        if (nilai != null) {
            badan.put(key, nilai);
        }
    }

    /** Apakah status HTTP 2xx? */
    protected static boolean sukses(HttpStatusCode status) {
        return status != null && status.is2xxSuccessful();
    }

    /**
     * Terapkan header auth pada spesifikasi request bila dikonfigurasi.
     *
     * <p>Bila {@code bearerToken} diisi → header {@code Authorization}.
     * Bila {@code secret} diisi → tanda tangan HMAC-SHA256 atas
     * {@code timestamp + "." + body} (pola endpoint internal, issue #29) memakai
     * {@link TandaTanganWebhook}, ditambah {@code X-Internal-Timestamp}.
     *
     * @param spec  spesifikasi request yang sedang dibangun
     * @param badan badan request mentah (byte UTF-8); {@code null} untuk GET
     */
    protected <S> S beriHeader(S spec, byte[] badan) {
        org.springframework.web.client.RestClient.RequestHeadersSpec<?> s =
                (org.springframework.web.client.RestClient.RequestHeadersSpec<?>) spec;
        String token = konfigurasi.getBearerToken();
        if (token != null && !token.isBlank()) {
            s = s.header("Authorization", "Bearer " + token);
        }
        String rahasia = konfigurasi.getSecret();
        if (rahasia != null && !rahasia.isBlank()) {
            String ts = String.valueOf(System.currentTimeMillis() / 1000L);
            String sig = TandaTanganWebhook.hitung(rahasia, ts, badan == null ? new byte[0] : badan);
            s = s.header("X-Internal-Timestamp", ts)
                 .header("X-Internal-Signature", "sha256=" + sig);
        }
        @SuppressWarnings("unchecked")
        S hasil = (S) s;
        return hasil;
    }

    /** Serialisasi badan ke byte UTF-8 (untuk penandatanganan). */
    protected static byte[] bytesJson(Object badan) {
        return JSON.writeValueAsString(badan).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
