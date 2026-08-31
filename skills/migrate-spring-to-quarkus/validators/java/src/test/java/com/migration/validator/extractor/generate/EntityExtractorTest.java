package com.migration.validator.extractor.generate;

import com.migration.validator.model.PersistenceModels.*;
import com.migration.validator.model.cldk.AnalysisResult;
import com.migration.validator.model.cldk.Field;
import com.migration.validator.model.cldk.JavaCompilationUnit;
import com.migration.validator.model.cldk.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EntityExtractor}.
 *
 * All tests use synthetic {@link AnalysisResult} fixtures — no real project or
 * external tool is required.
 */
class EntityExtractorTest {

    private EntityExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new EntityExtractor();
    }

    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

    /** Wraps a single type inside an AnalysisResult. */
    private AnalysisResult analysis(String fqn, Type type) {
        JavaCompilationUnit cu = new JavaCompilationUnit();
        cu.filePath = "/src/" + fqn.replace('.', '/') + ".java";
        cu.typeDeclarations = new LinkedHashMap<>();
        cu.typeDeclarations.put(fqn, type);

        AnalysisResult result = new AnalysisResult();
        result.symbolTable = new LinkedHashMap<>();
        result.symbolTable.put(cu.filePath, cu);
        return result;
    }

    private Type entityType(String... annotations) {
        Type t = new Type();
        t.annotations = Arrays.asList(annotations);
        return t;
    }

    private Field field(String type, String varName, String... annotations) {
        Field f = new Field();
        f.type = type;
        f.variables = List.of(varName);
        f.annotations = Arrays.asList(annotations);
        return f;
    }

    // -----------------------------------------------------------------------
    // Null / empty guard cases
    // -----------------------------------------------------------------------

    @Test
    void extract_nullAnalysis_returnsEmpty() {
        assertTrue(extractor.extract(null, Map.of()).isEmpty());
    }

    @Test
    void extract_nullSymbolTable_returnsEmpty() {
        AnalysisResult result = new AnalysisResult();
        result.symbolTable = null;
        assertTrue(extractor.extract(result, Map.of()).isEmpty());
    }

    @Test
    void extract_emptySymbolTable_returnsEmpty() {
        AnalysisResult result = new AnalysisResult();
        result.symbolTable = new LinkedHashMap<>();
        assertTrue(extractor.extract(result, Map.of()).isEmpty());
    }

    @Test
    void extract_cuWithNullTypeDeclarations_returnsEmpty() {
        JavaCompilationUnit cu = new JavaCompilationUnit();
        cu.typeDeclarations = null;
        AnalysisResult result = new AnalysisResult();
        result.symbolTable = new LinkedHashMap<>();
        result.symbolTable.put("/src/Foo.java", cu);
        assertTrue(extractor.extract(result, Map.of()).isEmpty());
    }

    // -----------------------------------------------------------------------
    // @Entity / non-@Entity filtering
    // -----------------------------------------------------------------------

    @Test
    void extract_entityAnnotation_included() {
        Type t = entityType("@Entity");
        List<EntityModel> entities = extractor.extract(analysis("com.example.Order", t), Map.of());
        assertEquals(1, entities.size());
    }

    @Test
    void extract_noEntityAnnotation_excluded() {
        Type t = entityType("@Service");
        assertTrue(extractor.extract(analysis("com.example.MyService", t), Map.of()).isEmpty());
    }

    @Test
    void extract_typeWithNullAnnotations_excluded() {
        Type t = new Type(); // annotations is null
        assertTrue(extractor.extract(analysis("com.example.MyClass", t), Map.of()).isEmpty());
    }

    @Test
    void extract_embeddableAnnotation_excluded() {
        // isEntityAnnotation() only matches @Entity, not @Embeddable
        Type t = entityType("@Embeddable");
        assertTrue(extractor.extract(analysis("com.example.Address", t), Map.of()).isEmpty());
    }

    // -----------------------------------------------------------------------
    // Class metadata — package, class name, file path
    // -----------------------------------------------------------------------

    @Test
    void extract_qualifiedClassName_splitCorrectly() {
        Type t = entityType("@Entity");
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);
        assertEquals("com.example", e.getPackageName());
        assertEquals("Order", e.getClassName());
    }

    @Test
    void extract_unqualifiedClassName_emptyPackage() {
        Type t = entityType("@Entity");
        EntityModel e = extractor.extract(analysis("Order", t), Map.of()).get(0);
        assertEquals("", e.getPackageName());
        assertEquals("Order", e.getClassName());
    }

    @Test
    void extract_filePath_setFromCompilationUnit() {
        Type t = entityType("@Entity");
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);
        assertTrue(e.getOriginalFile().endsWith("Order.java"));
    }

    @Test
    void extract_extendsAndImplements_propagated() {
        Type t = entityType("@Entity");
        t.extendsList = List.of("BaseEntity");
        t.implementsList = List.of("Serializable");
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);
        assertEquals(List.of("BaseEntity"), e.getExtendsClasses());
        assertEquals(List.of("Serializable"), e.getImplementsInterfaces());
    }

    @Test
    void extract_nullExtendsAndImplements_defaultToEmpty() {
        Type t = entityType("@Entity"); // extendsList / implementsList null
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);
        assertNotNull(e.getExtendsClasses());
        assertNotNull(e.getImplementsInterfaces());
        assertTrue(e.getExtendsClasses().isEmpty());
        assertTrue(e.getImplementsInterfaces().isEmpty());
    }

    // -----------------------------------------------------------------------
    // @Table annotation
    // -----------------------------------------------------------------------

    @Test
    void extract_tableAnnotationWithName_setsTableName() {
        Type t = entityType("@Entity", "@Table(name = \"orders\")");
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);
        assertEquals("orders", e.getTableName());
    }

    @Test
    void extract_noTableAnnotation_tableNameNull() {
        Type t = entityType("@Entity");
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);
        assertNull(e.getTableName());
    }

    // -----------------------------------------------------------------------
    // @Inheritance annotation
    // -----------------------------------------------------------------------

    @Test
    void extract_inheritanceStrategy_simpleNameExtracted() {
        Type t = entityType("@Entity", "@Inheritance(strategy = InheritanceType.SINGLE_TABLE)");
        EntityModel e = extractor.extract(analysis("com.example.Vehicle", t), Map.of()).get(0);
        assertEquals("SINGLE_TABLE", e.getInheritanceStrategy());
    }

    @Test
    void extract_inheritanceStrategyAlreadySimple_returnedAsIs() {
        Type t = entityType("@Entity", "@Inheritance(strategy = JOINED)");
        EntityModel e = extractor.extract(analysis("com.example.Vehicle", t), Map.of()).get(0);
        assertEquals("JOINED", e.getInheritanceStrategy());
    }

    @Test
    void extract_noInheritanceAnnotation_strategyNull() {
        Type t = entityType("@Entity");
        EntityModel e = extractor.extract(analysis("com.example.Vehicle", t), Map.of()).get(0);
        assertNull(e.getInheritanceStrategy());
    }

    // -----------------------------------------------------------------------
    // @NamedQuery annotation
    // -----------------------------------------------------------------------

    @Test
    void extract_namedQuery_parsedCorrectly() {
        String ann = "@NamedQuery(name = \"Order.findAll\", query = \"SELECT o FROM Order o\")";
        Type t = entityType("@Entity", ann);
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);

        assertEquals(1, e.getNamedQueries().size());
        NamedQueryModel nq = e.getNamedQueries().get(0);
        assertEquals("Order.findAll", nq.getName());
        assertEquals("SELECT o FROM Order o", nq.getQuery());
        assertEquals("SELECT", nq.getType());
        assertFalse(nq.isModifying());
    }

    @Test
    void extract_namedQueryDelete_isModifyingTrue() {
        String ann = "@NamedQuery(name = \"Order.deleteOld\", query = \"DELETE FROM Order o WHERE o.year < 2000\")";
        Type t = entityType("@Entity", ann);
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);

        assertTrue(e.getNamedQueries().get(0).isModifying());
    }

    @Test
    void extract_noNamedQuery_listIsEmpty() {
        Type t = entityType("@Entity");
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);
        assertTrue(e.getNamedQueries().isEmpty());
    }

    // -----------------------------------------------------------------------
    // Basic field extraction
    // -----------------------------------------------------------------------

    @Test
    void extract_basicField_addedToFields() {
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(field("String", "name"));
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);

        assertEquals(1, e.getFields().size());
        FieldModel fm = e.getFields().get(0);
        assertEquals("name", fm.getName());
        assertEquals("String", fm.getType());
    }

    @Test
    void extract_fieldWithNullVariables_skipped() {
        Field f = new Field();
        f.type = "String";
        f.variables = null;
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);
        assertTrue(e.getFields().isEmpty());
    }

    @Test
    void extract_fieldWithEmptyVariables_skipped() {
        Field f = new Field();
        f.type = "String";
        f.variables = List.of();
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);
        assertTrue(e.getFields().isEmpty());
    }

    @Test
    void extract_nullFieldDeclarations_noFieldsAdded() {
        Type t = entityType("@Entity"); // fieldDeclarations null
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);
        assertTrue(e.getFields().isEmpty());
    }

    // -----------------------------------------------------------------------
    // @Column field attributes
    // -----------------------------------------------------------------------

    @Test
    void extract_columnName_setOnField() {
        Field f = field("String", "name", "@Column(name = \"order_name\")");
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);
        assertEquals("order_name", e.getFields().get(0).getColumn());
    }

    @Test
    void extract_columnNullableTrue_setsNullable() {
        Field f = field("String", "notes", "@Column(nullable = true)");
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        FieldModel fm = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0).getFields().get(0);
        assertEquals(Boolean.TRUE, fm.getNullable());
    }

    @Test
    void extract_columnNullableFalse_setsNullable() {
        Field f = field("String", "code", "@Column(nullable = false)");
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        FieldModel fm = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0).getFields().get(0);
        assertEquals(Boolean.FALSE, fm.getNullable());
    }

    @Test
    void extract_noColumnAnnotation_nullableAndUniqueNull() {
        Field f = field("String", "name");
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        FieldModel fm = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0).getFields().get(0);
        assertNull(fm.getNullable());
        assertNull(fm.getUnique());
    }

    // -----------------------------------------------------------------------
    // @Transient
    // -----------------------------------------------------------------------

    @Test
    void extract_transientAnnotation_setsTransientTrue() {
        Field f = field("String", "temp", "@Transient");
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        FieldModel fm = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0).getFields().get(0);
        assertTrue(fm.isTransientField());
    }

    @Test
    void extract_noTransientAnnotation_transientFalse() {
        Field f = field("String", "name");
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        FieldModel fm = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0).getFields().get(0);
        assertFalse(fm.isTransientField());
    }

    // -----------------------------------------------------------------------
    // @Id / @GeneratedValue
    // -----------------------------------------------------------------------

    @Test
    void extract_idField_setsIdFieldAndIdGeneration() {
        Field f = field("Long", "id",
                "@Id",
                "@GeneratedValue(strategy = GenerationType.IDENTITY)");
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);

        assertNotNull(e.getIdField());
        assertEquals("id", e.getIdField().getName());
        assertEquals("Long", e.getIdField().getType());
        assertEquals("IDENTITY", e.getIdField().getStrategy());

        assertNotNull(e.getIdGeneration());
        assertEquals("IDENTITY", e.getIdGeneration().getStrategy());
    }

    @Test
    void extract_idFieldNoGeneratedValue_strategyNull() {
        Field f = field("Long", "id", "@Id");
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);

        assertNotNull(e.getIdField());
        assertNull(e.getIdField().getStrategy());
        assertNull(e.getIdGeneration().getStrategy());
    }

    @Test
    void extract_noIdField_idFieldNull() {
        Field f = field("String", "name");
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);
        assertNull(e.getIdField());
    }

    // -----------------------------------------------------------------------
    // @EmbeddedId
    // -----------------------------------------------------------------------

    @Test
    void extract_embeddedId_setsCompositeId() {
        Field f = field("OrderPK", "pk", "@EmbeddedId");
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);

        assertNotNull(e.getCompositeId());
        assertEquals("pk", e.getCompositeId().getName());
        assertEquals("OrderPK", e.getCompositeId().getType());
    }

    @Test
    void extract_noEmbeddedId_compositeIdNull() {
        Type t = entityType("@Entity");
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);
        assertNull(e.getCompositeId());
    }

    // -----------------------------------------------------------------------
    // @Version
    // -----------------------------------------------------------------------

    @Test
    void extract_versionField_setsVersionField() {
        Field f = field("int", "version", "@Version");
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);

        assertNotNull(e.getVersionField());
        assertEquals("version", e.getVersionField().getName());
        assertEquals("int", e.getVersionField().getType());
    }

    @Test
    void extract_noVersionField_versionFieldNull() {
        Type t = entityType("@Entity");
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);
        assertNull(e.getVersionField());
    }

    // -----------------------------------------------------------------------
    // Relationships
    // -----------------------------------------------------------------------

    @Test
    void extract_oneToManyRelationship_addedToRelationships() {
        Field f = field("List<Item>", "items",
                "@OneToMany(mappedBy = \"order\", fetch = FetchType.LAZY)");
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);

        assertEquals(1, e.getRelationships().size());
        RelationshipModel rm = e.getRelationships().get(0);
        assertEquals("OneToMany", rm.getType());
        assertEquals("List", rm.getCollectionType());
        assertEquals("Item", rm.getTargetEntity());
        assertEquals("order", rm.getMappedBy());
        assertEquals("LAZY", rm.getFetch());
    }

    @Test
    void extract_manyToOneRelationship_addedToRelationships() {
        Field f = field("Customer", "customer", "@ManyToOne");
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);

        assertEquals(1, e.getRelationships().size());
        assertEquals("ManyToOne", e.getRelationships().get(0).getType());
    }

    @Test
    void extract_joinColumn_setOnRelationship() {
        Field f = field("Customer", "customer",
                "@ManyToOne",
                "@JoinColumn(name = \"customer_id\")");
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);

        assertEquals("customer_id", e.getRelationships().get(0).getColumn());
    }

    @Test
    void extract_noRelationshipAnnotations_listIsEmpty() {
        Field f = field("String", "name");
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);
        assertTrue(e.getRelationships().isEmpty());
    }

    // -----------------------------------------------------------------------
    // Soft-delete detection
    // -----------------------------------------------------------------------

    @Test
    void extract_deletedBooleanField_softDeleteFlagTrue() {
        Field f = field("boolean", "deleted");
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);
        assertTrue(e.isSoftDeleteFlag());
    }

    @Test
    void extract_noSoftDeleteIndicators_softDeleteFlagFalse() {
        Field f = field("String", "name");
        Type t = entityType("@Entity");
        t.fieldDeclarations = List.of(f);
        EntityModel e = extractor.extract(analysis("com.example.Order", t), Map.of()).get(0);
        assertFalse(e.isSoftDeleteFlag());
    }

    // -----------------------------------------------------------------------
    // Multiple entities across compilation units
    // -----------------------------------------------------------------------

    @Test
    void extract_twoEntityClasses_bothReturned() {
        Type order = entityType("@Entity");
        Type customer = entityType("@Entity");

        JavaCompilationUnit cu1 = new JavaCompilationUnit();
        cu1.filePath = "/src/com/example/Order.java";
        cu1.typeDeclarations = new LinkedHashMap<>();
        cu1.typeDeclarations.put("com.example.Order", order);

        JavaCompilationUnit cu2 = new JavaCompilationUnit();
        cu2.filePath = "/src/com/example/Customer.java";
        cu2.typeDeclarations = new LinkedHashMap<>();
        cu2.typeDeclarations.put("com.example.Customer", customer);

        AnalysisResult result = new AnalysisResult();
        result.symbolTable = new LinkedHashMap<>();
        result.symbolTable.put(cu1.filePath, cu1);
        result.symbolTable.put(cu2.filePath, cu2);

        List<EntityModel> entities = extractor.extract(result, Map.of());
        assertEquals(2, entities.size());
    }

    @Test
    void extract_mixedEntityAndNonEntity_onlyEntityReturned() {
        Type entity = entityType("@Entity");
        Type service = entityType("@Service");

        JavaCompilationUnit cu = new JavaCompilationUnit();
        cu.filePath = "/src/com/example/Mixed.java";
        cu.typeDeclarations = new LinkedHashMap<>();
        cu.typeDeclarations.put("com.example.Order", entity);
        cu.typeDeclarations.put("com.example.MyService", service);

        AnalysisResult result = new AnalysisResult();
        result.symbolTable = new LinkedHashMap<>();
        result.symbolTable.put(cu.filePath, cu);

        List<EntityModel> entities = extractor.extract(result, Map.of());
        assertEquals(1, entities.size());
        assertEquals("Order", entities.get(0).getClassName());
    }
}
