package com.migration.validator.ui;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * FileUtils - Common file operations for UI validators
 */
public class FileUtils {

    /**
     * Find all files with given extension in directory (recursive)
     */
    public static List<Path> findFilesByExtension(Path baseDir, String extension) throws IOException {
        if (!Files.exists(baseDir)) {
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.walk(baseDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(extension))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Remove Java comments from content (single-line and multi-line)
     */
    public static String removeComments(String content) {
        // Remove single-line comments
        content = content.replaceAll("//.*?$", "");
        // Remove multi-line comments
        content = content.replaceAll("/\\*.*?\\*/", "");
        return content;
    }
}
