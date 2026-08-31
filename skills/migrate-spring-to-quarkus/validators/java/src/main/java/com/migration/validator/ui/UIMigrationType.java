package com.migration.validator.ui;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Supported UI migration types for Spring to Quarkus migration.
 * Each type represents a specific view technology migration path.
 *
 * Migration Paths:
 * - JSP_QUTE: JSP views → Qute templates
 * - THYMELEAF_QUTE: Thymeleaf templates → Qute templates
 * - FREEMARKER_QUTE: FreeMarker templates → Qute templates
 * - JSF_QUTE: JSF views → Qute templates
 * - JSF_MYFACES: JSF views → Quarkus MyFaces (preserves JSF)
 */
public enum UIMigrationType {
    JSP_QUTE("jsp-qute", "JSP to Qute"),
    THYMELEAF_QUTE("thymeleaf-qute", "Thymeleaf to Qute"),
    FREEMARKER_QUTE("freemarker-qute", "FreeMarker to Qute"),
    JSF_QUTE("jsf-qute", "JSF to Qute"),
    JSF_MYFACES("jsf-myfaces", "JSF to Quarkus MyFaces");

    private final String value;
    private final String description;

    UIMigrationType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    /**
     * Get the string value used in CLI and configuration files.
     * 
     * @return The migration type value (e.g., "jsp-qute")
     */
    public String getValue() {
        return value;
    }

    /**
     * Get the human-readable description of this migration type.
     * 
     * @return The description (e.g., "JSP to Qute")
     */
    public String getDescription() {
        return description;
    }

    /**
     * Parse a string value to UIMigrationType enum constant.
     * Case-insensitive matching.
     * 
     * @param value The string value (e.g., "jsp-qute")
     * @return The corresponding enum constant
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static UIMigrationType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Migration type cannot be null");
        }

        for (UIMigrationType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException(
                "Invalid migration type: '" + value + "'. Valid types: " + getValidTypes());
    }

    /**
     * Get a comma-separated list of all valid migration type values.
     * Useful for error messages and help text.
     *
     * @return Comma-separated list (e.g., "jsp-qute, thymeleaf-qute, ...")
     */
    public static String getValidTypes() {
        return Arrays.stream(values())
                .map(UIMigrationType::getValue)
                .collect(Collectors.joining(", "));
    }

    /**
     * Check if this migration type targets Qute templates.
     * All types except JSF_MYFACES migrate to Qute.
     * 
     * @return true if this is a Qute migration, false for MyFaces
     */
    public boolean isQuteMigration() {
        return this != JSF_MYFACES;
    }

    /**
     * Check if this migration type preserves JSF (MyFaces).
     * 
     * @return true if this is JSF_MYFACES, false otherwise
     */
    public boolean isMyFacesMigration() {
        return this == JSF_MYFACES;
    }

    @Override
    public String toString() {
        return value;
    }
}
