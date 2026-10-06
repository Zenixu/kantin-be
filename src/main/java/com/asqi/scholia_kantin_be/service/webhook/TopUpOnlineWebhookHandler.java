package com.asqi.scholia_kantin_be.service.webhook;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.config.webhook.TopUpOnlineProperties;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.saldo.SaldoTopUpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Handler webhook <b>top-up online</b> dari {@code callback-be} (PRD §8.2,
 * INTEGRATIONS §5, issue #37).
 *
 * <p><b>Alur:</b> ortu top-up via PG → callback PG → {@code callback-be} →
 * diteruskan ke kantin-be. Saldo bertambah <b>hanya setelah</b> callback sukses;
 * callback duplikat ≠ 2× (PRD §11.3). Pipa keamanan webhook (verifikasi HMAC,
 * anti-replay, idempotency per event id) sudah dijalankan {@link WebhookService}
 * <i>sebelum</i> handler ini dipanggil; idempotency saldo lapis kedua dijaga
 * {@link SaldoTopUpService#topUpOnline} lewat {@code refId} PG.
 *
 * <p><b>Kontrak payload belum final (Q4).</b> Karena itu field diambil dari
 * {@code data} dengan beberapa <b>alias</b> yang lazim, dan nama jenis event
 * diatur lewat {@link TopUpOnlineProperties}. Begitu kontrak dikonfirmasi,
 * cukup sesuaikan konfigurasi (tanpa mengubah kode keamanan).
 *
 * <p>Handler dipanggil <b>di dalam transaksi</b> webhook: bila melempar
 * exception, baris jurnal event ikut di-rollback sehingga event bisa dicoba
 * ulang dan tidak ada efek setengah jalan.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TopUpOnlineWebhookHandler implements WebhookHandlerPort {

    /** Alias field referensi transaksi PG (kunci idempotency, PRD §11.3). */
    private static final List<String> ALIAS_REF = List.of(
            "refId", "refIdPg", "referenceId", "referensiId", "orderId", "trxId", "pgRef");

    /** Alias field nominal rupiah. */
    private static final List<String> ALIAS_NOMINAL = List.of(
            "nominal", "amount", "jumlah", "total");

    /** Alias field id subjek pemilik saldo (id siswa / id kartu tamu). */
    private static final List<String> ALIAS_SUBJEK_ID = List.of(
            "subjekId", "siswaId", "idSiswa", "siswa_id", "kartuId", "kartuTamuId");

    /** Alias field jenis subjek ({@code SISWA} / {@code KARTU_TAMU}). */
    private static final List<String> ALIAS_SUBJEK_TIPE = List.of(
            "subjekTipe", "tipeSubjek");

    /** Alias field keterangan kanal pembayaran &amp; penyetor (opsional). */
    private static final List<String> ALIAS_KANAL = List.of("kanal", "channel", "metode");
    private static final List<String> ALIAS_PENYETOR = List.of("penyetor", "namaOrtu", "nama");

    private final SaldoTopUpService saldoTopUp;
    private final TopUpOnlineProperties properties;

    @Override
    public boolean mendukung(String eventType) {
        if (eventType == null) {
            return false;
        }
        String t = eventType.trim();
        return properties.getEventTypes().stream()
                .anyMatch(e -> e.equalsIgnoreCase(t));
    }

    @Override
    public void tangani(KonteksWebhook konteks) {
        Map<String, Object> data = konteks.data();
        if (data == null || data.isEmpty()) {
            throw new InvalidOperationException(
                    "Payload top-up online kosong (event " + konteks.eventId() + ")");
        }

        Long sekolahId = konteks.sekolahId() != null
                ? konteks.sekolahId()
                : ambilLong(data, List.of("sekolahId", "sekolah_id"));
        if (sekolahId == null) {
            throw new InvalidOperationException(
                    "sekolahId wajib ada pada event top-up online (tenant scoping, PRD §11.4)");
        }

        String refId = ambilString(data, ALIAS_REF);
        if (refId == null || refId.isBlank()) {
            throw new InvalidOperationException(
                    "Referensi transaksi PG (refId) wajib ada — kunci idempotency (PRD §11.3)");
        }

        Long nominal = ambilLong(data, ALIAS_NOMINAL);
        if (nominal == null || nominal <= 0) {
            throw new InvalidOperationException("Nominal top-up online tidak valid (harus > 0)");
        }

        Long subjekId = ambilLong(data, ALIAS_SUBJEK_ID);
        if (subjekId == null) {
            throw new InvalidOperationException(
                    "ID subjek (siswa) wajib ada pada event top-up online");
        }

        SubjekTipe subjekTipe = bacaSubjekTipe(data);

        // Idempotency lapis kedua di ledger: refId PG sama ⇒ tidak menambah dua kali.
        var hasil = saldoTopUp.topUpOnline(sekolahId, subjekTipe, subjekId, nominal,
                refId, ambilString(data, ALIAS_PENYETOR), ambilString(data, ALIAS_KANAL), null);

        if (hasil.isIdempotentReplay()) {
            log.info("Top-up online replay refId={} (sekolah={}) — saldo tidak ditambah ulang.",
                    refId, sekolahId);
        } else {
            log.info("Top-up online sukses refId={} sekolah={} subjek={}:{} nominal={} saldoSetelah={}",
                    refId, sekolahId, subjekTipe, subjekId, nominal, hasil.getSaldoSetelah());
        }
    }

    /** Jenis subjek dari payload, fallback ke default konfigurasi (SISWA). */
    private SubjekTipe bacaSubjekTipe(Map<String, Object> data) {
        String nilai = ambilString(data, ALIAS_SUBJEK_TIPE);
        String kandidat = (nilai == null || nilai.isBlank())
                ? properties.getSubjekTipeDefault() : nilai;
        if (kandidat == null || kandidat.isBlank()) {
            return SubjekTipe.SISWA;
        }
        try {
            return SubjekTipe.valueOf(kandidat.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidOperationException("Jenis subjek tidak dikenal: " + kandidat);
        }
    }

    /** Ambil nilai teks pertama yang ada dari daftar alias field. */
    private static String ambilString(Map<String, Object> data, List<String> alias) {
        for (String k : alias) {
            Object v = data.get(k);
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }

    /** Ambil nilai bilangan pertama yang ada &amp; dapat diparse dari alias field. */
    private static Long ambilLong(Map<String, Object> data, List<String> alias) {
        for (String k : alias) {
            Object v = data.get(k);
            if (v instanceof Number n) {
                return n.longValue();
            }
            if (v instanceof String s && !s.isBlank()) {
                try {
                    return Long.parseLong(s.trim());
                } catch (NumberFormatException ignored) {
                    // coba alias berikutnya
                }
            }
        }
        return null;
    }
}
