package com.migration.validator.core;

import com.migration.validator.model.PersistenceModels.PersistenceConfigModel;
import com.migration.validator.model.PersistenceModels.PersistenceProperty;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Utility class for extracting configuration properties from .properties and
 * .yaml files.
 */
public class PropertyUtils {

    // Keys used to find Quarkus root REST path
    private static final List<String> QUARKUS_REST_PATH_KEYS = Arrays.asList(
            "quarkus.rest.path", "quarkus.http.root-path");

    // Keys used to find Spring servlet context path
    private static final List<String> SPRING_SERVLET_PATH_KEYS = Arrays.asList(
            "server.servlet.context-path", "server.servlet.contextPath");

    // Datasource property keys to extract (Quarkus + Spring)
    private static final Set<String> DATASOURCE_URL_KEYS = new HashSet<>(Arrays.asList(
            "quarkus.datasource.jdbc.url", "quarkus.datasource.url",
            "spring.datasource.url", "spring.datasource.jdbc-url"));
    private static final Set<String> DATASOURCE_DRIVER_KEYS = new HashSet<>(Arrays.asList(
            "quarkus.datasource.jdbc.driver", "quarkus.datasource.driver",
            "spring.datasource.driver-class-name"));
    private static final Set<String> DATASOURCE_USERNAME_KEYS = new HashSet<>(Arrays.asList(
            "quarkus.datasource.username", "spring.datasource.username"));
    private static final Set<String> HIBERNATE_DIALECT_KEYS = new HashSet<>(Arrays.asList(
            "quarkus.hibernate-orm.dialect", "quarkus.hibernate.dialect",
            "hibernate.dialect", "spring.jpa.database-platform",
            "spring.jpa.properties.hibernate.dialect"));
    private static final Set<String> HIBERNATE_DDL_AUTO_KEYS = new HashSet<>(Arrays.asList(
            "quarkus.hibernate-orm.database.generation", "spring.jpa.hibernate.ddl-auto",
            "spring.jpa.properties.hibernate.hbm2ddl.auto"));
    private static final Set<String> HIBERNATE_SHOW_SQL_KEYS = new HashSet<>(Arrays.asList(
            "quarkus.hibernate-orm.log.sql", "spring.jpa.show-sql",
            "spring.jpa.properties.hibernate.show_sql"));

    /**
     * Extract a property value from a .properties file by key name.
     * Supports profile-prefixed keys like %dev.some.key = value.
     */
    public static Optional<String> extractFromProperties(Path file, String propertyName) {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            for (String line : Files.readAllLines(file)) {
                // Handle both plain and profile-prefixed keys: %profile.key = value
                String stripped = line.trim();
                if (stripped.isEmpty() || stripped.startsWith("#") || stripped.startsWith("!")) {
                    continue;
                }
                int eqIdx = stripped.indexOf('=');
                int colonIdx = stripped.indexOf(':');
                int sepIdx = eqIdx >= 0 ? (colonIdx >= 0 ? Math.min(eqIdx, colonIdx) : eqIdx) : colonIdx;
                if (sepIdx < 0)
                    continue;
                String key = stripped.substring(0, sepIdx).trim();
                String value = stripped.substring(sepIdx + 1).trim();

                // Normalize: strip profile prefix (%dev., etc.)
                String normalizedKey = key.startsWith("%") ? key.replaceFirst("^%[^.]+\\.", "") : key;
                if (normalizedKey.equals(propertyName)) {
                    return Optional.of(value);
                }
            }
        } catch (IOException e) {
            System.err.println("[WARN] Skipping " + file + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Flatten a nested YAML map to dotted-key entries.
     * Lists are joined with commas.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> flattenYamlProperties(Object data, String prefix) {
        Map<String, String> result = new HashMap<>();
        if (data instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) data;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String nextPrefix = prefix.isEmpty()
                        ? String.valueOf(entry.getKey())
                        : prefix + "." + entry.getKey();
                result.putAll(flattenYamlProperties(entry.getValue(), nextPrefix));
            }
        } else if (data instanceof List) {
            if (!prefix.isEmpty()) {
                List<?> list = (List<?>) data;
                StringBuilder sb = new StringBuilder();
                for (Object item : list) {
                    if (sb.length() > 0)
                        sb.append(",");
                    sb.append(item == null ? "" : item.toString());
                }
                result.put(prefix, sb.toString());
            }
        } else {
            if (!prefix.isEmpty()) {
                result.put(prefix, data == null ? "" : data.toString());
            }
        }
        return result;
    }

    /**
     * Extract a property value from a YAML file by dotted key name.
     */
    public static Optional<String> extractFromYaml(Path file, String propertyName) {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            Yaml yaml = new Yaml();
            Object content = yaml.load(Files.newInputStream(file));
            if (content == null) {
                return Optional.empty();
            }
            Map<String, String> flat = flattenYamlProperties(content, "");
            // Try exact match first, then suffix match (for property_name endings)
            String val = flat.get(propertyName);
            if (val != null) {
                return Optional.of(val);
            }
            for (Map.Entry<String, String> entry : flat.entrySet()) {
                if (entry.getKey().endsWith(propertyName)) {
                    return Optional.of(entry.getValue());
                }
            }
        } catch (Exception e) {
            System.err.println("[WARN] Skipping " + file + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Walk a project tree and find the Quarkus REST root path from application
     * .properties or .yml/.yaml files.
     */
    public static Optional<String> findQuarkusRestPath(Path projectRoot) {
        return walkForProperty(projectRoot, QUARKUS_REST_PATH_KEYS);
    }

    /**
     * Walk a project tree and find the Spring servlet context path from application
     * .properties or .yml/.yaml files.
     */
    public static Optional<String> findSpringRestPath(Path projectRoot) {
        return walkForProperty(projectRoot, SPRING_SERVLET_PATH_KEYS);
    }

    private static Optional<String> walkForProperty(Path projectRoot, List<String> keys) {
        try (Stream<Path> stream = Files.walk(projectRoot)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            for (Path file : files) {
                String name = file.getFileName().toString();
                Optional<String> value = Optional.empty();
                if (name.endsWith(".properties")) {
                    for (String key : keys) {
                        value = extractFromProperties(file, key);
                        if (value.isPresent())
                            break;
                    }
                } else if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                    for (String key : keys) {
                        value = extractFromYaml(file, key);
                        if (value.isPresent())
                            break;
                    }
                }
                if (value.isPresent()) {
                    return value;
                }
            }
        } catch (IOException e) {
            System.err.println("[WARN] Error walking project for property: " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Extract application persistence configuration from src/main/resources/.
     * Reads application.properties, application.yml, application.yaml in that
     * order.
     * Returns the first file that has any matching properties.
     */
    public static PersistenceConfigModel extractApplicationProperties(Path projectRoot) {
        Path resourcesDir = projectRoot.resolve("src").resolve("main").resolve("resources");
        Path propsPath = resourcesDir.resolve("application.properties");
        Path ymlPath = resourcesDir.resolve("application.yml");
        Path yamlPath = resourcesDir.resolve("application.yaml");

        PersistenceConfigModel propsConfig = extractFromPropertiesFile(propsPath);
        if (propsConfig != null && !propsConfig.getProperties().isEmpty()) {
            return propsConfig;
        }

        PersistenceConfigModel ymlConfig = extractFromYamlFile(ymlPath);
        if (ymlConfig != null && !ymlConfig.getProperties().isEmpty()) {
            return ymlConfig;
        }

        PersistenceConfigModel yamlConfig = extractFromYamlFile(yamlPath);
        if (yamlConfig != null && !yamlConfig.getProperties().isEmpty()) {
            return yamlConfig;
        }

        PersistenceConfigModel fallback = propsConfig != null ? propsConfig
                : (ymlConfig != null ? ymlConfig : (yamlConfig != null ? yamlConfig : newEmptyConfig()));
        return fallback;
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private static PersistenceConfigModel extractFromPropertiesFile(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        List<PersistenceProperty> properties = new ArrayList<>();
        Map<String, String> extracted = newExtractedMap();
        Map<String, String> fallback = new HashMap<>();

        try {
            for (String line : Files.readAllLines(path)) {
                String stripped = line.trim();
                if (stripped.isEmpty() || stripped.startsWith("#") || stripped.startsWith("!")) {
                    continue;
                }
                int sepIdx = stripped.indexOf('=');
                int colonIdx = stripped.indexOf(':');
                if (sepIdx < 0 && colonIdx < 0)
                    continue;
                if (sepIdx < 0)
                    sepIdx = colonIdx;
                else if (colonIdx >= 0)
                    sepIdx = Math.min(sepIdx, colonIdx);

                String key = stripped.substring(0, sepIdx).trim();
                String value = stripped.substring(sepIdx + 1).trim();
                applyPersistenceProperty(key, value, properties, extracted, fallback);
            }
        } catch (IOException e) {
            System.err.println("[WARN] Failed to parse application.properties: " + e.getMessage());
            return newEmptyConfig();
        }

        // Apply fallback values where primary values are null
        for (Map.Entry<String, String> fb : fallback.entrySet()) {
            if (extracted.get(fb.getKey()) == null) {
                extracted.put(fb.getKey(), fb.getValue());
            }
        }
        return buildConfig(false, properties, extracted);
    }

    private static PersistenceConfigModel extractFromYamlFile(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            Yaml yaml = new Yaml();
            Object raw = yaml.load(Files.newInputStream(path));
            if (raw == null) {
                return newEmptyConfig();
            }
            Map<String, String> flat = flattenYamlProperties(raw, "");
            List<PersistenceProperty> properties = new ArrayList<>();
            Map<String, String> extracted = newExtractedMap();
            Map<String, String> fallback = new HashMap<>();
            for (Map.Entry<String, String> entry : flat.entrySet()) {
                applyPersistenceProperty(entry.getKey(), entry.getValue(), properties, extracted, fallback);
            }
            for (Map.Entry<String, String> fb : fallback.entrySet()) {
                if (extracted.get(fb.getKey()) == null) {
                    extracted.put(fb.getKey(), fb.getValue());
                }
            }
            return buildConfig(false, properties, extracted);
        } catch (Exception e) {
            System.err.println("[WARN] Failed to parse " + path.getFileName() + ": " + e.getMessage());
            return newEmptyConfig();
        }
    }

    /**
     * Apply a single property key+value to the extraction maps.
     * Mirrors Python _apply_persistence_property().
     */
    private static void applyPersistenceProperty(
            String key, String value,
            List<PersistenceProperty> properties,
            Map<String, String> extracted,
            Map<String, String> fallback) {

        PersistenceProperty p = new PersistenceProperty();
        p.setKey(key);
        p.setValue(value);
        properties.add(p);

        // Resolve profile-prefixed fallback key: %dev.some.key -> some.key
        String fallbackKey = null;
        if (key.startsWith("%")) {
            int dot = key.indexOf('.');
            if (dot > 0 && dot < key.length() - 1) {
                fallbackKey = key.substring(dot + 1);
            }
        }

        // Helper: set extracted or fallback map based on priority
        setIfMatches("datasource_url", DATASOURCE_URL_KEYS, key, value, fallbackKey, extracted, fallback);
        setIfMatches("datasource_driver", DATASOURCE_DRIVER_KEYS, key, value, fallbackKey, extracted, fallback);
        setIfMatches("datasource_username", DATASOURCE_USERNAME_KEYS, key, value, fallbackKey, extracted, fallback);
        setIfMatches("hibernate_dialect", HIBERNATE_DIALECT_KEYS, key, value, fallbackKey, extracted, fallback);
        setIfMatches("hibernate_ddl_auto", HIBERNATE_DDL_AUTO_KEYS, key, value, fallbackKey, extracted, fallback);
        setIfMatches("hibernate_show_sql", HIBERNATE_SHOW_SQL_KEYS, key, value, fallbackKey, extracted, fallback);
    }

    private static void setIfMatches(
            String fieldName, Set<String> candidates,
            String key, String value, String fallbackKey,
            Map<String, String> extracted, Map<String, String> fallback) {
        if (candidates.contains(key)) {
            extracted.put(fieldName, value);
        } else if (fallbackKey != null && candidates.contains(fallbackKey)
                && extracted.get(fieldName) == null
                && !fallback.containsKey(fieldName)) {
            fallback.put(fieldName, value);
        }
    }

    private static Map<String, String> newExtractedMap() {
        Map<String, String> m = new HashMap<>();
        m.put("datasource_url", null);
        m.put("datasource_driver", null);
        m.put("datasource_username", null);
        m.put("hibernate_dialect", null);
        m.put("hibernate_ddl_auto", null);
        m.put("hibernate_show_sql", null);
        return m;
    }

    private static PersistenceConfigModel buildConfig(boolean xmlExists,
            List<PersistenceProperty> properties, Map<String, String> extracted) {
        PersistenceConfigModel cfg = new PersistenceConfigModel();
        cfg.setPersistenceXmlExists(xmlExists);
        cfg.setProperties(properties);
        cfg.setDatasourceUrl(extracted.get("datasource_url"));
        cfg.setDatasourceDriver(extracted.get("datasource_driver"));
        cfg.setDatasourceUsername(extracted.get("datasource_username"));
        cfg.setHibernateDialect(extracted.get("hibernate_dialect"));
        cfg.setHibernateDdlAuto(extracted.get("hibernate_ddl_auto"));
        String showSqlStr = extracted.get("hibernate_show_sql");
        if (showSqlStr != null) {
            cfg.setHibernateShowSql("true".equalsIgnoreCase(showSqlStr));
        }
        return cfg;
    }

    private static PersistenceConfigModel newEmptyConfig() {
        PersistenceConfigModel cfg = new PersistenceConfigModel();
        cfg.setPersistenceXmlExists(false);
        cfg.setProperties(new ArrayList<>());
        return cfg;
    }
}
