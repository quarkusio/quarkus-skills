package io.quarkus.ai.harness.checks;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EndpointCheck(
        String path,
        String method,
        String body,
        @JsonProperty("expected_status") Integer expectedStatus,
        @JsonProperty("body_contains") String bodyContains
) {
    public String effectiveMethod() {
        return method == null || method.isBlank() ? "GET" : method.toUpperCase();
    }

    public int effectiveExpectedStatus() {
        return expectedStatus != null ? expectedStatus : 200;
    }
}