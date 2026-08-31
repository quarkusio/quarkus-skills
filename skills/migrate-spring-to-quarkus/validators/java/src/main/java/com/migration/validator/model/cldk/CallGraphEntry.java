package com.migration.validator.model.cldk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an edge in the call graph - a flat superset of CallDependency and
 * SDGDependency.
 * Fields sourceKind and destinationKind are only present for SDGDependency
 * edges.
 * Uses @JsonIgnoreProperties so it can deserialise both edge subtypes without
 * error.
 *
 * Deserialised from Gson LOWER_CASE_WITH_UNDERSCORES JSON produced by
 * codeanalyzer-java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CallGraphEntry {

    public CallableVertex source;
    public CallableVertex target;

    /**
     * Edge type: "CALL_DEP" for call-graph edges, or an SDG dependency type string.
     */
    public String type;

    public String weight;

    @JsonProperty("source_kind")
    public String sourceKind;

    @JsonProperty("destination_kind")
    public String destinationKind;
}
