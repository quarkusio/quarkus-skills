package com.migration.validator.model.cldk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a CRUD operation associated with a call site or callable.
 * Uses String for operationType to avoid needing an enum dependency.
 * Deserialised from Gson LOWER_CASE_WITH_UNDERSCORES JSON produced by
 * codeanalyzer-java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CRUDOperation {

    @JsonProperty("line_number")
    public int lineNumber;

    @JsonProperty("operation_type")
    public String operationType;

    @JsonProperty("target_table")
    public String targetTable;

    @JsonProperty("involved_columns")
    public List<String> involvedColumns;

    public String condition;

    @JsonProperty("joined_tables")
    public List<String> joinedTables;
}
