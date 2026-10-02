package com.asqi.scholia_kantin_be.helper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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
     * Jangan pakai autoincrement untuk entitas bisnis yang butuh ID global.
     */
    public static Long idGenerator() {
        Random random = new Random();
        int x = random.nextInt(900) + 100;
        return Long.valueOf(Timestamp.valueOf(LocalDateTime.now()).getTime() + "" + x);
    }

    /** Kategori Buku Kas untuk posting kantin (INTEGRATIONS.md §3.3). */
    public static final String KATEGORI_PENDAPATAN_KANTIN = "Pendapatan Kantin";
    public static final String KATEGORI_BELANJA_STOK_KANTIN = "Belanja Stok Kantin";
    public static final String KATEGORI_PENYESUAIAN_KANTIN = "Penyesuaian Kantin";
}
