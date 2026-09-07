package io.quarkus.ai.harness.test;

import io.quarkus.ai.harness.result.MigrationResult;
import io.quarkus.ai.harness.result.ResultsTracker;
import io.quarkus.ai.harness.skill.SkillReference;
import io.quarkus.ai.harness.runner.AgentRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the data of the Skill report generated
 */
class SkillRunReportTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("writeSkillReportSingleRun with single run has zero stddev")
    void writeSkillReportSingleRun() throws IOException {
        Path historyFile = tempDir.resolve("history.jsonl");
        ResultsTracker tracker = new ResultsTracker(historyFile);

        MigrationResult r = buildResult("dummy", "claude-opus-4-6", "solo");
        r.setDuration(Duration.ofSeconds(28));
        r.setTotalTokens(75_241);
        r.setTotalCost(0.195412);

        tracker.writeSkillSummaryReport(List.of(r), "single-run");

        String benchmark = Files.readString(tempDir.resolve("single-run-report.md"));

        assertContains(benchmark, "0m 28s (+/- 0s)");
        assertContains(benchmark, "$0.20 (+/- $0.00)");
    }

    @Test
    @DisplayName("writeSkillReportFor2Runs computes correct averages and stddev for 2 runs")
    void writeSkillReportFor2Runs() throws IOException {
        Path historyFile = tempDir.resolve("history.jsonl");
        ResultsTracker tracker = new ResultsTracker(historyFile);

        MigrationResult r1 = buildResult("dummy", "claude-opus-4-6", "run1");
        r1.setDuration(Duration.ofSeconds(60));
        r1.setTotalTokens(70_000);
        r1.setTotalCost(0.50);
        r1.setInputTokens(800);
        r1.setOutputTokens(500);
        r1.setCacheRead(45_000);
        r1.setCacheWrite(23_700);

        MigrationResult r2 = buildResult("dummy", "claude-opus-4-6", "run2");
        r2.setDuration(Duration.ofSeconds(80));
        r2.setTotalTokens(80_000);
        r2.setTotalCost(0.70);
        r2.setInputTokens(1000);
        r2.setOutputTokens(600);
        r2.setCacheRead(55_000);
        r2.setCacheWrite(23_400);

        tracker.writeSkillSummaryReport(List.of(r1, r2), "two-runs");

        String benchmark = Files.readString(tempDir.resolve("two-runs-report.md"));

        // Avg duration = 70s => 1m 10s, stddev = 10s
        assertContains(benchmark, "1m 10s (+/- 10s)");

        // Avg cost = $0.60, stddev = $0.10
        assertContains(benchmark, "$0.60 (+/- $0.10)");

        // Avg total tokens = 75,000, stddev = 5,000
        assertContains(benchmark, "75,000 (+/- 5,000)");
    }

    // ─── Mean / Stddev math ───────────────────────────────────────────
    @Test
    @DisplayName("mean and stddev formulas: population stddev (not sample)")
    void meanAndStddev() {
        double[] values = {60.0, 80.0};
        double mean = 0;
        for (double v : values) mean += v;
        mean /= values.length;

        double sumSq = 0;
        for (double v : values) sumSq += (v - mean) * (v - mean);
        double stddev = Math.sqrt(sumSq / values.length);

        assertEquals(70.0, mean, 0.001);
        assertEquals(10.0, stddev, 0.001);
    }

    @Test
    @DisplayName("stddev of identical values is zero")
    void stddevZero() {
        double[] values = {42.0, 42.0, 42.0};
        double mean = 42.0;
        double sumSq = 0;
        for (double v : values) sumSq += (v - mean) * (v - mean);
        double stddev = Math.sqrt(sumSq / values.length);

        assertEquals(0.0, stddev, 0.0001);
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private MigrationResult buildResult(String project, String model, String runName) {
        SkillReference skillRef = new SkillReference("test-skill", null, "/tmp/skill");
        MigrationResult result = new MigrationResult("claude", project, model, "full", skillRef);
        result.setRunName(runName);
        result.setDuration(Duration.ofSeconds(28));
        result.setPrompt("Say Hello.");
        result.setTotalTokens(74_406);
        result.setTotalCost(0.195412);
        result.setInputTokens(5);
        result.setOutputTokens(494);
        result.setCacheRead(48_661);
        result.setCacheWrite(25_246);
        result.setToolCalls(2);
        return result;
    }

    private MigrationResult buildResultFromStats(AgentRunner.UsageStats stats,
            String skillName, String skillPath, String runName, Duration duration) {
        SkillReference ref = new SkillReference(skillName, null, skillPath);
        MigrationResult r = new MigrationResult("claude", "dummy", "claude-opus-4-6", "full", ref);
        r.setRunName(runName);
        r.setDuration(duration);
        r.setTotalTokens(stats.totalTokens());
        r.setTotalCost(stats.totalCost());
        r.setToolCalls(stats.toolCalls());
        r.setInputTokens(stats.inputTokens());
        r.setOutputTokens(stats.outputTokens());
        r.setCacheRead(stats.cacheRead());
        r.setCacheWrite(stats.cacheWrite());
        return r;
    }

    private static void assertContains(String content, String expected) {
        assertTrue(content.contains(expected),
                "Expected to find:\n  " + expected + "\nin:\n" + content);
    }
}
