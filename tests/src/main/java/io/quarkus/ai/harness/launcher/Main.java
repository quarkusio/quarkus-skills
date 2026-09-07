package io.quarkus.ai.harness.launcher;

import io.quarkus.ai.harness.launcher.AgentSkillExecutor.ExecutionResult;
import io.quarkus.ai.harness.launcher.AgentSkillExecutor.ProjectEntry;

import java.util.List;

/**
 * CLI entry point for running AI agent skills against test projects.
 *
 * <p>Reads configuration from system properties (same as the JUnit test harness).
 *
 * <p>Usage:
 * <pre>
 * # Via Maven exec plugin
 * mvn exec:exec -Dai.projects=spring-rest-api -Dai.cmd=claude
 *
 * # Via java -jar (requires maven-jar-plugin with Main-Class manifest)
 * java -Dai.projects=spring-rest-api -jar migration-tests.jar
 * </pre>
 */
public class Main {

    public static void main(String[] args) throws Exception {
        AgentSkillExecutor executor = new AgentSkillExecutor();

        List<ProjectEntry> projects = executor.discoverProjects();
        if (projects.isEmpty()) {
            System.err.println("No projects found matching the configuration.");
            System.err.println("Check ai.projects and ai.enabled system properties.");
            System.exit(1);
        }

        System.out.printf("Discovered %d project(s) to process%n%n", projects.size());

        boolean anyFailure = false;

        for (ProjectEntry entry : projects) {
            ExecutionResult result = executor.execute(entry.config(), entry.projectDir());

            if (!result.failures().isEmpty() && !result.benchmark()) {
                System.err.println("\nFAILED: " + entry.config().name());
                System.err.println("  Checks failed: " + result.failures());
                System.err.println("  Work dir: " + result.workDir());
                System.err.println("  Score: " + result.score());
                anyFailure = true;
            }
        }

        executor.generateBenchmarkReport();

        if (anyFailure) {
            System.err.println("\nSome projects had check failures.");
            System.exit(1);
        }

        System.out.println("\nAll projects completed successfully.");
    }
}