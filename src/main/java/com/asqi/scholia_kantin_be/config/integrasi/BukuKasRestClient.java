package com.asqi.scholia_kantin_be.config.integrasi;

import com.asqi.scholia_kantin_be.config.integrasi.IntegrasiRestProperties.Endpoint;
import com.asqi.scholia_kantin_be.service.integrasi.BukuKasPort;
import com.asqi.scholia_kantin_be.service.integrasi.HasilPostingBukuKas;
import com.asqi.scholia_kantin_be.service.integrasi.PerintahBukuKas;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Adapter REST nyata {@link BukuKasPort} → admin-be (INTEGRATIONS.md §3, issue
 * <b>#160</b>).
 *
 * <p><b>Kapan hidup?</b> Hanya bila {@code kantin.integrasi.bukukas.enabled=true}
 * (default <b>false</b>). Saat hidup ia {@code @Primary} sehingga menang atas
 * {@link com.asqi.scholia_kantin_be.service.integrasi.BukuKasFallback}; saat mati
 * fallback tetap dipakai (dev/demo/test tak berubah).
 *
 * <p><b>Idempotency (INTEGRATIONS.md §3.4 poin 2).</b> Buku Kas admin-be
 * <b>tidak</b> idempoten, jadi {@code refId} deterministik dari pemanggil
 * ({@code BukuKasPostingService}) dikirim apa adanya — retry mengarah ke entri
 * yang sama. Adapter tidak menyimpan state.
 *
 * <p><b>Fail-open (INTEGRATIONS.md §3.4).</b> Kegagalan jaringan/5xx/4xx
 * diterjemahkan menjadi {@link HasilPostingBukuKas#gagal} (bukan exception) agar
 * penutupan sesi kasir <b>tidak</b> dibatalkan; sesi bisa di-posting ulang.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "kantin.integrasi.bukukas", name = "enabled", havingValue = "true")
@Slf4j
public class BukuKasRestClient extends IntegrasiRestSupport implements BukuKasPort {

    /** Path default posting entri Buku Kas (diusulkan; dapat di-override). */
    public static final String PATH_DEFAULT = "/api/buku-kas/catat";

    public BukuKasRestClient(IntegrasiRestProperties properties) {
        super("BukuKas", properties.getBukukas());
    }

    @Override
    public HasilPostingBukuKas catat(PerintahBukuKas perintah) {
        if (perintah == null || perintah.getSekolahId() == null) {
            return HasilPostingBukuKas.gagal("Perintah Buku Kas tidak lengkap");
        }
        try {
            Map<String, Object> body = badan();
            isi(body, "sekolahId", perintah.getSekolahId());
            isi(body, "tanggal", perintah.getTanggal());
            isi(body, "tipe", perintah.getTipe());
            isi(body, "kategori", perintah.getKategori());
            isi(body, "jumlah", perintah.getJumlah());
            isi(body, "metode", perintah.getMetode());
            isi(body, "keterangan", perintah.getKeterangan());
            isi(body, "refId", perintah.getRefId());
            isi(body, "refModul", perintah.getRefModul());

            byte[] raw = bytesJson(body);
            String hasil = coba("catat " + perintah.getRefId(), () -> {
                var spec = klien().post()
                        .uri(path(PATH_DEFAULT))
                        .contentType(MediaType.APPLICATION_JSON);
                var spec2 = beriHeader(spec, raw);
                return spec2.body(body).retrieve().body(String.class);
            });
            log.info("[BukuKas] Entri {} terposting (jumlah={}).", perintah.getRefId(),
                    perintah.getJumlah());
            return HasilPostingBukuKas.sukses(perintah.getRefId(),
                    "Terposting ke Buku Kas admin-be");
        } catch (RestClientResponseException e) {
            log.warn("[BukuKas] admin-be menolak refId={} (HTTP {}): {}", perintah.getRefId(),
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            return HasilPostingBukuKas.gagal(
                    "Buku Kas menolak (HTTP " + e.getStatusCode().value() + ")");
        } catch (RuntimeException e) {
            log.warn("[BukuKas] Gagal posting refId={} (fail-open, sesi tetap tertutup): {}",
                    perintah.getRefId(), e.getMessage());
            return HasilPostingBukuKas.gagal("Gagal menghubungi Buku Kas: " + e.getMessage());
        }
    }
}
