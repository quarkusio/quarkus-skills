package com.migration.validator.model.cldk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a component of a Java record declaration.
 * Deserialised from Gson LOWER_CASE_WITH_UNDERSCORES JSON produced by
 * codeanalyzer-java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecordComponent {

    public Comment comment;
    public String name;
    public String type;
    public List<String> modifiers;
    public List<String> annotations;

    @JsonProperty("default_value")
    public Object defaultValue;

    /** Gson serialises boolean isVarArgs field as "is_var_args". */
    @JsonProperty("is_var_args")
    public boolean isVarArgs;
}
