package com.migration.validator.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Detects build tool and framework type for a Java project.
 */
public class FrameworkUtils {

    public enum Framework {
        SPRING_BOOT,
        QUARKUS,
        JAKARTA_EE
    }

    private static final List<String> SPRING_BOOT_KEYWORDS = Arrays.asList(
            "spring-boot", "org.springframework.boot");

    private static final List<String> QUARKUS_KEYWORDS = Arrays.asList(
            "io.quarkus", "quarkus-");

    /**
     * Detect the build tool used by the project.
     *
     * @param projectRoot path to the project root
     * @return "maven", "gradle", or null if neither was detected
     */
    public static String detectBuildTool(Path projectRoot) {
        if (Files.exists(projectRoot.resolve("pom.xml"))) {
            return "maven";
        }
        if (Files.exists(projectRoot.resolve("build.gradle"))
                || Files.exists(projectRoot.resolve("build.gradle.kts"))) {
            return "gradle";
        }
        return null;
    }

    /**
     * Detect the application framework by reading the build file.
     *
     * @param projectRoot path to the project root
     * @return detected Framework enum value
     * @throws IOException if the build file cannot be read
     */
    public static Framework detectFramework(Path projectRoot) throws IOException {
        String buildTool = detectBuildTool(projectRoot);
        if ("maven".equals(buildTool)) {
            return analyzeMaven(projectRoot);
        } else if ("gradle".equals(buildTool)) {
            return analyzeGradle(projectRoot);
        } else {
            throw new IOException(
                    "Could not detect build framework in the project root: " + projectRoot);
        }
    }

    private static Framework analyzeMaven(Path projectRoot) throws IOException {
        Path pomPath = projectRoot.resolve("pom.xml");
        String content = Files.readString(pomPath).toLowerCase();
        if (containsAny(content, QUARKUS_KEYWORDS)) {
            return Framework.QUARKUS;
        }
        if (containsAny(content, SPRING_BOOT_KEYWORDS)) {
            return Framework.SPRING_BOOT;
        }
        return Framework.JAKARTA_EE;
    }

    private static Framework analyzeGradle(Path projectRoot) throws IOException {
        for (String fileName : Arrays.asList("build.gradle", "build.gradle.kts")) {
            Path gradlePath = projectRoot.resolve(fileName);
            if (!Files.exists(gradlePath)) {
                continue;
            }
            String content = Files.readString(gradlePath);
            if (containsAny(content.toLowerCase(), QUARKUS_KEYWORDS)) {
                return Framework.QUARKUS;
            }
            if (containsAny(content.toLowerCase(), SPRING_BOOT_KEYWORDS)) {
                return Framework.SPRING_BOOT;
            }
        }
        return Framework.JAKARTA_EE;
    }

    private static boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
