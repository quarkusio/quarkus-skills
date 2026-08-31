package com.migration.validator.model.cldk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Represents a field declaration within a type.
 * Deserialised from Gson LOWER_CASE_WITH_UNDERSCORES JSON produced by
 * codeanalyzer-java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Field {

    public Comment comment;
    public String name;
    public String type;

    @JsonProperty("start_line")
    public Integer startLine;

    @JsonProperty("end_line")
    public Integer endLine;

    public List<String> variables;
    public List<String> modifiers;
    public List<String> annotations;

    @JsonProperty("variable_initializers")
    public Map<String, String> variableInitializers;
}
