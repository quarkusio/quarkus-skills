package io.quarkus.ai.harness.test;

import io.quarkus.ai.harness.skill.SkillResolver;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SkillResolver#resolve(String, String)} covering
 * - local skill name,
 * - GitHub URL and
 * - GitHub URL with an explicit branch.
 *
 * <p>The first two tests pre-populate the download cache directory so that
 * the resolver finds an existing clone and skips the actual {@code git clone}.
 * The third test performs a real clone from GitHub.
 */
class SkillResolverTest {

    private static final Path TARGET_DIR = Path.of("target", "skill-resolver-test").toAbsolutePath();

    private Path skillsBaseDir;
    private Path downloadDir;
    private SkillResolver resolver;

    @BeforeEach
    void setUp() throws Exception {
        skillsBaseDir = TARGET_DIR.resolve("skills");
        downloadDir = TARGET_DIR.resolve("downloads");
        Files.createDirectories(skillsBaseDir);
        Files.createDirectories(downloadDir);
        resolver = new SkillResolver(skillsBaseDir, downloadDir);
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (Files.exists(TARGET_DIR)) {
            try (var walk = Files.walk(TARGET_DIR)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            }
        }
    }

    @Test
    @DisplayName("Local skill name resolves under skillsBaseDir")
    void resolveLocalName() throws Exception {
        Path result = resolver.resolve("spring-boot-to-quarkus", null);

        assertEquals(skillsBaseDir.resolve("spring-boot-to-quarkus"), result);
    }

    @Test
    @DisplayName("GitHub URL parses branch and subpath without explicit branch")
    void resolveGitHubUrl() throws Exception {
        // URL: https://github.com/org/repo/tree/main/skills/my-skill
        //   cloneUrl = https://github.com/org/repo
        //   branch   = main          (first segment after /tree/)
        //   subPath  = skills/my-skill
        String dirName = "https://github.com/org/repo@main"
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        Path cloneDir = downloadDir.resolve(dirName);
        Files.createDirectories(cloneDir.resolve("skills/my-skill"));

        Path result = resolver.resolve(
                "https://github.com/org/repo/tree/main/skills/my-skill", null);

        assertEquals(cloneDir.resolve("skills/my-skill"), result);
    }

    @Test
    @DisplayName("GitHub repo URL with #branch/subpath clones and resolves correctly")
    void resolveGitHubUrlWithExplicitBranch() throws Exception {
        // Simulates: mvn test -Dai.skills=https://github.com/aureamunoz/quarkus-skills#add-new-migration-from-spring/skills/migrate-spring-to-quarkus
        //
        // The resolver splits on '#':
        //   URL:     https://github.com/aureamunoz/quarkus-skills
        //   branch:  add-new-migration-from-spring
        //   subpath: skills/migrate-spring-to-quarkus

        Path result = resolver.resolve(
                "https://github.com/aureamunoz/quarkus-skills#add-new-migration-from-spring/skills/migrate-spring-to-quarkus");

        assertTrue(Files.isDirectory(result),
                "Resolved skill path should exist after clone: " + result);
        assertTrue(result.endsWith(Path.of("skills", "migrate-spring-to-quarkus")),
                "Path should end with skills/migrate-spring-to-quarkus but was: " + result);
    }
}
