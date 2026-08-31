package com.migration.validator.model.cldk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a parameter in a callable (method or constructor).
 * Deserialised from Gson LOWER_CASE_WITH_UNDERSCORES JSON produced by
 * codeanalyzer-java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParameterInCallable {

    public String type;
    public String name;
    public List<String> annotations;
    public List<String> modifiers;

    @JsonProperty("start_line")
    public int startLine;

    @JsonProperty("end_line")
    public int endLine;

    @JsonProperty("start_column")
    public int startColumn;

    @JsonProperty("end_column")
    public int endColumn;
}
