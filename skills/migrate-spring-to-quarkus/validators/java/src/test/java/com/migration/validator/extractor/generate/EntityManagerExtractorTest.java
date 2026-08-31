package com.migration.validator.extractor.generate;

import com.migration.validator.model.PersistenceModels.EntityManagerUsageModel;
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
 * Unit tests for {@link EntityManagerExtractor}.
 *
 * All tests use synthetic {@link AnalysisResult} fixtures — no real project or
 * external tool is required.
 */
class EntityManagerExtractorTest {

    private EntityManagerExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new EntityManagerExtractor();
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

    private Field emField(String typeName, String varName, String... annotations) {
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
        AnalysisResult analysis = buildAnalysis("com.example.MyService", type);
        assertTrue(extractor.extract(analysis).isEmpty());
    }

    @Test
    void extract_typeWithNullTypeDeclarations_returnsEmpty() {
        JavaCompilationUnit cu = new JavaCompilationUnit();
        cu.filePath = "/src/Foo.java";
        cu.typeDeclarations = null;
        AnalysisResult result = new AnalysisResult();
        result.symbolTable = new LinkedHashMap<>();
        result.symbolTable.put(cu.filePath, cu);
        assertTrue(extractor.extract(result).isEmpty());
    }

    // -----------------------------------------------------------------------
    // EntityManager type detection
    // -----------------------------------------------------------------------

    @Test
    void extract_javaxPersistenceEntityManager_detected() {
        Field f = emField("javax.persistence.EntityManager", "em");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));
        assertEquals(1, extractor.extract(analysis).size());
    }

    @Test
    void extract_jakartaPersistenceEntityManager_detected() {
        Field f = emField("jakarta.persistence.EntityManager", "em");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));
        assertEquals(1, extractor.extract(analysis).size());
    }

    @Test
    void extract_bareEntityManagerType_detected() {
        // unqualified "EntityManager" — must still be detected
        Field f = emField("EntityManager", "em");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));
        assertEquals(1, extractor.extract(analysis).size());
    }

    @Test
    void extract_nonEntityManagerField_ignored() {
        Field f = emField("javax.sql.DataSource", "ds");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));
        assertTrue(extractor.extract(analysis).isEmpty());
    }

    @Test
    void extract_fieldWithNullType_ignored() {
        Field f = new Field();
        f.type = null;
        f.variables = List.of("em");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));
        assertTrue(extractor.extract(analysis).isEmpty());
    }

    @Test
    void extract_typeContainsEntityManagerButWrongPackage_ignored() {
        // "com.custom.MyEntityManagerHelper" contains "EntityManager" but is
        // neither javax/jakarta.persistence nor the bare simple name
        Field f = emField("com.custom.MyEntityManagerHelper", "helper");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));
        assertTrue(extractor.extract(analysis).isEmpty());
    }

    // -----------------------------------------------------------------------
    // Package / class name splitting
    // -----------------------------------------------------------------------

    @Test
    void extract_qualifiedClassName_splitCorrectly() {
        Field f = emField("javax.persistence.EntityManager", "em");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        EntityManagerUsageModel usage = extractor.extract(analysis).get(0);

        assertEquals("com.example", usage.getPackageName());
        assertEquals("MyBean", usage.getClassName());
    }

    @Test
    void extract_unqualifiedClassName_packageEmpty() {
        Field f = emField("javax.persistence.EntityManager", "em");
        AnalysisResult analysis = buildAnalysis("MyBean", typeWithFields(f));

        EntityManagerUsageModel usage = extractor.extract(analysis).get(0);

        assertEquals("", usage.getPackageName());
        assertEquals("MyBean", usage.getClassName());
    }

    // -----------------------------------------------------------------------
    // Field metadata
    // -----------------------------------------------------------------------

    @Test
    void extract_fieldNameTakenFromVariables() {
        Field f = emField("javax.persistence.EntityManager", "entityManager");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));
        assertEquals("entityManager", extractor.extract(analysis).get(0).getFieldName());
    }

    @Test
    void extract_nullVariables_fieldNameIsUnknown() {
        Field f = new Field();
        f.type = "javax.persistence.EntityManager";
        f.variables = null;
        f.annotations = List.of();
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));
        assertEquals("unknown", extractor.extract(analysis).get(0).getFieldName());
    }

    @Test
    void extract_emptyVariables_fieldNameIsUnknown() {
        Field f = new Field();
        f.type = "javax.persistence.EntityManager";
        f.variables = List.of();
        f.annotations = List.of();
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));
        assertEquals("unknown", extractor.extract(analysis).get(0).getFieldName());
    }

    @Test
    void extract_filePath_setOnModel() {
        Field f = emField("javax.persistence.EntityManager", "em");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));
        String filePath = extractor.extract(analysis).get(0).getOriginalFile();
        assertTrue(filePath.endsWith("MyBean.java"), "originalFile should be the compilation unit path");
    }

    // -----------------------------------------------------------------------
    // Injection annotation flags
    // -----------------------------------------------------------------------

    @Test
    void extract_persistenceContextAnnotation_setsUsesPersistenceContext() {
        Field f = emField("javax.persistence.EntityManager", "em", "@PersistenceContext");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        EntityManagerUsageModel usage = extractor.extract(analysis).get(0);

        assertTrue(usage.isUsesPersistenceContext());
        assertFalse(usage.isUsesInject());
    }

    @Test
    void extract_injectAnnotation_setsUsesInject() {
        Field f = emField("javax.persistence.EntityManager", "em", "@Inject");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        EntityManagerUsageModel usage = extractor.extract(analysis).get(0);

        assertFalse(usage.isUsesPersistenceContext());
        assertTrue(usage.isUsesInject());
    }

    @Test
    void extract_noAnnotations_allFlagsAreFalse() {
        Field f = emField("javax.persistence.EntityManager", "em");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));

        EntityManagerUsageModel usage = extractor.extract(analysis).get(0);

        assertFalse(usage.isUsesPersistenceContext());
        assertFalse(usage.isUsesInject());
        assertNull(usage.getUnitName());
    }

    // -----------------------------------------------------------------------
    // unitName extraction from @PersistenceContext
    // -----------------------------------------------------------------------

    @Test
    void extract_persistenceContextWithUnitName_extractsUnitName() {
        Field f = emField("javax.persistence.EntityManager", "em",
                "@PersistenceContext(unitName = \"myPU\")");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));
        assertEquals("myPU", extractor.extract(analysis).get(0).getUnitName());
    }

    @Test
    void extract_persistenceContextWithoutUnitName_unitNameIsNull() {
        Field f = emField("javax.persistence.EntityManager", "em", "@PersistenceContext");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));
        assertNull(extractor.extract(analysis).get(0).getUnitName());
    }

    @Test
    void extract_injectWithoutPersistenceContext_unitNameIsNull() {
        Field f = emField("javax.persistence.EntityManager", "em", "@Inject");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f));
        assertNull(extractor.extract(analysis).get(0).getUnitName());
    }

    // -----------------------------------------------------------------------
    // Multiple fields / types
    // -----------------------------------------------------------------------

    @Test
    void extract_multipleEntityManagerFields_allReturned() {
        Field f1 = emField("javax.persistence.EntityManager", "readEm",
                "@PersistenceContext(unitName = \"readPU\")");
        Field f2 = emField("jakarta.persistence.EntityManager", "writeEm",
                "@PersistenceContext(unitName = \"writePU\")");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(f1, f2));

        List<EntityManagerUsageModel> usages = extractor.extract(analysis);

        assertEquals(2, usages.size());
    }

    @Test
    void extract_mixedFields_onlyEntityManagerReturned() {
        Field em = emField("javax.persistence.EntityManager", "em", "@PersistenceContext");
        Field ds = emField("javax.sql.DataSource", "ds");
        Field str = emField("String", "name");
        AnalysisResult analysis = buildAnalysis("com.example.MyBean", typeWithFields(em, ds, str));

        List<EntityManagerUsageModel> usages = extractor.extract(analysis);

        assertEquals(1, usages.size());
        assertEquals("em", usages.get(0).getFieldName());
    }
}
