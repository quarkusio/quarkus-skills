package com.migration.validator.ui;

import java.io.IOException;
import java.nio.file.*;

/**
 * BuildSystemUtils - Common build system operations for UI validators
 */
public class BuildSystemUtils {

    /**
     * Detect build system (maven, gradle, or unknown)
     */
    public static String detectBuildSystem(Path projectDir) {
        if (Files.exists(projectDir.resolve("pom.xml"))) {
            return "maven";
        } else if (Files.exists(projectDir.resolve("build.gradle")) ||
                Files.exists(projectDir.resolve("build.gradle.kts"))) {
            return "gradle";
        }
        return "unknown";
    }

    /**
     * Check if a dependency exists in the project
     */
    public static boolean checkDependency(Path projectDir, String groupId, String artifactId) {
        String buildSystem = detectBuildSystem(projectDir);
        if ("maven".equals(buildSystem)) {
            return checkPomDependency(projectDir, groupId, artifactId);
        } else if ("gradle".equals(buildSystem)) {
            return checkGradleDependency(projectDir, groupId, artifactId);
        }
        return false;
    }

    /**
     * Check if a dependency exists in pom.xml
     */
    private static boolean checkPomDependency(Path projectDir, String groupId, String artifactId) {
        try {
            Path pomPath = projectDir.resolve("pom.xml");
            String content = Files.readString(pomPath);
            return content.contains("<groupId>" + groupId + "</groupId>") &&
                    content.contains("<artifactId>" + artifactId + "</artifactId>");
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Check if a dependency exists in build.gradle or build.gradle.kts
     */
    private static boolean checkGradleDependency(Path projectDir, String groupId, String artifactId) {
        try {
            Path[] gradleFiles = {
                    projectDir.resolve("build.gradle"),
                    projectDir.resolve("build.gradle.kts")
            };

            for (Path gradleFile : gradleFiles) {
                if (Files.exists(gradleFile)) {
                    String content = Files.readString(gradleFile);
                    String dep = groupId + ":" + artifactId;
                    if (content.contains(dep) ||
                            content.contains("'" + dep) ||
                            content.contains("\"" + dep)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            // Ignore
        }
        return false;
    }
}
