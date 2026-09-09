package io.quarkus.ai.harness.checks;

import io.quarkus.ai.harness.launcher.AgentSkillExecutor;
import io.quarkus.ai.harness.launcher.AgentSkillExecutor.ProjectEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * CLI entry point for running project verification checks independently of
 * the AI agent. Expects a prior agent run to have produced a work directory
 * under {@code target/workdirs/<project-name>}.
 *
 * <p>Usage:
 * <pre>
 * mvn exec:exec@checks -Dai.projects=spring-rest-api
 * </pre>
 */
public class CheckRunner {

    public static void main(String[] args) throws Exception {
        AgentSkillExecutor executor = new AgentSkillExecutor();

        List<ProjectEntry> projects = executor.discoverProjects();
        if (projects.isEmpty()) {
            System.err.println("No projects found matching the configuration.");
            System.err.println("Check ai.projects and ai.enabled system properties.");
            System.exit(1);
        }

        Path workdirsBase = Path.of("target", "workdirs").toAbsolutePath();
        boolean anyFailure = false;

        for (ProjectEntry entry : projects) {
            String name = entry.config().name();
            Path workDir = workdirsBase.resolve(name);

            System.out.println("\n" + "=".repeat(60));
            System.out.println("PROJECT: " + name);
            System.out.println("  workdir: " + workDir);
            System.out.println("=".repeat(60));

            if (!Files.isDirectory(workDir)) {
                System.err.println("  Work directory not found — run the agent first.");
                anyFailure = true;
                continue;
            }

            var checks = entry.config().checks();
            if (checks.isEmpty()) {
                System.out.println("  No checks defined in project.yaml — skipping.");
                continue;
            }

            List<String> failures = AgentSkillExecutor.runChecks(entry.config(), workDir, Optional.empty());
            if (!failures.isEmpty()) {
                System.err.println("  FAILED checks: " + failures);
                anyFailure = true;
            }
        }

        if (anyFailure) {
            System.err.println("\nSome projects had check failures.");
            System.exit(1);
        }

        System.out.println("\nAll checks passed.");
    }
}