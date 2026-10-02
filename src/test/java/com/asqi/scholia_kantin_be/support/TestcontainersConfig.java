package com.asqi.scholia_kantin_be.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Kontainer PostgreSQL bersama untuk seluruh test integrasi ledger.
 *
 * <p>Testcontainers <b>wajib</b> untuk uji ledger &amp; race condition
 * (ADR-0003, CONVENTIONS.md §9). Memakai instance statis agar kontainer di-reuse
 * antar-kelas test (lebih cepat).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:18-alpine");
    }
}
