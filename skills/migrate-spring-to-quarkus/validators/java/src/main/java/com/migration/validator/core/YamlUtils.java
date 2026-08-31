package com.migration.validator.core;

import org.yaml.snakeyaml.Yaml;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Utility class for YAML file operations.
 * Provides methods to load and save YAML files with backup support.
 */
public class YamlUtils {
    private static final Yaml yaml = new Yaml();

    /**
     * Load YAML file and return as Map.
     * 
     * @param path Path to YAML file
     * @return Map representation of YAML content
     * @throws IOException if file cannot be read
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadYaml(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            Object loaded = yaml.load(is);
            if (loaded instanceof Map) {
                return (Map<String, Object>) loaded;
            }
            return new HashMap<>();
        }
    }

    /**
     * Save Map as YAML file with automatic backup.
     * Creates a timestamped backup if file already exists.
     * 
     * @param path Path to save YAML file
     * @param data Map to save as YAML
     * @throws IOException if file cannot be written
     */
    public static void saveYaml(Path path, Map<String, Object> data) throws IOException {
        // Create backup if file exists — stored in migration-metadata/backups/ per
        // FILE_ORGANIZATION.md
        if (Files.exists(path)) {
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path backupsDir = path.toAbsolutePath().getParent()
                    .resolve("migration-metadata/backups");
            Files.createDirectories(backupsDir);
            String backupName = path.getFileName() + ".bak." + timestamp;
            Path backupPath = backupsDir.resolve(backupName);
            Files.copy(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // Write YAML
        try (Writer writer = Files.newBufferedWriter(path)) {
            yaml.dump(data, writer);
        }
    }

    /**
     * Get nested value from Map using dot notation.
     * Example: getValue(map, "intermediate.history") returns
     * map.get("intermediate").get("history")
     * 
     * @param map  Source map
     * @param path Dot-separated path
     * @return Value at path, or null if not found
     */
    @SuppressWarnings("unchecked")
    public static Object getValue(Map<String, Object> map, String path) {
        String[] parts = path.split("\\.");
        Object current = map;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * Set nested value in Map using dot notation.
     * Creates intermediate maps as needed.
     * 
     * @param map   Target map
     * @param path  Dot-separated path
     * @param value Value to set
     */
    @SuppressWarnings("unchecked")
    public static void setValue(Map<String, Object> map, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = map;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object next = current.get(part);

            if (!(next instanceof Map)) {
                next = new HashMap<String, Object>();
                current.put(part, next);
            }

            current = (Map<String, Object>) next;
        }

        current.put(parts[parts.length - 1], value);
    }
}
