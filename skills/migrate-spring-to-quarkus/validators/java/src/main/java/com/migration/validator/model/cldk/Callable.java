package com.migration.validator.model.cldk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a callable entity (method or constructor) in a Java type.
 * Deserialised from Gson LOWER_CASE_WITH_UNDERSCORES JSON produced by
 * codeanalyzer-java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Callable {

    @JsonProperty("file_path")
    public String filePath;

    public String signature;
    public List<Comment> comments;
    public List<String> annotations;
    public List<String> modifiers;

    @JsonProperty("thrown_exceptions")
    public List<String> thrownExceptions;

    public String declaration;
    public List<ParameterInCallable> parameters;
    public String code;

    @JsonProperty("start_line")
    public int startLine;

    @JsonProperty("end_line")
    public int endLine;

    @JsonProperty("code_start_line")
    public int codeStartLine;

    @JsonProperty("return_type")
    public String returnType;

    /** Gson serialises boolean isImplicit field as "is_implicit". */
    @JsonProperty("is_implicit")
    public boolean isImplicit;

    /** Gson serialises boolean isConstructor field as "is_constructor". */
    @JsonProperty("is_constructor")
    public boolean isConstructor;

    @JsonProperty("referenced_types")
    public List<String> referencedTypes;

    @JsonProperty("accessed_fields")
    public List<String> accessedFields;

    @JsonProperty("call_sites")
    public List<CallSite> callSites;

    @JsonProperty("variable_declarations")
    public List<VariableDeclaration> variableDeclarations;

    @JsonProperty("crud_operations")
    public List<CRUDOperation> crudOperations;

    @JsonProperty("crud_queries")
    public List<CRUDQuery> crudQueries;

    @JsonProperty("cyclomatic_complexity")
    public int cyclomaticComplexity;

    /** Gson serialises boolean isEntrypoint field as "is_entrypoint". */
    @JsonProperty("is_entrypoint")
    public boolean isEntrypoint;
}
