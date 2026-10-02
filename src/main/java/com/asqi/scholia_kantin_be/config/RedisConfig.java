package com.asqi.scholia_kantin_be.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Konfigurasi Redis (cache, blacklist token, rate limit).
 *
 * <p>Memakai {@link GenericJacksonJsonRedisSerializer} (Jackson 3 / Spring Data
 * Redis 4) — pengganti {@code Jackson2JsonRedisSerializer} yang sudah
 * <i>deprecated for removal</i> di Spring Boot 4.
 *
 * <p>⚠️ DILARANG memakai Redis untuk meng-cache <b>status blokir kartu</b>
 * (PRD §11.11, AGENTS.md §10). Blokir wajib diperiksa di server setiap tap.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        RedisSerializer<Object> serializer = GenericJacksonJsonRedisSerializer.builder().build();

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();

        return template;
    }
}
