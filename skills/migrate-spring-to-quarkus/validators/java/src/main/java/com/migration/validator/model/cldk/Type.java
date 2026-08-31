package com.migration.validator.model.cldk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Represents a type declaration (class, interface, enum, annotation, or record)
 * in a compilation unit.
 * Deserialised from Gson LOWER_CASE_WITH_UNDERSCORES JSON produced by
 * codeanalyzer-java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Type {

    /** Gson serialises boolean isNestedType field as "is_nested_type". */
    @JsonProperty("is_nested_type")
    public boolean isNestedType;

    /**
     * Gson serialises boolean isClassOrInterfaceDeclaration field as
     * "is_class_or_interface_declaration".
     */
    @JsonProperty("is_class_or_interface_declaration")
    public boolean isClassOrInterfaceDeclaration;

    /** Gson serialises boolean isEnumDeclaration field as "is_enum_declaration". */
    @JsonProperty("is_enum_declaration")
    public boolean isEnumDeclaration;

    /**
     * Gson serialises boolean isAnnotationDeclaration field as
     * "is_annotation_declaration".
     */
    @JsonProperty("is_annotation_declaration")
    public boolean isAnnotationDeclaration;

    /**
     * Gson serialises boolean isRecordDeclaration field as "is_record_declaration".
     */
    @JsonProperty("is_record_declaration")
    public boolean isRecordDeclaration;

    /** Gson serialises boolean isInterface field as "is_interface". */
    @JsonProperty("is_interface")
    public boolean isInterface;

    /** Gson serialises boolean isInnerClass field as "is_inner_class". */
    @JsonProperty("is_inner_class")
    public boolean isInnerClass;

    /** Gson serialises boolean isLocalClass field as "is_local_class". */
    @JsonProperty("is_local_class")
    public boolean isLocalClass;

    @JsonProperty("extends_list")
    public List<String> extendsList;

    public List<Comment> comments;

    @JsonProperty("implements_list")
    public List<String> implementsList;

    public List<String> modifiers;
    public List<String> annotations;

    @JsonProperty("parent_type")
    public String parentType;

    @JsonProperty("nested_type_declarations")
    public List<String> nestedTypeDeclarations;

    @JsonProperty("callable_declarations")
    public Map<String, Callable> callableDeclarations;

    @JsonProperty("field_declarations")
    public List<Field> fieldDeclarations;

    @JsonProperty("enum_constants")
    public List<EnumConstant> enumConstants;

    @JsonProperty("record_components")
    public List<RecordComponent> recordComponents;

    @JsonProperty("initialization_blocks")
    public List<InitializationBlock> initializationBlocks;

    /** Gson serialises boolean isEntrypointClass field as "is_entrypoint_class". */
    @JsonProperty("is_entrypoint_class")
    public boolean isEntrypointClass;
}
