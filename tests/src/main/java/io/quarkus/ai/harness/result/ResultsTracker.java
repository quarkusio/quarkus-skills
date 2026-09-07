package io.quarkus.ai.harness.result;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import io.quarkus.ai.harness.skill.SkillReference;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Locale;

/**
 * Appends migration results to a JSONL history file for trend tracking.
 */
public class ResultsTracker {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .disable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private final Path historyFile;

    public ResultsTracker(Path historyFile) {
        this.historyFile = historyFile;
    }

    /**
     * Default history file location: target/runs/history.jsonl
     */
    public static ResultsTracker defaultTracker() {
        Path runsDir = Path.of("target", "runs").toAbsolutePath();
        try {
            Files.createDirectories(runsDir);
        } catch (IOException ignored) {
        }
        return new ResultsTracker(runsDir.resolve("history.jsonl"));
    }

    /**
     * Append a result to the history file and write a markdown report.
     */
    public void record(MigrationResult result) {
        ObjectNode node = JSON.createObjectNode();
        node.put("project", result.getProject());
        node.put("date", Instant.now().toString());
        node.put("model", result.getModel());
        boolean isSpringMigration = isSpringMigration(result);
        if (isSpringMigration) {
            node.put("strategy", result.getStrategy());
        }
        node.put("skill", result.getSkill());
        SkillReference ref = result.getSkillRef();
        ObjectNode skillNode = node.putObject("skill_ref");
        skillNode.put("name", ref.name());
        if (ref.url() != null) {
            skillNode.put("url", ref.url());
        }
        skillNode.put("local_path", ref.localPath());
        node.put("duration_seconds", result.getDuration().toSeconds());

        ObjectNode usage = node.putObject("usage");
        usage.put("total_tokens", result.getTotalTokens());
        usage.put("total_cost", result.getTotalCost());
        usage.put("api_calls", result.getApiCalls());
        usage.put("tool_calls", result.getToolCalls());

        ObjectNode checks = node.putObject("checks");
        result.getChecks().forEach(checks::put);

        node.put("score", result.score());
        node.put("ai_exit_code", result.getAiExitCode());
        node.put("run_name", result.getRunName());
        node.put("workdir", result.getWorkDir());

        if (result.getReview() != null && !result.getReview().isBlank()) {
            ObjectNode review = node.putObject("review");
            review.put("tokens", result.getReviewTokens());
            review.put("cost", result.getReviewCost());
            String reviewSummary = result.getReview();
            if (reviewSummary.length() > 500) {
                reviewSummary = reviewSummary.substring(0, 500) + "...";
            }
            review.put("summary", reviewSummary);
        }

        try {
            Files.writeString(historyFile,
                    JSON.writeValueAsString(node) + "\n",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to write result to history: " + e.getMessage());
        }

        writeMarkdownReport(result);
    }

    private void writeMarkdownReport(MigrationResult result) {
        String runName = result.getRunName();
        if (runName == null || runName.isBlank()) return;

        Path reportFile = historyFile.getParent().resolve(runName + ".report.md");
        String date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(result.getTimestamp());

        long durationSecs = result.getDuration().toSeconds();
        long durationMin = durationSecs / 60;
        long durationSec = durationSecs % 60;

        var sb = new StringBuilder();
        sb.append("# Migration Run Report\n\n");

        // Command executed
        sb.append("## Command executed\n\n");
        sb.append("```bash\n");
        sb.append("mvn test -Dai.projects=%s".formatted(result.getProject()));
        String prompt = result.getPrompt();
        if (prompt != null && !prompt.isBlank()) {
            sb.append(" -Dai.prompt=\"%s\"".formatted(prompt));
        }
        if (!"opencode".equals(result.getAgent())) {
            sb.append(" -Dai.cmd=%s".formatted(result.getAgent()));
        }
        String userProvider = result.getUserProvider();
        if (userProvider != null && !userProvider.isBlank()) {
            sb.append(" -Dai.provider=%s".formatted(userProvider));
        }
        String userModel = result.getUserModel();
        if (userModel != null && !userModel.isBlank()) {
            sb.append(" -Dai.model=%s".formatted(userModel));
        }
        sb.append("\n```\n\n");

        // Run Info
        sb.append("## Run Info\n\n");
        sb.append("| Field | Value |\n");
        sb.append("|-------|-------|\n");
        sb.append("| Skill | `%s` |\n".formatted(result.getSkill()));
        sb.append("| Project | %s |\n".formatted(result.getProject()));
        sb.append("| Model | `%s` |\n".formatted(result.getModel()));
        sb.append("| Agent | `%s` |\n".formatted(result.getAgent()));
        boolean isSpringMigration = isSpringMigration(result);
        if (isSpringMigration) {
            sb.append("| Strategy | %s |\n".formatted(result.getStrategy()));
        }
        SkillReference ref = result.getSkillRef();
        if (ref.isRemote()) {
            sb.append("| Skill URL | %s |\n".formatted(ref.url()));
        }
        sb.append("| Title | `%s` |\n".formatted(runName));
        sb.append("| Date | %s |\n".formatted(date));
        sb.append("| Duration | %dm %ds (%ds) |\n".formatted(durationMin, durationSec, durationSecs));
        sb.append("| Tool calls | %d |\n".formatted(result.getToolCalls()));
        sb.append("\n");

        // Token Usage
        sb.append("## Token Usage\n\n");
        var modelUsages = result.getModelUsages();
        if (modelUsages != null && !modelUsages.isEmpty()) {
            sb.append("| Model | Input | Output | Cache Read | Cache Write | Cost | $/MTokens |\n");
            sb.append("|---|---|---|---|---|---|---|\n");
            for (var mu : modelUsages) {
                long muTotal = mu.inputTokens() + mu.outputTokens() + mu.cacheRead() + mu.cacheWrite();
                String rateStr = muTotal > 0
                        ? String.format(Locale.US, "$%.2f", mu.cost() / muTotal * 1_000_000)
                        : "—";
                sb.append(String.format(Locale.US, "| %s | %s | %s | %s | %s | $%.4f | %s |\n",
                        mu.model(),
                        formatTokens(mu.inputTokens()), formatTokens(mu.outputTokens()),
                        formatTokens(mu.cacheRead()), formatTokens(mu.cacheWrite()),
                        mu.cost(), rateStr));
            }
            sb.append(String.format(Locale.US, "| **Total** | **%s** | **%s** | **%s** | **%s** | **$%.2f** | |\n",
                    formatTokens(result.getInputTokens()), formatTokens(result.getOutputTokens()),
                    formatTokens(result.getCacheRead()), formatTokens(result.getCacheWrite()),
                    result.getTotalCost()));
            sb.append("\n");
            sb.append("**Grand total: %s + %s + %s + %s = %s tokens**\n\n".formatted(
                    formatTokens(result.getInputTokens()),
                    formatTokens(result.getOutputTokens()),
                    formatTokens(result.getCacheRead()),
                    formatTokens(result.getCacheWrite()),
                    formatTokens(result.getTotalTokens())));
            sb.append("**Cost formula:** `Total Cost = Sum(per-model cost)` where each model's cost = `(input * input_rate + output * output_rate + cache_read * cache_read_rate + cache_write * cache_write_rate) / 1,000,000`\n\n");
        } else {
            sb.append("| Metric | Value |\n");
            sb.append("| --- | --- |\n");
            sb.append("| Model | `%s` |\n".formatted(result.getModel()));
            sb.append("| Input tokens | %s |\n".formatted(formatTokens(result.getInputTokens())));
            sb.append("| Output tokens | %s |\n".formatted(formatTokens(result.getOutputTokens())));
            sb.append("| Thinking tokens | %s |\n".formatted(formatTokens(result.getThinkingTokens())));
            sb.append("| Cache read | %s |\n".formatted(formatTokens(result.getCacheRead())));
            sb.append("| Cache write | %s |\n".formatted(formatTokens(result.getCacheWrite())));
            sb.append("| Total tokens | %s |\n".formatted(formatTokens(result.getTotalTokens())));
            sb.append(String.format(Locale.US, "| Cost | $%.2f |\n", result.getTotalCost()));
            sb.append("\n");
        }

        // Checks
        if (!result.getChecks().isEmpty()) {
            sb.append("## Checks (%s)\n\n".formatted(result.score()));
            sb.append("| Check | Result |\n");
            sb.append("| --- | --- |\n");
            result.getChecks().forEach((check, passed) ->
                    sb.append("| %s | %s |\n".formatted(check, passed ? "PASS" : "FAIL")));
            sb.append("\n");
        }

        // Review
        if (result.getReview() != null && !result.getReview().isBlank()) {
            sb.append("## Skill Review\n\n");
            sb.append("| Metric | Value |\n");
            sb.append("| --- | --- |\n");
            sb.append("| Review tokens | %s |\n".formatted(formatTokens(result.getReviewTokens())));
            sb.append(String.format(Locale.US, "| Review cost | $%.4f |\n", result.getReviewCost()));
            sb.append("\n");
            sb.append(result.getReview());
            sb.append("\n\n");
        }


        try {
            Files.writeString(reportFile, sb.toString());
            System.out.println("  Report:   " + reportFile);
        } catch (IOException e) {
            System.err.println("Failed to write markdown report: " + e.getMessage());
        }
    }


    public void writeBenchmarkComparisonReport(Map<String, List<MigrationResult>> resultsBySkill) {
        List<String> skillNames = new ArrayList<>(resultsBySkill.keySet());
        String titleSkills = String.join(" vs ", skillNames);
        Path summaryFile = historyFile.getParent().resolve("benchmark-report.md");

        var sb = new StringBuilder();
        sb.append("# Benchmark report and comparison: %s\n\n".formatted(titleSkills));

        sb.append("## Global Summary\n\n");
        sb.append("| Skill | Runs | Avg Duration | Avg Tool calls | Avg Input | Avg Output | Avg Cache Read | Avg Cache Write | Avg Total Tokens | Avg Cost | Avg $/MTokens |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|---|---|\n");

        var skillAvgs = new ArrayList<double[]>();

        for (var entry : resultsBySkill.entrySet()) {
            String skillName = entry.getKey();
            List<MigrationResult> results = entry.getValue();
            int runs = results.size();

            double[] durations = results.stream().mapToDouble(r -> r.getDuration().toSeconds()).toArray();
            double[] gTools = results.stream().mapToDouble(MigrationResult::getToolCalls).toArray();
            double[] inputs = results.stream().mapToDouble(MigrationResult::getInputTokens).toArray();
            double[] outputs = results.stream().mapToDouble(MigrationResult::getOutputTokens).toArray();
            double[] cacheReads = results.stream().mapToDouble(MigrationResult::getCacheRead).toArray();
            double[] cacheWrites = results.stream().mapToDouble(MigrationResult::getCacheWrite).toArray();
            double[] totals = results.stream().mapToDouble(MigrationResult::getTotalTokens).toArray();
            double[] costs = results.stream().mapToDouble(MigrationResult::getTotalCost).toArray();
            double[] gRates = results.stream().mapToDouble(r ->
                    r.getTotalTokens() > 0 ? r.getTotalCost() / r.getTotalTokens() * 1_000_000 : 0).toArray();

            sb.append("| %s | %d | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n".formatted(
                    skillName, runs,
                    formatDurationWithStddev(durations),
                    formatIntWithStddev(gTools),
                    formatTokensWithStddev(inputs), formatTokensWithStddev(outputs),
                    formatTokensWithStddev(cacheReads), formatTokensWithStddev(cacheWrites),
                    formatTokensWithStddev(totals), formatCostWithStddev(costs),
                    formatRateWithStddev(gRates)));

            skillAvgs.add(new double[]{
                    mean(durations), mean(gTools), mean(inputs), mean(outputs),
                    mean(cacheReads), mean(cacheWrites), mean(totals), mean(costs),
                    mean(gRates)
            });
        }

        if (resultsBySkill.size() == 2) {
            double[] a = skillAvgs.get(0);
            double[] b = skillAvgs.get(1);

            sb.append("| **Delta** | — ");
            for (int i = 0; i < a.length; i++) {
                if (a[i] == 0) {
                    sb.append("| — ");
                } else {
                    double pct = (b[i] - a[i]) / a[i] * 100;
                    sb.append(String.format(Locale.US, "| %+.1f%% ", pct));
                }
            }
            sb.append("|\n");
        }

        sb.append("\n");

        try {
            Files.writeString(summaryFile, sb.toString());
            System.out.println("  Benchmark report: " + summaryFile);
        } catch (IOException e) {
            System.err.println("Failed to write benchmark report: " + e.getMessage());
        }
    }

    public void writeSkillSummaryReport(List<MigrationResult> results, String baseRunName) {
        if (results == null || results.isEmpty()) return;

        Path summaryFile = historyFile.getParent().resolve(baseRunName + "-report.md");
        int n = results.size();

        double[] durations = results.stream().mapToDouble(r -> r.getDuration().toSeconds()).toArray();
        double[] tools = results.stream().mapToDouble(MigrationResult::getToolCalls).toArray();
        double[] inputs = results.stream().mapToDouble(MigrationResult::getInputTokens).toArray();
        double[] outputs = results.stream().mapToDouble(MigrationResult::getOutputTokens).toArray();
        double[] cacheReads = results.stream().mapToDouble(MigrationResult::getCacheRead).toArray();
        double[] cacheWrites = results.stream().mapToDouble(MigrationResult::getCacheWrite).toArray();
        double[] totals = results.stream().mapToDouble(MigrationResult::getTotalTokens).toArray();
        double[] costs = results.stream().mapToDouble(MigrationResult::getTotalCost).toArray();
        double[] rates = results.stream().mapToDouble(r ->
                r.getTotalTokens() > 0 ? r.getTotalCost() / r.getTotalTokens() * 1_000_000 : 0).toArray();

        MigrationResult first = results.getFirst();
        String project = first.getProject();
        String agent = first.getAgent();
        boolean isSpringMigration = isSpringMigration(first);
        String strategy = first.getStrategy();

        var sb = new StringBuilder();
        sb.append("# Summary Report (%d runs)\n\n".formatted(n));

        sb.append("## Results\n\n");
        sb.append("| SKILL | ");
        if (isSpringMigration) sb.append("Spring Compatibility ? | ");
        sb.append("Agent | Duration | Tool calls | Input | Output | Cache Read | Cache Write | Total Tokens | Cost | $/MTokens |\n");
        sb.append("|---|");
        if (isSpringMigration) sb.append("---|");
        sb.append("---|---|---|---|---|---|---|---|---|---|\n");

        sb.append("| **%s** ".formatted(project));
        if (isSpringMigration) sb.append("| **%s** ".formatted(strategy));
        sb.append("| **%s** ".formatted(agent));
        sb.append("| **%s** ".formatted(formatDurationWithStddev(durations)));
        sb.append("| **%s** ".formatted(formatIntWithStddev(tools)));
        sb.append("| **%s** ".formatted(formatTokensWithStddev(inputs)));
        sb.append("| **%s** ".formatted(formatTokensWithStddev(outputs)));
        sb.append("| **%s** ".formatted(formatTokensWithStddev(cacheReads)));
        sb.append("| **%s** ".formatted(formatTokensWithStddev(cacheWrites)));
        sb.append("| **%s** ".formatted(formatTokensWithStddev(totals)));
        sb.append("| **%s** ".formatted(formatCostWithStddev(costs)));
        sb.append("| **%s** ".formatted(formatRateWithStddev(rates)));
        sb.append("|\n");

        for (int i = 0; i < n; i++) {
            MigrationResult r = results.get(i);
            long dSecs = r.getDuration().toSeconds();
            sb.append("| run %d ".formatted(i + 1));
            if (isSpringMigration) sb.append("| %s ".formatted(strategy));
            sb.append("| %s ".formatted(agent));
            sb.append("| %dm %ds ".formatted(dSecs / 60, dSecs % 60));
            sb.append("| %d ".formatted(r.getToolCalls()));
            sb.append("| %s ".formatted(formatTokens(r.getInputTokens())));
            sb.append("| %s ".formatted(formatTokens(r.getOutputTokens())));
            sb.append("| %s ".formatted(formatTokens(r.getCacheRead())));
            sb.append("| %s ".formatted(formatTokens(r.getCacheWrite())));
            sb.append("| %s ".formatted(formatTokens(r.getTotalTokens())));
            sb.append(String.format(Locale.US, "| $%.2f ", r.getTotalCost()));
            long runTotal = r.getTotalTokens();
            sb.append("| %s ".formatted(runTotal > 0
                    ? String.format(Locale.US, "$%.2f", r.getTotalCost() / runTotal * 1_000_000)
                    : "—"));
            sb.append("|\n");
        }

        sb.append("\n");

        try {
            Files.writeString(summaryFile, sb.toString());
            System.out.println("  Summary:  " + summaryFile);
        } catch (IOException e) {
            System.err.println("Failed to write summary report: " + e.getMessage());
        }
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private static double stddev(double[] values) {
        double avg = mean(values);
        double sumSq = 0;
        for (double v : values) sumSq += (v - avg) * (v - avg);
        return Math.sqrt(sumSq / values.length);
    }

    private static String formatDurationWithStddev(double[] seconds) {
        double avg = mean(seconds);
        double sd = stddev(seconds);
        long avgSecs = Math.round(avg);
        long sdSecs = Math.round(sd);
        return "%dm %ds (+/- %ds)".formatted(avgSecs / 60, avgSecs % 60, sdSecs);
    }

    private static String formatIntWithStddev(double[] values) {
        double avg = mean(values);
        double sd = stddev(values);
        return "%d (+/- %d)".formatted(Math.round(avg), Math.round(sd));
    }

    private static String formatTokensWithStddev(double[] values) {
        double avg = mean(values);
        double sd = stddev(values);
        return "%s (+/- %s)".formatted(formatTokens(Math.round(avg)), formatTokens(Math.round(sd)));
    }

    private static String formatCostWithStddev(double[] values) {
        double avg = mean(values);
        double sd = stddev(values);
        return String.format(Locale.US, "$%.2f (+/- $%.2f)", avg, sd);
    }

    private static String formatRateWithStddev(double[] values) {
        double avg = mean(values);
        double sd = stddev(values);
        return String.format(Locale.US, "$%.2f (+/- $%.2f)", avg, sd);
    }

    private static boolean isSpringMigration(MigrationResult result) {
        return "spring-boot".equals(result.getProjectType())
                && result.getSkill() != null
                && result.getSkill().toLowerCase().contains("migrate");
    }

    private static String formatTokens(long n) {
        if (n >= 1_000_000) return String.format(Locale.US, "%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format(Locale.US, "%,d", n);
        return String.valueOf(n);
    }

}