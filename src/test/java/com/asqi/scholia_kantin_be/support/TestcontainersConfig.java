package com.asqi.scholia_kantin_be.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Kontainer bersama untuk seluruh test integrasi (*IT).
 *
 * <p>Testcontainers <b>wajib</b> untuk uji ledger &amp; race condition
 * (ADR-0003, CONVENTIONS.md §9). Memakai instance statis agar kontainer
 * di-reuse antar-kelas test (lebih cepat).
 *
 * <p><b>PostgreSQL</b> menyediakan skema (Flyway V1..Vn) untuk uji ledger.
 *
 * <p><b>Redis</b> diperlukan {@code FilterGandaIT} (audit B33/#13): uji itu
 * memakai {@code StringRedisTemplate} &amp; {@code RateLimiterRedis} nyata, jadi
 * tanpa Redis konteks gagal (bukan fail-open seperti jalur produksi). Sebelum
 * kontainer Redis ditambahkan di sini, IT hanya hijau di mesin yang menjalankan
 * {@code docker compose up -d} — CI tanpa Redis merah. {@code @ServiceConnection}
 * mengarahkan {@code spring.data.redis.host/port} ke kontainer ini.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:18-alpine");
    }

    /**
     * Redis 7 untuk uji rate-limit &amp; blacklist token (B33/#13).
     *
     * <p>{@code @ServiceConnection} mengenali kontainer ini lewat nama image
     * {@code redis} (lihat {@code RedisContainerConnectionDetailsFactory} Spring
     * Boot) dan menyuntikkan host/port-nya ke konfigurasi Redis aplikasi.
     */
    @Bean
    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource")
    public GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
    }
}
