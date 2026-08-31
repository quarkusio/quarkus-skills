package com.migration.validator.model.cldk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a static or instance initialization block within a type.
 * Deserialised from Gson LOWER_CASE_WITH_UNDERSCORES JSON produced by
 * codeanalyzer-java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InitializationBlock {

    @JsonProperty("file_path")
    public String filePath;

    public List<Comment> comments;
    public List<String> annotations;

    @JsonProperty("thrown_exceptions")
    public List<String> thrownExceptions;

    public String code;

    @JsonProperty("start_line")
    public int startLine;

    @JsonProperty("end_line")
    public int endLine;

    /** Gson serialises boolean isStatic field as "is_static". */
    @JsonProperty("is_static")
    public boolean isStatic;

    @JsonProperty("referenced_types")
    public List<String> referencedTypes;

    @JsonProperty("accessed_fields")
    public List<String> accessedFields;

    @JsonProperty("call_sites")
    public List<CallSite> callSites;

    @JsonProperty("variable_declarations")
    public List<VariableDeclaration> variableDeclarations;

    @JsonProperty("cyclomatic_complexity")
    public int cyclomaticComplexity;
}
