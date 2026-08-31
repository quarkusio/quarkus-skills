package com.migration.validator.model.cldk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Represents an enum constant declaration.
 * Deserialised from Gson LOWER_CASE_WITH_UNDERSCORES JSON produced by
 * codeanalyzer-java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnumConstant {

    public String name;
    public List<String> arguments;
}
