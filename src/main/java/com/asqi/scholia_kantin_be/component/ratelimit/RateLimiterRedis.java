package com.asqi.scholia_kantin_be.component.ratelimit;

import com.asqi.scholia_kantin_be.config.ratelimit.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rate limiter fixed-window berbasis Redis (SECURITY.md §7).
 *
 * <p>Memakai Lua agar INCR + EXPIRE atomik — tanpa balapan antar request paralel.
 * Kunci: {@code rl:{kategori}:{identitas}} dengan TTL = jendela, sehingga
 * otomatis bersih tanpa job pembersih.
 *
 * <p><b>Fail-open secara default</b> ({@link RateLimitProperties#isFailClosed()}
 * {@code = false}): kegagalan Redis dilaporkan sebagai "diizinkan" agar kantin
 * tetap jalan saat infra rusak. ⚠️ Ini sengaja: memblokir semua transaksi
 * karena Redis down jauh lebih merugikan daripada melewatkan sebagian limit.
 *
 * <p>DILARANG memakai komponen ini untuk menyimpan status blokir kartu
 * (PRD §11.11) — itu wajib diperiksa di DB setiap tap.
 */
@Component
@RequiredArgsConstructor
public class RateLimiterRedis {

    /**
     * INCR kunci; bila kunci baru (nilai 1) set TTL. Kembalikan hitungan.
     * Atomik di sisi Redis.
     */
    private static final String LUA_INCR_EXPIRE = """
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return n
            """;

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            LUA_INCR_EXPIRE, Long.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RateLimitProperties properties;

    /**
     * Hasil pemeriksaan limit.
     *
     * @param diizinkan  {@code true} bila request boleh lanjut
     * @param hitungan   jumlah request pada jendela berjalan
     * @param batas      batas yang dikonfigurasi
     */
    public record Hasil(boolean diizinkan, long hitungan, int batas) {
    }

    /**
     * Periksa &amp; tambah hitungan untuk satu identitas.
     *
     * @param kategori  pengelompokan limit (mis. {@code "tap"}, {@code "auth"})
     * @param identitas pembeda antar klien (mis. sekolahId:userId, atau IP)
     * @param batas     batas pada jendela berjalan
     */
    public Hasil periksa(String kategori, String identitas, int batas) {
        String kunci = "rl:" + kategori + ":" + identitas;
        try {
            Long n = redisTemplate.execute(SCRIPT, List.of(kunci),
                    String.valueOf(properties.getWindowSeconds()));
            long hitungan = n == null ? 1L : n;

            if (hitungan > batas) {
                return new Hasil(false, hitungan, batas);
            }
            return new Hasil(true, hitungan, batas);
        } catch (RuntimeException e) {
            // Redis tidak tersedia (DataAccessException/timeout, dsb.) →
            // fail-open (default) agar operasional jalan terus.
            return new Hasil(!properties.isFailClosed(), 0L, batas);
        }
    }
}
