package io.quarkus.ai.harness.launcher;

import io.quarkus.ai.harness.runner.AgentRunner;
import io.quarkus.ai.harness.runner.RunnerRegistry;
import tools.jackson.dataformat.yaml.YAMLMapper;
import io.quarkus.ai.harness.checks.ProjectVerifier;
import io.quarkus.ai.harness.config.ProjectConfig;
import io.quarkus.ai.harness.result.MigrationResult;
import io.quarkus.ai.harness.result.ResultsTracker;
import io.quarkus.ai.harness.skill.SkillReference;
import io.quarkus.ai.harness.skill.SkillResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.quarkus.ai.harness.config.AiConfig.*;
import static io.quarkus.ai.harness.runner.RunnerRegistry.resolveModel;
import static io.quarkus.ai.harness.runner.RunnerRegistry.resolveProvider;

/**
 * Orchestrates the execution of AI agent skills against test projects.
 *
 * <p>Handles project discovery, work directory preparation, agent execution,
 * usage extraction, verification checks, and result tracking. This class
 * contains no test-framework dependencies and can be used from JUnit tests,
 * a CLI main class, or any other entry point.
 */
public class AgentSkillExecutor {

    private static final YAMLMapper YAML = new YAMLMapper();

    private final ResultsTracker tracker;
    private final SkillResolver skillResolver;
    private final Map<String, List<MigrationResult>> skillsComparisonResults = new LinkedHashMap<>();

    public AgentSkillExecutor() {
        this(ResultsTracker.defaultTracker(),
                new SkillResolver(skillsDir(), Path.of("target", "skills").toAbsolutePath()));
    }

    public AgentSkillExecutor(ResultsTracker tracker, SkillResolver skillResolver) {
        this.tracker = tracker;
        this.skillResolver = skillResolver;
    }

    /**
     * A discovered project entry pairing its configuration with its directory.
     */
    public record ProjectEntry(ProjectConfig config, Path projectDir) {}

    /**
     * The outcome of executing skills against a single project.
     */
    public record ExecutionResult(
            List<String> failures,
            Path workDir,
            String score,
            boolean benchmark
    ) {}

    /**
     * Discovers test projects by scanning the projects directory for
     * subdirectories containing a {@code project.yaml} file.
     *
     * <p>Results can be filtered by project name via {@code ai.projects}
     * or include disabled tests via {@code ai.enabled=all}.
     *
     * @return the list of discovered project entries
     * @throws IOException if the projects directory cannot be listed or a
     *         {@code project.yaml} file cannot be parsed
     */
    public List<ProjectEntry> discoverProjects() throws IOException {
        Path pathToProjectsDir = projectsDir();
        String projectList = aiProjects();

        Set<String> projects = new LinkedHashSet<>();
        if (!projectList.isEmpty()) {
            for (String p : projectList.split(",")) {
                String trimmed = p.trim();
                if (trimmed.isEmpty()) continue;
                if (!validProjectNamePattern().matcher(trimmed).matches()) {
                    throw new IllegalArgumentException(
                            "Invalid project name: '" + trimmed + "' — only alphanumerics, hyphens, and underscores are allowed");
                }
                projects.add(trimmed);
            }
        }

        boolean includeDisabled = "all".equalsIgnoreCase(aiEnabled());
        boolean filterByName = !projectList.isEmpty();

        try (Stream<Path> dirs = Files.list(pathToProjectsDir)) {
            return dirs
                    .filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve("project.yaml")))
                    .filter(p -> !filterByName || projects.contains(p.getFileName().toString()))
                    .sorted()
                    .map(p -> {
                        ProjectConfig config = YAML.readValue(
                                p.resolve("project.yaml").toFile(),
                                ProjectConfig.class);
                        return new ProjectEntry(config, p);
                    })
                    .filter(entry -> entry.config().isTestEnabled() || includeDisabled || filterByName)
                    .toList();
        }
    }

    /**
     * Executes the configured skills against a single project.
     *
     * <p>For each skill (from {@code ai.skills} or the project's default),
     * runs the agent the configured number of times, extracts usage stats,
     * runs verification checks, optionally runs a skill review, and records
     * results via the {@link ResultsTracker}.
     *
     * @param config     the project configuration
     * @param projectDir the path to the project directory (containing {@code project.yaml})
     * @return the execution result with any check failures
     * @throws Exception if agent execution or work directory preparation fails
     */
    public ExecutionResult execute(ProjectConfig config, Path projectDir) throws Exception {
        String provider = resolveProvider(aiCmd(), aiProvider());
        String model = resolveModel(aiCmd(), aiModel());
        String modelDisplay = aiModelDisplay(provider, model);

        boolean projectDefinesChecks = config.checks() != null && !config.checks().isEmpty();
        boolean hasChecks = aiChecks() && projectDefinesChecks;
        int totalRuns = runs();

        List<String> skills = aiSkills();
        boolean isBenchmark = skills.size() > 1;
        if (skills.isEmpty()) {
            skills = List.of(config.skill());
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("PROJECT: " + config.name());
        System.out.println("  agent:    " + aiCmd());
        System.out.println("  provider: " + (provider.isEmpty() ? "(n/a)" : provider));
        System.out.println("  model:    " + model);
        System.out.println("  timeout:  " + aiTimeout() + "s");
        System.out.println("  checks:   " + (hasChecks ? config.checks().keySet() : !aiChecks() ? "disabled (runChecks=false)" : "disabled (none defined)"));
        System.out.println("  skills:   " + skills);
        if (totalRuns > 1) {
            System.out.println("  runs:     " + totalRuns + " (per skill)");
        }
        System.out.println("=".repeat(60));

        Path outputDir = Path.of("target", "runs").toAbsolutePath();
        int timeout = config.timeout() > 0 ? config.timeout() : aiTimeout();
        String modelShort = model.isEmpty() ? "default" : model.replaceAll("[^a-zA-Z0-9-]", "-");

        List<String> lastFailures = new ArrayList<>();
        Path lastWorkDir = null;
        String lastScore = "0/0";

        for (String skillRefStr : skills) {
            Path skillPath = skillResolver.resolve(skillRefStr);

            boolean isUrl = skillRefStr.startsWith("https://") || skillRefStr.startsWith("http://") || skillRefStr.startsWith("git@");
            SkillReference skillRef = new SkillReference(
                    isUrl ? extractSkillShortName(skillRefStr) : skillRefStr,
                    isUrl ? skillRefStr : null,
                    skillPath.toString());

            if (!Files.isDirectory(skillPath)) {
                throw new IOException("Skill directory not found: " + skillPath);
            }

            String skillShort = extractSkillShortName(skillRefStr);
            boolean isSpringMigration = "spring-boot".equals(config.type()) && isMigrationSkill(skillRefStr);
            if (isSpringMigration) {
                System.out.println("  strategy: " + aiStrategy());
            }
            String suffix = isSpringMigration ? "_" + modelShort + "_" + aiStrategy() : "_" + modelShort;
            String baseRunName = isBenchmark
                    ? config.name() + "_" + skillShort + suffix
                    : config.name() + suffix;

            if (isBenchmark) {
                System.out.println("\n" + "=".repeat(60));
                System.out.printf("  SKILL: %s%n", skillRefStr);
                System.out.println("=".repeat(60));
            }

            List<MigrationResult> skillResultRuns = new ArrayList<>();

            for (int run = 1; run <= totalRuns; run++) {
                String runName = totalRuns > 1 ? baseRunName + "_run" + run : baseRunName;

                if (totalRuns > 1) {
                    System.out.println("\n" + "-".repeat(60));
                    System.out.printf("  RUN %d/%d%n", run, totalRuns);
                    System.out.println("-".repeat(60));
                }

                // 1. Prepare a fresh working directory
                Path workDir = prepareWorkDir(config, projectDir);
                lastWorkDir = workDir;

                System.out.println("  workdir:  " + workDir);
                System.out.println("  outputs:  " + outputDir.resolve(runName + ".*"));

                MigrationResult result = new MigrationResult(aiCmd(),
                        config.name(), modelDisplay, aiStrategy(), skillRef);
                result.setWorkDir(workDir.toString());
                result.setRunName(runName);
                result.setPrompt(aiPrompt());
                result.setUserProvider(aiProvider());
                result.setUserModel(aiModel());
                result.setProjectType(config.type());

                // 2. Run migration
                AgentRunner runner = RunnerRegistry.getRunner(aiCmd(), provider, model, skillPath, aiStrategy(), timeout, aiPrompt(), aiArgs(), aiSanitize());

                System.out.printf("  Running migration agent: %s ...%n", aiCmd());
                AgentRunner.RunOutput output = runner.run(workDir, outputDir, runName);

                result.setAiExitCode(output.exitCode());
                result.setDuration(output.duration());
                result.setSessionFiles(output.sessionFiles());

                System.out.println("  Migration completed in " + output.duration().toSeconds() + "s (exit=" + output.exitCode() + ")");

                // 3. Extract usage stats from session
                AgentRunner.UsageStats usage = runner.extractUsage(output.sessionFiles());
                result.setTotalTokens(usage.totalTokens());
                result.setTotalCost(usage.totalCost());
                result.setApiCalls(usage.apiCalls());
                result.setToolCalls(usage.toolCalls());
                result.setInputTokens(usage.inputTokens());
                result.setOutputTokens(usage.outputTokens());
                result.setThinkingTokens(usage.thinkingTokens());
                result.setCacheRead(usage.cacheRead());
                result.setCacheWrite(usage.cacheWrite());
                result.setModelUsages(usage.modelUsages());

                // 4. Run checks
                List<String> failures = new ArrayList<>();
                if (hasChecks) {
                    ProjectVerifier checks = new ProjectVerifier(workDir);
                    System.out.println("  Running checks...");

                    config.checks().forEach((checkName, checkConfig) -> {
                        System.out.print("    " + checkName + " ... ");
                        boolean passed = checks.runCheck(checkName, checkConfig);
                        result.addCheck(checkName, passed);
                        System.out.println(passed ? "PASS" : "FAIL");
                        if (!passed) {
                            failures.add(checkName);
                        }
                    });
                } else {
                    System.out.println("  Skipping checks" + (!aiChecks() ? " (runChecks=false)" : " (none defined)"));
                }

                // 5. Run skill review (separate ai session)
                if (aiReview() && hasChecks && !output.sessionFiles().isEmpty()) {
                    AgentRunner.ReviewOutput reviewOutput = runner.review(
                            output.sessionFiles().getFirst(), workDir, outputDir, runName, skillPath, result.getChecks());
                    result.setReview(reviewOutput.review());
                    result.setReviewTokens(reviewOutput.usage().totalTokens());
                    result.setReviewCost(reviewOutput.usage().totalCost());
                } else {
                    String reason = !aiReview() ? " (ai.review=false)"
                            : output.sessionFiles().isEmpty() ? " (no session files exported)"
                            : " (no checks defined)";
                    System.out.println("  Skipping skill review" + reason);
                }

                // 6. Record result
                tracker.record(result);
                skillResultRuns.add(result);
                System.out.println("\n" + result);

                lastFailures = failures;
                lastScore = result.score();
            }

            // 7. Write per-skill report when multiple runs have been executed
            if (totalRuns > 1) {
                tracker.writeSkillSummaryReport(skillResultRuns, baseRunName);
            }

            skillsComparisonResults.computeIfAbsent(skillRefStr, k -> new ArrayList<>()).addAll(skillResultRuns);
        }

        return new ExecutionResult(lastFailures, lastWorkDir, lastScore, isBenchmark);
    }

    /**
     * Generates a benchmark comparison report if multiple skills or projects
     * have been executed. Should be called after all {@link #execute} calls
     * are complete.
     */
    public void generateBenchmarkReport() {
        boolean multipleSkills = skillsComparisonResults.size() > 1;
        boolean multipleProjects = skillsComparisonResults.values().stream()
                .flatMap(List::stream)
                .map(MigrationResult::getProject)
                .distinct()
                .count() > 1;

        if (multipleSkills || multipleProjects) {
            tracker.writeBenchmarkComparisonReport(skillsComparisonResults);
        }
    }

    private static String extractSkillShortName(String skillRef) {
        String name = skillRef;
        if (skillRef.contains("/")) {
            name = skillRef.substring(skillRef.lastIndexOf('/') + 1);
        }
        return name.replaceAll("[^a-zA-Z0-9-]", "-");
    }

    private static boolean isMigrationSkill(String skillRef) {
        String name = extractSkillShortName(skillRef).toLowerCase();
        return name.contains("migrate");
    }

    private Path prepareWorkDir(ProjectConfig config, Path projectDir) throws IOException, InterruptedException {
        Path workdirsBase = Path.of("").toAbsolutePath().resolve("target").resolve("workdirs");
        Path workDir = workdirsBase.resolve(config.name());
        if (Files.exists(workDir)) {
            try (var walk = Files.walk(workDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            }
        }
        Files.createDirectories(workDir);

        if (config.isLocal()) {
            Path source = projectDir.resolve("source");
            if (!Files.isDirectory(source)) {
                throw new IOException("Local source directory not found: " + source);
            }
            copyDirectory(source, workDir);
        } else {
            List<String> cmd = new ArrayList<>(List.of(
                    "git", "clone", "--depth", "1"));
            if (config.ref() != null && !config.ref().isBlank()) {
                cmd.addAll(List.of("--branch", config.ref()));
            }
            cmd.add(config.source());
            cmd.add(workDir.toString());

            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();

            boolean done = p.waitFor(60, TimeUnit.SECONDS);
            if (!done || p.exitValue() != 0) {
                throw new IOException("Failed to clone " + config.source());
            }
        }

        return workDir;
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            stream.forEach(src -> {
                Path dest = target.resolve(source.relativize(src));
                try {
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}