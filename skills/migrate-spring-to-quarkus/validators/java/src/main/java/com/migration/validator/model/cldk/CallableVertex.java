package com.migration.validator.model.cldk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a node (method/constructor) in the call graph.
 * Deserialised from Gson LOWER_CASE_WITH_UNDERSCORES JSON produced by
 * codeanalyzer-java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CallableVertex {

    @JsonProperty("file_path")
    public String filePath;

    @JsonProperty("type_declaration")
    public String typeDeclaration;

    public String signature;

    @JsonProperty("callable_declaration")
    public String callableDeclaration;
}
