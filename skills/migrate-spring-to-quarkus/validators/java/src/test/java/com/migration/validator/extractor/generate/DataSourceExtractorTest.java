package com.migration.validator.extractor.generate;

import com.migration.validator.model.PersistenceModels.DataSourceUsageModel;
import com.migration.validator.model.cldk.AnalysisResult;
import com.migration.validator.model.cldk.Field;
import com.migration.validator.model.cldk.JavaCompilationUnit;
import com.migration.validator.model.cldk.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DataSourceExtractor}.
 *
 * All tests use synthetic {@link AnalysisResult} fixtures — no real project or
 * external tool is required.
 */
class DataSourceExtractorTest {

    private DataSourceExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new DataSourceExtractor();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private AnalysisResult buildAnalysis(String fqn, Type type) {
        JavaCompilationUnit cu = new JavaCompilationUnit();
        cu.filePath = "/src/" + fqn.replace('.', '/') + ".java";
        cu.typeDeclarations = new LinkedHashMap<>();
        cu.typeDeclarations.put(fqn, type);

        AnalysisResult result = new AnalysisResult();
        result.symbolTable = new LinkedHashMap<>();
        result.symbolTable.put(cu.filePath, cu);
        return result;
    }

    private Field dataSourceField(String typeName, String varName, String... annotations) {
        Field f = new Field();
        f.type = typeName;
        f.variables = List.of(varName);
        f.annotations = Arrays.asList(annotations);
        return f;
    }

    private Type typeWithFields(Field... fields) {
        Type t = new Type();
        t.fieldDeclarations = Arrays.asList(fields);
        return t;
    }

    // -----------------------------------------------------------------------
    // Null / empty guard cases
    // -----------------------------------------------------------------------

    @Test
    void extract_nullAnalysis_returnsEmpty() {
        assertTrue(extractor.extract(null).isEmpty());
    }

    @Test
    void extract_nullSymbolTable_returnsEmpty() {
        AnalysisResult result = new AnalysisResult();
        result.symbolTable = null;
        assertTrue(extractor.extract(result).isEmpty());
    }

    @Test
    void extract_emptySymbolTable_returnsEmpty() {
        AnalysisResult result = new AnalysisResult();
        result.symbolTable = new LinkedHashMap<>();
        assertTrue(extractor.extract(result).isEmpty());
    }

    @Test
    void extract_typeWithNullFieldDeclarations_returnsEmpty() {
        Type type = new Type(); // fieldDeclarations is null
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", type);
        assertTrue(extractor.extract(analysis).isEmpty());
    }

    @Test
    void extract_typeWithEmptyFieldDeclarations_returnsEmpty() {
        Type type = typeWithFields(); // zero fields
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", type);
        assertTrue(extractor.extract(analysis).isEmpty());
    }

    // -----------------------------------------------------------------------
    // DataSource type detection
    // -----------------------------------------------------------------------

    @Test
    void extract_javaxSqlDataSource_detected() {
        Field f = dataSourceField("javax.sql.DataSource", "ds");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        List<DataSourceUsageModel> usages = extractor.extract(analysis);

        assertEquals(1, usages.size());
    }

    @Test
    void extract_jakartaSqlDataSource_detected() {
        Field f = dataSourceField("jakarta.sql.DataSource", "ds");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        List<DataSourceUsageModel> usages = extractor.extract(analysis);

        assertEquals(1, usages.size());
    }

    @Test
    void extract_bareDataSourceType_detected() {
        // unqualified "DataSource" ends with "DataSource" — must still be detected
        Field f = dataSourceField("DataSource", "ds");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        List<DataSourceUsageModel> usages = extractor.extract(analysis);

        assertEquals(1, usages.size());
    }

    @Test
    void extract_nonDataSourceField_ignored() {
        Field f = dataSourceField("EntityManager", "em");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        assertTrue(extractor.extract(analysis).isEmpty());
    }

    @Test
    void extract_fieldWithNullType_ignored() {
        Field f = new Field();
        f.type = null;
        f.variables = List.of("ds");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        assertTrue(extractor.extract(analysis).isEmpty());
    }

    @Test
    void extract_dataSourceContainingNameButWrongPackage_ignored() {
        // "com.custom.MyDataSourceHelper" contains "DataSource" but is neither
        // javax.sql nor jakarta.sql and does NOT end with "DataSource"
        Field f = dataSourceField("com.custom.MyDataSourceHelper", "helper");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        assertTrue(extractor.extract(analysis).isEmpty());
    }

    // -----------------------------------------------------------------------
    // Package / class name splitting
    // -----------------------------------------------------------------------

    @Test
    void extract_qualifiedClassName_splitCorrectly() {
        Field f = dataSourceField("javax.sql.DataSource", "ds");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        DataSourceUsageModel usage = extractor.extract(analysis).get(0);

        assertEquals("com.example", usage.getPackageName());
        assertEquals("MyBean", usage.getClassName());
    }

    @Test
    void extract_unqualifiedClassName_packageEmpty() {
        Field f = dataSourceField("javax.sql.DataSource", "ds");
        AnalysisResult analysis = buildAnalysis("MyBean", typeWithFields(f));

        DataSourceUsageModel usage = extractor.extract(analysis).get(0);

        assertEquals("", usage.getPackageName());
        assertEquals("MyBean", usage.getClassName());
    }

    // -----------------------------------------------------------------------
    // Field metadata
    // -----------------------------------------------------------------------

    @Test
    void extract_fieldNameTakenFromVariables() {
        Field f = dataSourceField("javax.sql.DataSource", "primaryDs");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        assertEquals("primaryDs", extractor.extract(analysis).get(0).getFieldName());
    }

    @Test
    void extract_nullVariables_fieldNameIsUnknown() {
        Field f = new Field();
        f.type = "javax.sql.DataSource";
        f.variables = null;
        f.annotations = List.of();
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        assertEquals("unknown", extractor.extract(analysis).get(0).getFieldName());
    }

    @Test
    void extract_emptyVariables_fieldNameIsUnknown() {
        Field f = new Field();
        f.type = "javax.sql.DataSource";
        f.variables = List.of();
        f.annotations = List.of();
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        assertEquals("unknown", extractor.extract(analysis).get(0).getFieldName());
    }

    @Test
    void extract_filePath_setOnModel() {
        Field f = dataSourceField("javax.sql.DataSource", "ds");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        String filePath = extractor.extract(analysis).get(0).getOriginalFile();
        assertTrue(filePath.endsWith("MyBean.java"), "originalFile should be the compilation unit path");
    }

    // -----------------------------------------------------------------------
    // Injection annotation flags
    // -----------------------------------------------------------------------

    @Test
    void extract_resourceAnnotation_setsUsesResource() {
        Field f = dataSourceField("javax.sql.DataSource", "ds", "@Resource(name = \"jdbc/myDS\")");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        DataSourceUsageModel usage = extractor.extract(analysis).get(0);

        assertTrue(usage.isUsesResource());
        assertFalse(usage.isUsesInject());
        assertFalse(usage.isUsesAutowired());
    }

    @Test
    void extract_injectAnnotation_setsUsesInject() {
        Field f = dataSourceField("javax.sql.DataSource", "ds", "@Inject");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        DataSourceUsageModel usage = extractor.extract(analysis).get(0);

        assertFalse(usage.isUsesResource());
        assertTrue(usage.isUsesInject());
        assertFalse(usage.isUsesAutowired());
    }

    @Test
    void extract_autowiredAnnotation_setsUsesAutowired() {
        Field f = dataSourceField("javax.sql.DataSource", "ds", "@Autowired");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        DataSourceUsageModel usage = extractor.extract(analysis).get(0);

        assertFalse(usage.isUsesResource());
        assertFalse(usage.isUsesInject());
        assertTrue(usage.isUsesAutowired());
    }

    @Test
    void extract_noAnnotations_allFlagsAreFalse() {
        Field f = dataSourceField("javax.sql.DataSource", "ds");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        DataSourceUsageModel usage = extractor.extract(analysis).get(0);

        assertFalse(usage.isUsesResource());
        assertFalse(usage.isUsesInject());
        assertFalse(usage.isUsesAutowired());
        assertNull(usage.getJndiName());
    }

    // -----------------------------------------------------------------------
    // JNDI name extraction from @Resource
    // -----------------------------------------------------------------------

    @Test
    void extract_resourceWithName_extractsJndiName() {
        Field f = dataSourceField("javax.sql.DataSource", "ds", "@Resource(name = \"jdbc/myDS\")");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        assertEquals("jdbc/myDS", extractor.extract(analysis).get(0).getJndiName());
    }

    @Test
    void extract_resourceWithLookup_fallsBackToLookup() {
        Field f = dataSourceField("javax.sql.DataSource", "ds", "@Resource(lookup = \"jdbc/lookupDS\")");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        assertEquals("jdbc/lookupDS", extractor.extract(analysis).get(0).getJndiName());
    }

    @Test
    void extract_resourceWithoutNameOrLookup_jndiNameIsNull() {
        Field f = dataSourceField("javax.sql.DataSource", "ds", "@Resource");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        DataSourceUsageModel usage = extractor.extract(analysis).get(0);
        assertTrue(usage.isUsesResource());
        assertNull(usage.getJndiName());
    }

    @Test
    void extract_noResource_jndiNameIsNull() {
        Field f = dataSourceField("javax.sql.DataSource", "ds", "@Inject");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        assertNull(extractor.extract(analysis).get(0).getJndiName());
    }

    // -----------------------------------------------------------------------
    // Multiple fields / types
    // -----------------------------------------------------------------------

    @Test
    void extract_multipleDataSourceFields_allReturned() {
        Field f1 = dataSourceField("javax.sql.DataSource", "readDs", "@Resource(name = \"jdbc/readDS\")");
        Field f2 = dataSourceField("javax.sql.DataSource", "writeDs", "@Resource(name = \"jdbc/writeDS\")");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f1, f2));

        List<DataSourceUsageModel> usages = extractor.extract(analysis);

        assertEquals(2, usages.size());
    }

    @Test
    void extract_mixedFields_onlyDataSourceReturned() {
        Field ds = dataSourceField("javax.sql.DataSource", "ds", "@Resource");
        Field em = dataSourceField("EntityManager", "em");
        Field str = dataSourceField("String", "name");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(ds, em, str));

        List<DataSourceUsageModel> usages = extractor.extract(analysis);

        assertEquals(1, usages.size());
        assertEquals("ds", usages.get(0).getFieldName());
    }
}
