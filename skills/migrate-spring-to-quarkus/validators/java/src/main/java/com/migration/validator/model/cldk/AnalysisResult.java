package com.migration.validator.model.cldk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Top-level model for the analysis.json output produced by codeanalyzer-java.
 *
 * Structure:
 * 
 * <pre>
 * {
 *   "symbol_table": { "&lt;file_path&gt;": { ... JavaCompilationUnit ... }, ... },
 *   "call_graph":   [ { ... CallGraphEntry ... }, ... ],   // present only at analysis level 2
 *   "version":      "x.y.z"
 * }
 * </pre>
 *
 * Deserialised from Gson LOWER_CASE_WITH_UNDERSCORES JSON produced by
 * codeanalyzer-java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisResult {

    @JsonProperty("symbol_table")
    public Map<String, JavaCompilationUnit> symbolTable;

    @JsonProperty("call_graph")
    public List<CallGraphEntry> callGraph;

    public String version;
}
