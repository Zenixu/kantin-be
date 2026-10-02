package com.asqi.scholia_kantin_be.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Kondisi JUnit 5: jalankan test hanya bila Docker tersedia.
 *
 * <p>Dipakai lewat anotasi {@link EnabledIfDockerAvailable}. Bila Docker mati,
 * test integrasi Testcontainers di-<b>skip</b> (bukan gagal), sehingga
 * {@code mvn test} tetap hijau di mesin/CI tanpa Docker.
 */
public class DockerAvailableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        try {
            boolean ada = DockerClientFactory.instance().isDockerAvailable();
            return ada
                    ? ConditionEvaluationResult.enabled("Docker tersedia")
                    : ConditionEvaluationResult.disabled("Docker tidak tersedia — test integrasi dilewati");
        } catch (Throwable t) {
            return ConditionEvaluationResult.disabled("Docker tidak tersedia: " + t.getMessage());
        }
    }
}
