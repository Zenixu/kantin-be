package com.asqi.scholia_kantin_be.config.integrasi;

import com.asqi.scholia_kantin_be.service.integrasi.HasilKirimNotifikasi;
import com.asqi.scholia_kantin_be.service.integrasi.NotifikasiPort;
import com.asqi.scholia_kantin_be.service.integrasi.PerintahNotifikasi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Adapter REST nyata {@link NotifikasiPort} → mobile-be (INTEGRATIONS.md §6,
 * issue <b>#160</b>).
 *
 * <p><b>Kapan hidup?</b> Hanya bila {@code kantin.integrasi.notifikasi.enabled=true}
 * (default <b>false</b>). Saat hidup ia {@code @Primary} menang atas
 * {@link com.asqi.scholia_kantin_be.service.integrasi.NotifikasiFallback}.
 *
 * <p><b>Kontrak (Q5):</b> {@code POST /api/internal/notifikasi} dengan body
 * {@code {siswaId, sekolahId, jenis, judul, pesan, nominal, timestamp}}.
 *
 * <p><b>Fail-open (PRD §8.4).</b> Kegagalan apa pun → {@link HasilKirimNotifikasi#gagal};
 * notifikasi gagal <b>tidak pernah</b> membatalkan transaksi/top-up/refund.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "kantin.integrasi.notifikasi", name = "enabled", havingValue = "true")
@Slf4j
public class NotifikasiRestClient extends IntegrasiRestSupport implements NotifikasiPort {

    /** Path default kirim notifikasi (Q5). */
    public static final String PATH_DEFAULT = "/api/internal/notifikasi";

    public NotifikasiRestClient(IntegrasiRestProperties properties) {
        super("Notifikasi", properties.getNotifikasi());
    }

    @Override
    public HasilKirimNotifikasi kirim(PerintahNotifikasi perintah) {
        if (perintah == null) {
            return HasilKirimNotifikasi.gagal("Perintah notifikasi kosong");
        }
        try {
            Map<String, Object> body = badan();
            isi(body, "siswaId", perintah.getSubjekId());
            isi(body, "sekolahId", perintah.getSekolahId());
            isi(body, "jenis", perintah.getJenis());
            isi(body, "judul", perintah.getJenis() == null ? null : perintah.getJenis().name());
            isi(body, "pesan", perintah.getRingkasan());
            isi(body, "nominal", perintah.getNominal());
            isi(body, "referensiId", perintah.getReferensiId());
            isi(body, "timestamp", perintah.getWaktu());

            byte[] raw = bytesJson(body);
            coba("kirim " + perintah.getJenis(), () -> {
                var spec = klien().post()
                        .uri(path(PATH_DEFAULT))
                        .contentType(MediaType.APPLICATION_JSON);
                return beriHeader(spec, raw).body(body).retrieve().body(String.class);
            });
            log.info("[Notifikasi] {} terkirim ke mobile-be (siswa={}).", perintah.getJenis(),
                    perintah.getSubjekId());
            return HasilKirimNotifikasi.terkirim("Terkirim ke mobile-be");
        } catch (RestClientResponseException e) {
            log.warn("[Notifikasi] mobile-be menolak {} (HTTP {}): {}", perintah.getJenis(),
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            return HasilKirimNotifikasi.gagal(
                    "Notifikasi ditolak (HTTP " + e.getStatusCode().value() + ")");
        } catch (RuntimeException e) {
            log.warn("[Notifikasi] Gagal kirim {} (best-effort, transaksi tetap sah): {}",
                    perintah.getJenis(), e.getMessage());
            return HasilKirimNotifikasi.gagal("Gagal menghubungi mobile-be: " + e.getMessage());
        }
    }
}
