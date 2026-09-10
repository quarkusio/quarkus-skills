package io.quarkus.ai.harness.runner;

import io.quarkus.ai.harness.runner.acp.SmallryeAcpRunner;
import io.quarkus.ai.harness.runner.claude.ClaudeRunner;
import io.quarkus.ai.harness.runner.opencode.OpenCodeRunner;
import io.quarkus.ai.harness.runner.pi.PiRunner;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public enum RunnerRegistry {
    PI("pi", PiRunner::new, "vertex-anthropic", "claude-opus-4-6"),
    OPENCODE("opencode", OpenCodeRunner::new, "google-vertex-anthropic", "claude-opus-4-6@default"),
    CLAUDE("claude", ClaudeRunner::new, "", "claude-opus-4-6"),
    ACP("acp",SmallryeAcpRunner::new,"","");

    @FunctionalInterface
    interface RunnerFactory {
        AgentRunner create(String aiCmd, String provider, String model,
                           Path skillPath, String strategy, int timeoutSeconds, String prompt, String skillArgs, boolean sanitize);
    }

    private final String key;
    private final RunnerFactory factory;
    private final String defaultProvider;
    private final String defaultModel;

    // Fast lookup cache map
    private static final Map<String, RunnerRegistry> LOOKUP_MAP = new HashMap<>();

    static {
        for (RunnerRegistry registry : values()) {
            LOOKUP_MAP.put(registry.key.toLowerCase(), registry);
        }
    }

    RunnerRegistry(String key, RunnerFactory factory, String defaultProvider, String defaultModel) {
        this.key = key;
        this.factory = factory;
        this.defaultProvider = defaultProvider;
        this.defaultModel = defaultModel;
    }

    public static String resolveProvider(String aiCmd, String userProvider) {
        if (userProvider != null && !userProvider.isBlank()) return userProvider;
        RunnerRegistry r = lookup(aiCmd);
        return r != null ? r.defaultProvider : "";
    }

    public static String resolveModel(String aiCmd, String userModel) {
        if (userModel != null && !userModel.isBlank()) return userModel;
        RunnerRegistry r = lookup(aiCmd);
        return r != null ? r.defaultModel : "";
    }

    // Factory method
    public static AgentRunner getRunner(String aiCmd, String provider, String model,
                                        Path skillPath, String strategy, int timeoutSeconds, String prompt, String skillArgs, boolean sanitize) {
        RunnerRegistry registry = lookup(aiCmd);
        if (registry == null) {
            throw new IllegalArgumentException("No runner registered for the agent: " + aiCmd);
        }
        String resolvedProvider = (provider != null && !provider.isBlank()) ? provider : registry.defaultProvider;
        String resolvedModel = (model != null && !model.isBlank()) ? model : registry.defaultModel;
        return registry.factory.create(aiCmd, resolvedProvider, resolvedModel, skillPath, strategy, timeoutSeconds, prompt, skillArgs, sanitize);
    }

    private static RunnerRegistry lookup(String aiCmd) {
        if (aiCmd == null) {
            throw new IllegalArgumentException("aiCmd cannot be null");
        }
        return LOOKUP_MAP.get(aiCmd.toLowerCase().trim());
    }
}