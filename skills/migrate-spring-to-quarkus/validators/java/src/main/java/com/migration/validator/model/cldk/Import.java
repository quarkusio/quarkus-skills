package com.migration.validator.model.cldk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an import declaration in a Java compilation unit.
 * Deserialised from Gson LOWER_CASE_WITH_UNDERSCORES JSON produced by
 * codeanalyzer-java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Import {

    public String path;

    /** Gson serialises boolean isStatic field as "is_static". */
    @JsonProperty("is_static")
    public boolean isStatic;

    /** Gson serialises boolean isWildcard field as "is_wildcard". */
    @JsonProperty("is_wildcard")
    public boolean isWildcard;
}
