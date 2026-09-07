package io.quarkus.ai.harness.test;

import io.quarkus.ai.harness.result.MigrationResult;
import io.quarkus.ai.harness.result.ResultsTracker;
import io.quarkus.ai.harness.skill.SkillReference;
import io.quarkus.ai.harness.runner.AgentRunner;
import io.quarkus.ai.harness.runner.claude.ClaudeRunner;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the benchmark report content
 */
class BenchmarkReportTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("writeBenchmarkReportAndCheckDelta creates a benchmark report and computes delta between two skills compared")
    void writeBenchmarkReportAndCheckDelta() throws IOException {
        Path historyFile = tempDir.resolve("history.jsonl");
        ResultsTracker tracker = new ResultsTracker(historyFile);

        MigrationResult rA = buildResult("project", "claude-opus-4-6", "skillA-run");
        rA.setDuration(Duration.ofSeconds(66));
        rA.setTotalTokens(240_654);
        rA.setTotalCost(0.50);
        rA.setInputTokens(0);
        rA.setOutputTokens(0);
        rA.setCacheRead(0);
        rA.setCacheWrite(0);

        MigrationResult rB = buildResult("project", "claude-opus-4-6", "skillB-run");
        rB.setDuration(Duration.ofSeconds(78));
        rB.setTotalTokens(446_433);
        rB.setTotalCost(0.86);
        rB.setInputTokens(0);
        rB.setOutputTokens(0);
        rB.setCacheRead(0);
        rB.setCacheWrite(0);

        Map<String, List<MigrationResult>> bySkill = new LinkedHashMap<>();
        bySkill.put("skills/skill-a", List.of(rA));
        bySkill.put("skills/skill-b", List.of(rB));

        tracker.writeBenchmarkComparisonReport(bySkill);

        String benchmark = Files.readString(tempDir.resolve("benchmark-report.md"));
        assertContains(benchmark, "# Benchmark report and comparison:");
        assertContains(benchmark, "| **Delta** |");

        // Duration delta: (78-66)/66 * 100 = +18.2%
        assertContains(benchmark, "+18.2%");

        // Total tokens delta: (446433-240654)/240654 * 100 = +85.5%
        assertContains(benchmark, "+85.5%");

        // Cost delta: (0.86-0.50)/0.50 * 100 = +72.0%
        assertContains(benchmark, "+72.0%");
    }

    @Test
    @DisplayName("writeBenchmark with single skill produces no delta row")
    void writeBenchmarkTestWithNoDelta() throws IOException {
        Path historyFile = tempDir.resolve("history.jsonl");
        ResultsTracker tracker = new ResultsTracker(historyFile);

        MigrationResult r = buildResult("project", "claude-opus-4-6", "single-run");
        r.setDuration(Duration.ofSeconds(66));
        r.setTotalTokens(240_654);
        r.setTotalCost(0.50);

        Map<String, List<MigrationResult>> bySkill = Map.of("single-skill", List.of(r));
        tracker.writeBenchmarkComparisonReport(bySkill);

        String global = Files.readString(tempDir.resolve("benchmark-report.md"));
        assertFalse(global.contains("**Delta**"), "single-skill summary should not have a delta row");
    }

    // ─── Benchmark from fixture JSONL files ──────────────────────

    @Test
    @DisplayName("end-to-end: two fixture sessions produce correct benchmark-report.md with delta")
    void endToEndTestFromFixtures() throws IOException {
        Path fixture1 = tempDir.resolve("session1.jsonl");
        Path fixture2 = tempDir.resolve("session2.jsonl");
        try (var in1 = getClass().getResourceAsStream("/sessions/dummy1_claude_session.jsonl");
             var in2 = getClass().getResourceAsStream("/sessions/dummy2_claude_session.jsonl")) {
            assertNotNull(in1, "dummy1 fixture not found");
            assertNotNull(in2, "dummy2 fixture not found");
            Files.copy(in1, fixture1);
            Files.copy(in2, fixture2);
        }

        ClaudeRunner runner = new ClaudeRunner(
                "claude", null, "claude-opus-4-6", Path.of("/tmp/skill"),
                "full", 300, "", "", false);

        // Extract from fixture 1 (skill A — efficient)
        // Token counts sum from modelUsage: haiku(600,20,0,0) + opus(4,300,30000,15000)
        AgentRunner.UsageStats stats1 = runner.extractUsage(
                Collections.singletonList(fixture1.toString()));
        assertEquals(604, stats1.inputTokens(), "fixture1 input: haiku(600) + opus(4)");
        assertEquals(320, stats1.outputTokens(), "fixture1 output: haiku(20) + opus(300)");
        assertEquals(30_000, stats1.cacheRead(), "fixture1 cacheRead");
        assertEquals(15_000, stats1.cacheWrite(), "fixture1 cacheWrite");
        assertEquals(45_924, stats1.totalTokens(), "fixture1 totalTokens");
        assertEquals(0.15, stats1.totalCost(), 0.001, "fixture1 cost");
        assertEquals(3, stats1.apiCalls(), "fixture1 apiCalls");
        assertEquals(1, stats1.toolCalls(), "fixture1 toolCalls");

        // Extract from fixture 2 (skill B — expensive)
        // Token counts sum from modelUsage: haiku(1000,40,0,0) + opus(12,600,60000,30000)
        AgentRunner.UsageStats stats2 = runner.extractUsage(
                Collections.singletonList(fixture2.toString()));
        assertEquals(1012, stats2.inputTokens(), "fixture2 input: haiku(1000) + opus(12)");
        assertEquals(640, stats2.outputTokens(), "fixture2 output: haiku(40) + opus(600)");
        assertEquals(60_000, stats2.cacheRead(), "fixture2 cacheRead");
        assertEquals(30_000, stats2.cacheWrite(), "fixture2 cacheWrite");
        assertEquals(91_652, stats2.totalTokens(), "fixture2 totalTokens");
        assertEquals(0.30, stats2.totalCost(), 0.001, "fixture2 cost");
        assertEquals(4, stats2.apiCalls(), "fixture2 apiCalls");
        assertEquals(2, stats2.toolCalls(), "fixture2 toolCalls");

        // Build MigrationResults for two different skills
        MigrationResult rA = buildResultFromStats(stats1, "skill-fast",
                "/tmp/skillA", "dummy_skill-fast", Duration.ofSeconds(40));
        MigrationResult rB = buildResultFromStats(stats2, "skill-thorough",
                "/tmp/skillB", "dummy_skill-thorough", Duration.ofSeconds(80));

        // Generate Benchmark report
        Path historyFile = tempDir.resolve("history.jsonl");
        ResultsTracker tracker = new ResultsTracker(historyFile);

        Map<String, List<MigrationResult>> bySkill = new LinkedHashMap<>();
        bySkill.put("skills/skill-fast", List.of(rA));
        bySkill.put("skills/skill-thorough", List.of(rB));
        tracker.writeBenchmarkComparisonReport(bySkill);

        String benchmark = Files.readString(tempDir.resolve("benchmark-report.md"));

        assertContains(benchmark, "skills/skill-fast vs skills/skill-thorough");

        // Skill A row
        assertContains(benchmark, "| skills/skill-fast | 1 |");
        assertContains(benchmark, "45,924 (+/- 0)");
        assertContains(benchmark, "$0.15 (+/- $0.00)");

        // Skill B row
        assertContains(benchmark, "| skills/skill-thorough | 1 |");
        assertContains(benchmark, "91,652 (+/- 0)");
        assertContains(benchmark, "$0.30 (+/- $0.00)");

        // Delta row
        assertContains(benchmark, "| **Delta** |");
        // Duration: (80-40)/40 = +100.0%
        assertContains(benchmark, "+100.0%");
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
