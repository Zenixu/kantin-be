package com.asqi.scholia_kantin_be.config.webhook;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Konfigurasi handler webhook <b>top-up online</b> (PRD §8.2, INTEGRATIONS §5).
 *
 * <p><b>Kenapa bisa dikonfigurasi?</b> Kontrak payload callback-be belum final
 * (OPEN-QUESTIONS Q4), termasuk <i>nama</i> jenis event yang dikirim. Alih-alih
 * mengunci satu literal di kode, nama jenis event (dan sinonimnya) diatur di
 * sini sehingga penyesuaian kontrak cukup lewat konfigurasi/environment tanpa
 * mengubah atau me-<i>deploy</i> ulang kode.
 *
 * <p>Nilai default aman &amp; fail-safe: bila pengirim memakai nama lain,
 * event cukup <b>tidak</b> ditangani (dicatat {@code DIABAIKAN} di jurnal
 * webhook) — bukan diproses keliru.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "kantin.webhook.topup")
public class TopUpOnlineProperties {

    /**
     * Jenis event yang memicu penambahan saldo (boleh lebih dari satu, mis.
     * saat pengirim berganti nama). Pencocokan <b>case-insensitive</b>.
     */
    private List<String> eventTypes = List.of("TOPUP_ONLINE_SUKSES", "TOPUP_ONLINE");

    /**
     * Jenis subjek pemilik saldo bila payload tidak menyebutkannya. Top-up
     * online berasal dari ortu → default {@code SISWA}.
     */
    private String subjekTipeDefault = "SISWA";
}
