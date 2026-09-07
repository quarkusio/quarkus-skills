package io.quarkus.ai.harness.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Centralizes access to {@code ai.*} system properties and path resolution
 * used by the migration test harness.
 */
public final class AiConfig {

    private static final Pattern VALID_PROJECT_NAME = Pattern.compile("[a-zA-Z0-9_\\-]+");

    private AiConfig() {}

    /** AI provider name (e.g. {@code anthropic}, {@code google-vertex-anthropic}). Default: {@code google-vertex-anthropic}. */
    public static String aiProvider() {
        return System.getProperty("ai.provider", "google-vertex-anthropic");
    }

    /** Model ID (e.g. {@code claude-opus-4-6@default}, {@code claude-sonnet-4-5-20250514}). Default: {@code claude-opus-4-6@default}. */
    public static String aiModel() {
        return System.getProperty("ai.model", "claude-opus-4-6@default");
    }

    /**
     * Returns a human-readable display string for the resolved provider/model combination.
     *
     * @param provider the resolved provider name
     * @param model    the resolved model ID
     * @return a display string such as {@code google-vertex-anthropic/claude-opus-4-6@default}
     */
    public static String aiModelDisplay(String provider, String model) {
        if (!provider.isEmpty() && !model.isEmpty()) return provider + "/" + model;
        if (!model.isEmpty()) return model;
        return "(ai agent default)";
    }

    /** Migration strategy: {@code full} or {@code compatibility}. Default: {@code full}. */
    public static String aiStrategy() {
        return System.getProperty("ai.strategy", "full");
    }

    /** Agent Timeout in seconds. Default: {@code 300}. */
    public static int aiTimeout() {
        return Integer.parseInt(System.getProperty("ai.timeout", "300"));
    }

    /** Path to the AI agent binary. Default: {@code claude}. */
    public static String aiCmd() {
        return System.getProperty("ai.cmd", "claude");
    }

    /** Override prompt message. Default: empty (uses built-in prompt). */
    public static String aiPrompt() {
        return System.getProperty("ai.prompt", "");
    }

    /** Comma-separated list of project names to test. Default: empty (all projects). */
    public static String aiProjects() {
        return System.getProperty("ai.projects", "");
    }

    /** Whether to pass {@code --sanitize} when exporting opencode sessions. Default: {@code false}. */
    public static boolean aiSanitize() {
        return Boolean.parseBoolean(System.getProperty("ai.sanitize", "false"));
    }

    /** Whether to run verification checks after migration. Default: {@code true}. */
    public static boolean aiChecks() {
        return Boolean.parseBoolean(System.getProperty("runChecks", "true"));
    }

    /** Whether to run the skill review step after migration. Default: {@code true}. */
    public static boolean aiReview() {
        return Boolean.parseBoolean(System.getProperty("ai.review", "true"));
    }

    /** Number of times to repeat the migration per skill. Default: {@code 1}. */
    public static int runs() {
        return Integer.parseInt(System.getProperty("runs", "1"));
    }

    /** Space-separated skill arguments substituted into {@code SKILL.md} placeholders ({@code $0}, {@code $1}, etc.). */
    public static String aiArgs() {
        return System.getProperty("ai.args", "");
    }

    /** When set to {@code all}, include projects with {@code enabled: false}. Default: empty (respect the flag). */
    public static String aiEnabled() {
        return System.getProperty("ai.enabled", "");
    }

    /**
     * Comma-separated list of skills to run (max 2 for benchmark comparison).
     * Each entry can be a local skill name or a GitHub URL with optional {@code #branch/subpath}.
     * Empty entries and surrounding whitespace are ignored.
     *
     * @return the parsed list of skill references, or an empty list if {@code ai.skills} is not set
     * @throws IllegalArgumentException if more than 2 skills are specified
     */
    public static List<String> aiSkills() {
        String val = System.getProperty("ai.skills", "");
        if (val.isEmpty()) return List.of();
        List<String> skills = new ArrayList<>();
        for (String s : val.split(",")) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) continue;
            skills.add(trimmed);
        }
        if (skills.size() > 2) {
            throw new IllegalArgumentException("ai.skills supports at most 2 skills for benchmark, got: " + skills.size());
        }
        return skills;
    }

    /** Returns the compiled pattern for validating project names (alphanumerics, hyphens, and underscores). */
    public static Pattern validProjectNamePattern() {
        return VALID_PROJECT_NAME;
    }

    /**
     * Resolves the repository root directory by walking up from the current working directory
     * until a {@code skills/} subdirectory is found.
     *
     * @return the absolute path to the repository root
     */
    public static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        if (dir.getFileName().toString().equals("tests") && Files.isDirectory(dir.resolve("projects"))) {
            return dir.getParent();
        }
        if (Files.isDirectory(dir.resolve("skills"))) {
            return dir;
        }
        if (dir.getParent() != null && Files.isDirectory(dir.getParent().resolve("skills"))) {
            return dir.getParent();
        }
        return dir;
    }

    /**
     * Returns the path to the test projects' directory.
     *
     * @return the absolute path to the {@code projects/} folder
     */
    public static Path projectsDir() {
        Path testsDir = Path.of("").toAbsolutePath();
        if (Files.isDirectory(testsDir.resolve("projects"))) {
            return testsDir.resolve("projects");
        }
        return repoRoot().resolve("tests").resolve("projects");
    }

    /**
     * Returns the path to the skills directory at the repository root.
     *
     * @return the absolute path to the {@code skills/} folder
     */
    public static Path skillsDir() {
        return repoRoot().resolve("skills");
    }
}