package io.quarkus.ai.harness.config;

import io.quarkus.ai.harness.checks.CheckConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps to a project.yaml file defining the different parameters.
 */
public record ProjectConfig(
        String name,
        String description,
        String type,
        String skill,
        String source,
        String ref,
        int timeout,
        Map<String, CheckConfig> checks,
        TestConfig test
) {
    public record TestConfig(Boolean enabled) {
        public TestConfig {
            if (enabled == null) enabled = true;
        }
    }

    public Map<String, CheckConfig> checks() {
        if (checks == null) return Map.of();
        var normalized = new LinkedHashMap<String, CheckConfig>();
        checks.forEach((k, v) -> normalized.put(k, v == null ? new CheckConfig(null) : v));
        return normalized;
    }

    public boolean isTestEnabled() {
        return test == null || test.enabled();
    }

    public boolean isLocal() {
        return "local".equals(source);
    }
}