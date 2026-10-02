package com.asqi.scholia_kantin_be.helper;

import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Konstanta &amp; util bersama. Pola idGenerator mengikuti admin-be
 * (AGENTS.md §5, CONVENTIONS.md §4).
 */
public final class Constants {

    private Constants() {
    }

    /**
     * Generator ID entitas bisnis (pola admin-be: epoch-millis + 3 digit acak).
     *
     * <p><b>⚠️ DIHAPUS (B20) — JANGAN dipakai.</b> Pola ini berisiko
     * <b>tabrakan PK</b> pada trafik tinggi (tap bersamaan) dan tidak monoton
     * (urutan id ≠ urutan waktu). Seluruh entitas ledger/transaksi kini memakai
     * bean {@code IdGenerator} ({@code helper/IdGenerator.java}) yang monoton &amp;
     * anti-tabrakan. Lihat {@code docs/spesifikasi-fase4-ledger.md §5.5}.
     */
    public static Long idGenerator() {
        Random random = new Random();
        int x = random.nextInt(900) + 100;
        return Long.valueOf(Timestamp.valueOf(LocalDateTime.now()).getTime() + "" + x);
    }

    /**
     * ID yang aman untuk ledger/transaksi bernilai tinggi: meletakkan 3 digit
     * acak DI DEPAN epoch-millis sehingga urutan tetap kira-kira naik, tetapi
     * tabrakan dalam milidetik yang sama sangat kecil.
     *
     * <p><b>⚠️ Usang (B20).</b> Gunakan bean {@code IdGenerator} (monoton + offset
     * per-JVM) sebagai gantinya. Method ini hanya disisakan agar tak ada kode lama
     * yang menyalinnya.
     *
     * @deprecated pakai {@code helper/IdGenerator} (bean Spring).
     */
    @Deprecated(since = "audit-B20", forRemoval = true)
    public static Long sortableIdGenerator() {
        Random random = new Random();
        int rand = random.nextInt(900) + 100;
        return Long.valueOf(rand + "" + System.currentTimeMillis());
    }

    /** Konversi `PascalCase` → `Sentence case` (parity admin-be). */
    public static String pascalToSentenceCase(String pascalCaseString) {
        String result = pascalCaseString.replaceAll("([a-z])([A-Z])", "$1 $2");
        return result.substring(0, 1).toUpperCase() + result.substring(1);
    }

    /** Ubah {@link Errors} validasi menjadi map field → pesan. */
    public static Map<String, String> validateErrorMessage(Errors errors) {
        Map<String, String> messages = new HashMap<>();
        for (FieldError fieldError : errors.getFieldErrors()) {
            messages.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return messages;
    }

    /** Kategori Buku Kas untuk posting kantin (INTEGRATIONS.md §3.3). */
    public static final String KATEGORI_PENDAPATAN_KANTIN = "Pendapatan Kantin";
    public static final String KATEGORI_BELANJA_STOK_KANTIN = "Belanja Stok Kantin";
    public static final String KATEGORI_PENYESUAIAN_KANTIN = "Penyesuaian Kantin";
}

