package com.migration.validator.model.cldk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a comment extracted from Java source code.
 * Deserialised from Gson LOWER_CASE_WITH_UNDERSCORES JSON produced by
 * codeanalyzer-java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Comment {

    public String content;

    @JsonProperty("start_line")
    public int startLine;

    @JsonProperty("end_line")
    public int endLine;

    @JsonProperty("start_column")
    public int startColumn;

    @JsonProperty("end_column")
    public int endColumn;

    /** Gson serialises boolean isJavadoc field as "is_javadoc". */
    @JsonProperty("is_javadoc")
    public boolean isJavadoc;
}
