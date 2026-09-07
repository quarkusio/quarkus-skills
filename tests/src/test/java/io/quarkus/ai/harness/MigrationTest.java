package io.quarkus.ai.harness;

import io.quarkus.ai.harness.launcher.AgentSkillExecutor;
import io.quarkus.ai.harness.launcher.AgentSkillExecutor.ExecutionResult;
import io.quarkus.ai.harness.config.ProjectConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 test suite that runs migration skills against test projects and verifies the results.
 *
 * <p>Delegates all orchestration logic to {@link AgentSkillExecutor} — this class
 * is a thin JUnit wrapper that provides parameterized test discovery and assertions.
 *
 * <p>Configuration via system properties:
 * <ul>
 *   <li>{@code ai.model} — model to use (default: vertex-anthropic/claude-sonnet-4-5@20250929)</li>
 *   <li>{@code ai.strategy} — migration strategy: full or compatibility (default: full)</li>
 *   <li>{@code ai.timeout} — timeout in seconds per project (default: 300)</li>
 *   <li>{@code ai.cmd} — path to AI agent binary (default: claude)</li>
 *   <li>{@code ai.projects} — run only the project(s) (default: all)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * # Run all projects with defaults
 * mvn test -Pintegration
 *
 * # Run a specific project
 * mvn test -Pintegration -Dai.projects=spring-rest-api
 *
 * # Compare models
 * mvn test -Pintegration -Dai.model=vertex-anthropic/claude-sonnet-4-5@20250929
 * </pre>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MigrationTest {

    private static final AgentSkillExecutor executor = new AgentSkillExecutor();

    static Stream<Arguments> projectsToTest() throws IOException {
        return executor.discoverProjects().stream()
                .map(entry -> Arguments.of(entry.config(), entry.projectDir()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("projectsToTest")
    @Order(1)
    void migrate(ProjectConfig config, Path projectDir) throws Exception {
        ExecutionResult result = executor.execute(config, projectDir);

        if (!result.failures().isEmpty() && !result.benchmark()) {
            fail("Migration checks failed: " + result.failures() + "\n" +
                    "Work dir preserved at: " + result.workDir() + "\n" +
                    "Score: " + result.score());
        }
    }

    @AfterAll
    static void generateBenchmarkReport() {
        executor.generateBenchmarkReport();
    }
}