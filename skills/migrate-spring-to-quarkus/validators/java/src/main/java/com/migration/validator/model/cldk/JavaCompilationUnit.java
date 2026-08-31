package com.migration.validator.model.cldk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Represents a Java compilation unit (a single .java source file) with its
 * symbol table entry.
 * Deserialised from Gson LOWER_CASE_WITH_UNDERSCORES JSON produced by
 * codeanalyzer-java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JavaCompilationUnit {

    @JsonProperty("file_path")
    public String filePath;

    @JsonProperty("package_name")
    public String packageName;

    public List<Comment> comments;
    public List<Import> imports;

    @JsonProperty("type_declarations")
    public Map<String, Type> typeDeclarations;

    /** Gson serialises boolean isModified field as "is_modified". */
    @JsonProperty("is_modified")
    public boolean isModified;
}
