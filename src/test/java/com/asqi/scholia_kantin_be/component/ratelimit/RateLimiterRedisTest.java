package com.asqi.scholia_kantin_be.component.ratelimit;

import com.asqi.scholia_kantin_be.config.ratelimit.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Uji rate limiter Redis: batas, fail-open, fail-closed (SECURITY.md §7). */
@DisplayName("RateLimiterRedis — batas & mode gagal")
class RateLimiterRedisTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    private RateLimitProperties props;
    private RateLimiterRedis limiter;

    @BeforeEach
    void setUp() {
        props = new RateLimitProperties();
        props.setWindowSeconds(60);
        limiter = new RateLimiterRedis(redis, props);
    }

    private void redisMengembalikan(long n) {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(n);
    }

    @Test
    @DisplayName("di bawah batas → diizinkan")
    void diBawahBatas() {
        redisMengembalikan(3);
        var hasil = limiter.periksa("tap", "1:42", 10);
        assertThat(hasil.diizinkan()).isTrue();
        assertThat(hasil.hitungan()).isEqualTo(3);
    }

    @Test
    @DisplayName("tepat di batas → masih diizinkan")
    void tepatDiBatas() {
        redisMengembalikan(10);
        assertThat(limiter.periksa("tap", "1:42", 10).diizinkan()).isTrue();
    }

    @Test
    @DisplayName("melebihi batas → ditolak")
    void melebihiBatas() {
        redisMengembalikan(11);
        var hasil = limiter.periksa("tap", "1:42", 10);
        assertThat(hasil.diizinkan()).isFalse();
        assertThat(hasil.batas()).isEqualTo(10);
    }

    @Test
    @DisplayName("Redis error → FAIL-OPEN (default): request tetap diizinkan")
    void failOpenSaatRedisMati() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new QueryTimeoutException("redis down"));

        var hasil = limiter.periksa("tap", "1:42", 10);
        assertThat(hasil.diizinkan())
                .as("gangguan Redis tidak boleh melumpuhkan kantin")
                .isTrue();
    }

    @Test
    @DisplayName("Redis error + fail-closed aktif → ditolak")
    void failClosedBilaDikonfigurasi() {
        props.setFailClosed(true);
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new QueryTimeoutException("redis down"));

        assertThat(limiter.periksa("tap", "1:42", 10).diizinkan()).isFalse();
    }

    @Test
    @DisplayName("Redis mengembalikan null → dianggap hitungan 1 (diizinkan)")
    void nullDianggapSatu() {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(null);
        var hasil = limiter.periksa("tap", "1:42", 10);
        assertThat(hasil.diizinkan()).isTrue();
        assertThat(hasil.hitungan()).isEqualTo(1);
    }
}
