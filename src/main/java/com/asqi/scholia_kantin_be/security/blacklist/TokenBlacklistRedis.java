package com.asqi.scholia_kantin_be.security.blacklist;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Implementasi Redis {@link TokenBlacklistPort}.
 *
 * <p>Kunci: {@code bl:<sha256(token)>} dengan TTL = sisa umur token. Setelah
 * token kedaluwarsa sendiri, entri ikut hilang — tidak perlu pembersih.
 *
 * <p><b>Fail-open:</b> kegagalan Redis tidak pernah membuat request ditolak.
 * Saat memeriksa, error → dianggap belum dicabut. Saat mencabut, error hanya
 * dicatat di log — operasi pencabutan gagal, tetapi service tetap hidup.
 *
 * <p>Memakai {@link StringRedisTemplate} (nilai sederhana) alih-alih
 * {@code RedisTemplate<String,Object>} agar tidak bergantung serializer JSON
 * untuk penanda yang hanya berupa string.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TokenBlacklistRedis implements TokenBlacklistPort {

    private static final String PREFIX = "bl:";

    private final StringRedisTemplate redis;

    @Override
    public void cabut(String token, long detikSampaiKedaluwarsa) {
        if (token == null || token.isBlank() || detikSampaiKedaluwarsa <= 0) {
            return;
        }
        try {
            redis.opsForValue().set(kunci(token), "1", Duration.ofSeconds(detikSampaiKedaluwarsa));
        } catch (RuntimeException e) {
            // Fail-open: pencabutan gagal, tetapi jangan mengganggu permintaan berjalan.
            log.warn("Gagal mencabut token di Redis (fail-open): {}", e.getMessage());
        }
    }

    @Override
    public boolean tercabut(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(kunci(token)));
        } catch (RuntimeException e) {
            // Fail-open: penyimpanan tak dapat dihubungi → anggap belum dicabut.
            log.warn("Gagal memeriksa blacklist Redis (fail-open): {}", e.getMessage());
            return false;
        }
    }

    private String kunci(String token) {
        return PREFIX + SidikJari.dari(token);
    }
}
