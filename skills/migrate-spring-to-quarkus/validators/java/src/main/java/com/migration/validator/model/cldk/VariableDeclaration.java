package com.migration.validator.model.cldk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a variable declaration in a method body.
 * Deserialised from Gson LOWER_CASE_WITH_UNDERSCORES JSON produced by
 * codeanalyzer-java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VariableDeclaration {

    public Comment comment;
    public String name;
    public String type;
    public String initializer;

    @JsonProperty("start_line")
    public int startLine;

    @JsonProperty("start_column")
    public int startColumn;

    @JsonProperty("end_line")
    public int endLine;

    @JsonProperty("end_column")
    public int endColumn;
}
