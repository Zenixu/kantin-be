package com.asqi.scholia_kantin_be.config.notifikasi;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfigurasi pengiriman notifikasi ke orang tua (PRD §8.4, INTEGRATIONS §6).
 *
 * <p><b>Kenapa bisa dikonfigurasi?</b> Kontrak push notification mobile-be
 * belum final (OPEN-QUESTIONS Q5), termasuk <i>base URL</i> API internalnya.
 * Alih-alih mengunci alamat di kode, base URL diatur di sini sehingga
 * penyesuaian kontrak cukup lewat konfigurasi/environment tanpa mengubah atau
 * me-<i>deploy</i> ulang kode.
 *
 * <p>Nilai default aman &amp; fail-safe: bila dinonaktifkan (atau base URL
 * kosong), pengiriman cukup <b>dilewati</b> (dicatat {@code DILEWATI}) — bukan
 * diproses keliru.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "kantin.notifikasi")
public class NotifikasiProperties {

    /**
     * Aktifkan pengiriman notifikasi. Bila {@code false}, semua pengiriman
     * dilewati ({@code DILEWATI}) tanpa memanggil port — berguna untuk
     * dev/test.
     */
    private boolean enabled = true;

    /**
     * Base URL API internal mobile-be (mis. {@code https://mobile-be.internal}).
     * Kosong = integrasi belum siap (fallback aktif).
     */
    private String baseUrl = "";

    /**
     * Batas waktu (ms) panggilan HTTP ke mobile-be. Notifikasi best-effort —
     * jangan menahan transaksi terlalu lama.
     */
    private int timeoutMs = 2000;
}
