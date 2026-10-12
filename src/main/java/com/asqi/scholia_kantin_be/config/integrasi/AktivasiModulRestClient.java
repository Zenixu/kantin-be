package com.asqi.scholia_kantin_be.config.integrasi;

import com.asqi.scholia_kantin_be.service.integrasi.AktivasiModulInfo;
import com.asqi.scholia_kantin_be.service.integrasi.AktivasiModulPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Adapter REST nyata {@link AktivasiModulPort} → internal-be (INTEGRATIONS.md §7,
 * issue <b>#160</b>).
 *
 * <p><b>Kontrak (Q6):</b> {@code GET /api/internal/modul-kantin/status?sekolahId={id}}
 * → {@code {aktif, fee:{perTopupNominal,perTopupPersen,biayaLangganan}}}.
 *
 * <p><b>Fail-open (PRD §10).</b> Bila integrasi gagal/kontrak tak dikenali →
 * {@link AktivasiModulInfo#tidakDiketahui} (modul <b>dianggap aktif</b>) agar
 * kantin tidak mati karena integrasi absen.
 *
 * <p><b>Kapan hidup?</b> Hanya bila
 * {@code kantin.integrasi.aktivasi.enabled=true} (default <b>false</b>).
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "kantin.integrasi.aktivasi", name = "enabled", havingValue = "true")
@Slf4j
public class AktivasiModulRestClient extends IntegrasiRestSupport implements AktivasiModulPort {

    /** Path default status aktivasi modul (Q6). */
    public static final String PATH_DEFAULT = "/api/internal/modul-kantin/status";

    public AktivasiModulRestClient(IntegrasiRestProperties properties) {
        super("AktivasiModul", properties.getAktivasi());
    }

    @Override
    public AktivasiModulInfo status(Long sekolahId) {
        if (sekolahId == null) {
            return AktivasiModulInfo.tidakDiketahui("sekolahId kosong");
        }
        try {
            String json = coba("status sekolah=" + sekolahId, () -> {
                var spec = klien().get()
                        .uri(path(PATH_DEFAULT) + "?sekolahId=" + sekolahId);
                return beriHeader(spec, null).retrieve().body(String.class);
            });
            return parse(json);
        } catch (RestClientResponseException e) {
            log.warn("[AktivasiModul] internal-be menolak status sekolah={} (HTTP {}).", sekolahId,
                    e.getStatusCode().value());
            return AktivasiModulInfo.tidakDiketahui(
                    "internal-be menolak (HTTP " + e.getStatusCode().value() + ")");
        } catch (RuntimeException e) {
            log.warn("[AktivasiModul] Gagal status sekolah={} (fail-open, modul dianggap aktif): {}",
                    sekolahId, e.getMessage());
            return AktivasiModulInfo.tidakDiketahui("Gagal menghubungi internal-be: " + e.getMessage());
        }
    }

    private AktivasiModulInfo parse(String json) {
        if (json == null || json.isBlank()) {
            return AktivasiModulInfo.tidakDiketahui("Respons kosong");
        }
        JsonNode d = data(JSON.readTree(json));
        if (d == null || d.isNull() || !d.isObject() || d.get("aktif") == null) {
            return AktivasiModulInfo.tidakDiketahui("Bentuk respons tak dikenali (Q6)");
        }
        boolean aktif = d.get("aktif").asBoolean();
        Long feeTopup = null;
        JsonNode fee = d.get("fee");
        if (fee != null && fee.isObject() && fee.get("perTopupNominal") != null
                && !fee.get("perTopupNominal").isNull()) {
            feeTopup = fee.get("perTopupNominal").asLong();
        }
        return AktivasiModulInfo.builder()
                .aktif(aktif)
                .diketahui(true)
                .feeTopup(feeTopup)
                .catatan("Dari internal-be")
                .build();
    }
}
