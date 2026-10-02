package com.asqi.scholia_kantin_be.support;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Jalankan test hanya bila Docker tersedia (lihat {@link DockerAvailableCondition}).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(DockerAvailableCondition.class)
public @interface EnabledIfDockerAvailable {
}
