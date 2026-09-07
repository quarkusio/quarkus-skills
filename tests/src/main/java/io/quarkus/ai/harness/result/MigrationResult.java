package io.quarkus.ai.harness.result;

import io.quarkus.ai.harness.runner.AgentRunner;
import io.quarkus.ai.harness.skill.SkillReference;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures the full result of a migration test run.
 */
public class MigrationResult {
    private final String agent;
    private final String project;
    private final String model;
    private final String strategy;
    private final SkillReference skillRef;
    private final Instant timestamp;
    private final Duration duration;
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private long totalTokens;
    private double totalCost;
    private int apiCalls;
    private int toolCalls;
    private long inputTokens;
    private long outputTokens;
    private long thinkingTokens;
    private long cacheRead;
    private long cacheWrite;
    private List<String> sessionFiles;
    private String workDir;
    private String runName;
    private int aiExitCode;
    private String review;
    private long reviewTokens;
    private double reviewCost;
    private String prompt;
    private String userProvider;
    private String userModel;
    private String projectType;
    private List<AgentRunner.ModelUsage> modelUsages = List.of();

    public MigrationResult(String agent, String project, String model, String strategy, SkillReference skillRef) {
        this.agent = agent;
        this.project = project;
        this.model = model;
        this.strategy = strategy;
        this.skillRef = skillRef;
        this.timestamp = Instant.now();
        this.duration = Duration.ZERO;
    }

    private Duration actualDuration;

    public void setDuration(Duration duration) {
        this.actualDuration = duration;
    }

    public Duration getDuration() {
        return actualDuration != null ? actualDuration : duration;
    }

    public void addCheck(String name, boolean passed) {
        checks.put(name, passed);
    }

    public Map<String, Boolean> getChecks() {
        return checks;
    }

    public boolean checkPassed(String name) {
        return checks.getOrDefault(name, false);
    }

    public int passed() {
        return (int) checks.values().stream().filter(v -> v).count();
    }

    public int total() {
        return checks.size();
    }

    public String score() {
        return passed() + "/" + total();
    }

    // Getters and setters for usage stats
    public long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }

    public double getTotalCost() { return totalCost; }
    public void setTotalCost(double totalCost) { this.totalCost = totalCost; }

    public int getApiCalls() { return apiCalls; }
    public void setApiCalls(int apiCalls) { this.apiCalls = apiCalls; }

    public int getToolCalls() { return toolCalls; }
    public void setToolCalls(int toolCalls) { this.toolCalls = toolCalls; }

    public long getInputTokens() { return inputTokens; }
    public void setInputTokens(long inputTokens) { this.inputTokens = inputTokens; }

    public long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }

    public long getThinkingTokens() { return thinkingTokens; }
    public void setThinkingTokens(long thinkingTokens) { this.thinkingTokens = thinkingTokens; }

    public long getCacheRead() { return cacheRead; }
    public void setCacheRead(long cacheRead) { this.cacheRead = cacheRead; }

    public long getCacheWrite() { return cacheWrite; }
    public void setCacheWrite(long cacheWrite) { this.cacheWrite = cacheWrite; }

    public List<String> getSessionFiles() { return sessionFiles; }
    public void setSessionFiles(List<String> sessionFiles) { this.sessionFiles = sessionFiles; }

    public String getWorkDir() { return workDir; }
    public void setWorkDir(String workDir) { this.workDir = workDir; }

    public String getRunName() { return runName; }
    public void setRunName(String runName) { this.runName = runName; }

    public int getAiExitCode() { return aiExitCode; }
    public void setAiExitCode(int aiExitCode) { this.aiExitCode = aiExitCode; }

    public String getReview() { return review; }
    public void setReview(String review) { this.review = review; }

    public long getReviewTokens() { return reviewTokens; }
    public void setReviewTokens(long reviewTokens) { this.reviewTokens = reviewTokens; }

    public double getReviewCost() { return reviewCost; }
    public void setReviewCost(double reviewCost) { this.reviewCost = reviewCost; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public String getUserProvider() { return userProvider; }
    public void setUserProvider(String userProvider) { this.userProvider = userProvider; }

    public String getUserModel() { return userModel; }
    public void setUserModel(String userModel) { this.userModel = userModel; }

    public String getProjectType() { return projectType; }
    public void setProjectType(String projectType) { this.projectType = projectType; }

    public List<AgentRunner.ModelUsage> getModelUsages() { return modelUsages; }
    public void setModelUsages(List<AgentRunner.ModelUsage> modelUsages) { this.modelUsages = modelUsages; }

    public String getAgent() { return agent; }
    public String getProject() { return project; }
    public String getModel() { return model; }
    public String getStrategy() { return strategy; }
    public String getSkill() { return skillRef.name(); }
    public SkillReference getSkillRef() { return skillRef; }
    public Instant getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("Migration Result: %s [%s]\n".formatted(project, score()));
        sb.append("  agent:    %s\n".formatted(agent));
        sb.append("  model:    %s\n".formatted(model));
        if ("spring-boot".equals(projectType) && skillRef.name() != null
                && skillRef.name().toLowerCase().contains("migrate")) {
            sb.append("  strategy: %s\n".formatted(strategy));
        }
        sb.append("  skill:    %s\n".formatted(skillRef.name()));
        if (skillRef.isRemote()) {
            sb.append("  skill-url: %s\n".formatted(skillRef.url()));
        }
        sb.append("  skill-path: %s\n".formatted(skillRef.localPath()));
        sb.append("  duration: %ds\n".formatted(getDuration().toSeconds()));
        sb.append("  tokens:   %d\n".formatted(totalTokens));
        sb.append(String.format(java.util.Locale.US, "  cost:     $%.4f\n", totalCost));
        sb.append("  calls:    %d\n".formatted(apiCalls));
        sb.append("  tools:    %d\n".formatted(toolCalls));
        sb.append("  checks:\n");
        checks.forEach((k, v) -> sb.append("    %s %s\n".formatted(v ? "✅" : "❌", k)));
        sb.append("  workdir:  %s\n".formatted(workDir));
        return sb.toString();
    }
}