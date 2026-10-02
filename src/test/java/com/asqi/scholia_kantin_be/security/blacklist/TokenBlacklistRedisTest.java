package com.asqi.scholia_kantin_be.security.blacklist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Uji blacklist token Redis: TTL, fail-open, skip token mati. */
@DisplayName("TokenBlacklistRedis — cabut & periksa (fail-open)")
class TokenBlacklistRedisTest {

    @SuppressWarnings("unchecked")
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private TokenBlacklistRedis blacklist;

    @BeforeEach
    void setUp() {
        blacklist = new TokenBlacklistRedis(redis);
        when(redis.opsForValue()).thenReturn(ops);
    }

    @Test
    @DisplayName("cabut menyimpan kunci dengan TTL = sisa umur")
    void cabutDenganTtl() {
        blacklist.cabut("token-x", 300);
        verify(ops).set(eq("bl:" + SidikJari.dari("token-x")), eq("1"), eq(Duration.ofSeconds(300)));
    }

    @Test
    @DisplayName("token sudah kedaluwarsa (sisa<=0) → tidak disimpan")
    void skipTokenMati() {
        blacklist.cabut("token-x", 0);
        blacklist.cabut("token-x", -5);
        verify(ops, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("tercabut → true bila kunci ada")
    void tercabutTrue() {
        when(redis.hasKey("bl:" + SidikJari.dari("token-x"))).thenReturn(true);
        assertThat(blacklist.tercabut("token-x")).isTrue();
    }

    @Test
    @DisplayName("tidak tercabut → false")
    void tercabutFalse() {
        when(redis.hasKey(anyString())).thenReturn(false);
        assertThat(blacklist.tercabut("token-x")).isFalse();
    }

    @Test
    @DisplayName("Redis error saat periksa → FAIL-OPEN (false)")
    void failOpenPeriksa() {
        when(redis.hasKey(anyString())).thenThrow(new QueryTimeoutException("down"));
        assertThat(blacklist.tercabut("token-x"))
                .as("gangguan Redis tidak boleh menolak token yang sah")
                .isFalse();
    }

    @Test
    @DisplayName("Redis error saat cabut → tidak melempar (dicatat di log)")
    void failOpenCabut() {
        org.mockito.Mockito.doThrow(new QueryTimeoutException("down"))
                .when(ops).set(anyString(), anyString(), any(Duration.class));
        assertThatCode(() -> blacklist.cabut("token-x", 60)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("token null/blank → tidak menyentuh Redis")
    void tokenKosong() {
        blacklist.cabut(null, 60);
        blacklist.cabut("  ", 60);
        assertThat(blacklist.tercabut(null)).isFalse();
        verify(ops, never()).set(anyString(), anyString(), any(Duration.class));
    }
}
