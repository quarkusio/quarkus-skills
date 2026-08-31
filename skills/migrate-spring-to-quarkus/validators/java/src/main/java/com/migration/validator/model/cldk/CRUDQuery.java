package com.migration.validator.model.cldk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a CRUD query associated with a call site or callable.
 * Uses String for queryType to avoid needing an enum dependency.
 * Deserialised from Gson LOWER_CASE_WITH_UNDERSCORES JSON produced by
 * codeanalyzer-java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CRUDQuery {

    @JsonProperty("line_number")
    public int lineNumber;

    @JsonProperty("query_arguments")
    public List<String> queryArguments;

    @JsonProperty("query_type")
    public String queryType;
}
