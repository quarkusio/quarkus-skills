package io.quarkus.ai.harness.checks;

import java.util.List;

public record CheckConfig(
        List<EndpointCheck> endpoints
) {
    public CheckConfig {
        if (endpoints == null) endpoints = List.of();
    }
}