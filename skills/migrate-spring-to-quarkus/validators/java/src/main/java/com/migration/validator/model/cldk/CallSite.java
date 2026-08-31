package com.migration.validator.model.cldk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a call site (method invocation) within a callable body.
 * Deserialised from Gson LOWER_CASE_WITH_UNDERSCORES JSON produced by
 * codeanalyzer-java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CallSite {

    @JsonProperty("method_name")
    public String methodName;

    public Comment comment;

    @JsonProperty("receiver_expr")
    public String receiverExpr;

    @JsonProperty("receiver_type")
    public String receiverType;

    @JsonProperty("argument_types")
    public List<String> argumentTypes;

    @JsonProperty("argument_expr")
    public List<String> argumentExpr;

    @JsonProperty("return_type")
    public String returnType;

    @JsonProperty("callee_signature")
    public String calleeSignature;

    /** Gson serialises boolean isPublic field as "is_public". */
    @JsonProperty("is_public")
    public boolean isPublic;

    /** Gson serialises boolean isProtected field as "is_protected". */
    @JsonProperty("is_protected")
    public boolean isProtected;

    /** Gson serialises boolean isPrivate field as "is_private". */
    @JsonProperty("is_private")
    public boolean isPrivate;

    /** Gson serialises boolean isUnspecified field as "is_unspecified". */
    @JsonProperty("is_unspecified")
    public boolean isUnspecified;

    /** Gson serialises boolean isStaticCall field as "is_static_call". */
    @JsonProperty("is_static_call")
    public boolean isStaticCall;

    /** Gson serialises boolean isConstructorCall field as "is_constructor_call". */
    @JsonProperty("is_constructor_call")
    public boolean isConstructorCall;

    @JsonProperty("crud_operation")
    public CRUDOperation crudOperation;

    @JsonProperty("crud_query")
    public CRUDQuery crudQuery;

    @JsonProperty("start_line")
    public int startLine;

    @JsonProperty("start_column")
    public int startColumn;

    @JsonProperty("end_line")
    public int endLine;

    @JsonProperty("end_column")
    public int endColumn;
}
