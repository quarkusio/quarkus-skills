package com.migration.validator.extractor.generate;

import com.migration.validator.model.PersistenceModels.RepositoryModel;
import com.migration.validator.model.PersistenceModels.TransactionManagementModel;
import com.migration.validator.model.cldk.AnalysisResult;
import com.migration.validator.model.cldk.Callable;
import com.migration.validator.model.cldk.CRUDOperation;
import com.migration.validator.model.cldk.Field;
import com.migration.validator.model.cldk.JavaCompilationUnit;
import com.migration.validator.model.cldk.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RepositoryExtractor}.
 *
 * All tests use synthetic {@link AnalysisResult} fixtures.
 * No real project or external tool is required.
 */
class RepositoryExtractorTest {

    private RepositoryExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new RepositoryExtractor();
    }

    // -----------------------------------------------------------------------
    // Shared fixture helpers
    // -----------------------------------------------------------------------

    private AnalysisResult singleTypeAnalysis(String fqn, Type type) {
        JavaCompilationUnit cu = new JavaCompilationUnit();
        cu.filePath = "/src/" + fqn.replace('.', '/') + ".java";
        cu.typeDeclarations = new LinkedHashMap<>();
        cu.typeDeclarations.put(fqn, type);

        AnalysisResult result = new AnalysisResult();
        result.symbolTable = new LinkedHashMap<>();
        result.symbolTable.put(cu.filePath, cu);
        return result;
    }

    private Field field(String typeName, String varName, String... annotations) {
        Field f = new Field();
        f.type = typeName;
        f.variables = List.of(varName);
        f.annotations = Arrays.asList(annotations);
        return f;
    }

    private Callable callableWithCrud(String operationType, String targetTable) {
        CRUDOperation op = new CRUDOperation();
        op.operationType = operationType;
        op.targetTable = targetTable;
        Callable c = new Callable();
        c.crudOperations = List.of(op);
        return c;
    }

    // -----------------------------------------------------------------------
    // Null / empty guard
    // -----------------------------------------------------------------------

    @Test
    void allExtractors_nullAnalysis_returnEmpty() {
        assertTrue(extractor.extractJakarta(null).isEmpty());
        assertTrue(extractor.extractSpring(null).isEmpty());
        assertTrue(extractor.extractPanache(null).isEmpty());
        assertTrue(extractor.extractQuarkus(null).isEmpty());
    }

    @Test
    void allExtractors_nullSymbolTable_returnEmpty() {
        AnalysisResult analysis = new AnalysisResult();
        analysis.symbolTable = null;

        assertTrue(extractor.extractJakarta(analysis).isEmpty());
        assertTrue(extractor.extractSpring(analysis).isEmpty());
        assertTrue(extractor.extractPanache(analysis).isEmpty());
        assertTrue(extractor.extractQuarkus(analysis).isEmpty());
    }

    // -----------------------------------------------------------------------
    // extractJakarta
    // -----------------------------------------------------------------------

    @Nested
    class ExtractJakarta {

        @Test
        void stateless_withEntityManager_isExtracted() {
            Type type = new Type();
            type.annotations = List.of("@Stateless");
            type.fieldDeclarations = List.of(field("EntityManager", "em"));

            List<RepositoryModel> repos = extractor.extractJakarta(
                    singleTypeAnalysis("com.example.OrderRepo", type));

            assertEquals(1, repos.size());
            RepositoryModel repo = repos.get(0);
            assertEquals("OrderRepo", repo.getClassName());
            assertEquals("com.example", repo.getPackageName());
            assertTrue(repo.isUsesEntityManager());
            assertEquals("STATELESS", repo.getEjbType());
        }

        @Test
        void stateful_withEntityManager_isExtracted() {
            Type type = new Type();
            type.annotations = List.of("@Stateful");
            type.fieldDeclarations = List.of(field("EntityManager", "em"));

            RepositoryModel repo = extractor.extractJakarta(
                    singleTypeAnalysis("com.example.CartRepo", type)).get(0);
            assertEquals("STATEFUL", repo.getEjbType());
        }

        @Test
        void singleton_withEntityManager_isExtracted() {
            Type type = new Type();
            type.annotations = List.of("@Singleton");
            type.fieldDeclarations = List.of(field("EntityManager", "em"));

            RepositoryModel repo = extractor.extractJakarta(
                    singleTypeAnalysis("com.example.ConfigRepo", type)).get(0);
            assertEquals("SINGLETON", repo.getEjbType());
        }

        @Test
        void stateless_withoutEntityManager_notExtracted() {
            Type type = new Type();
            type.annotations = List.of("@Stateless");
            type.fieldDeclarations = List.of(field("SomeService", "svc"));

            assertTrue(extractor.extractJakarta(
                    singleTypeAnalysis("com.example.NotARepo", type)).isEmpty());
        }

        @Test
        void entityManagerPresent_withoutCdiAnnotation_notExtracted() {
            Type type = new Type();
            type.annotations = List.of("@Service");
            type.fieldDeclarations = List.of(field("EntityManager", "em"));

            assertTrue(extractor.extractJakarta(
                    singleTypeAnalysis("com.example.SpringService", type)).isEmpty());
        }

        @Test
        void persistenceContext_isDetected() {
            Type type = new Type();
            type.annotations = List.of("@Stateless");
            type.fieldDeclarations = List.of(
                    field("EntityManager", "em", "@PersistenceContext"));

            RepositoryModel repo = extractor.extractJakarta(
                    singleTypeAnalysis("com.example.Repo", type)).get(0);

            assertTrue(repo.isPersistenceContext(),
                    "persistenceContext should be true when @PersistenceContext is present");
            assertFalse(repo.isPersistenceUnit(),
                    "persistenceUnit should be false when @PersistenceUnit is absent");
        }

        @Test
        void persistenceUnit_isDetected() {
            Type type = new Type();
            type.annotations = List.of("@Stateless");
            type.fieldDeclarations = List.of(
                    field("EntityManagerFactory", "emf", "@PersistenceUnit"),
                    field("EntityManager", "em"));

            RepositoryModel repo = extractor.extractJakarta(
                    singleTypeAnalysis("com.example.Repo", type)).get(0);

            assertFalse(repo.isPersistenceContext());
            assertTrue(repo.isPersistenceUnit());
        }

        @Test
        void transactionManagement_containerType_byDefault() {
            Type type = new Type();
            type.annotations = List.of("@Stateless");
            type.fieldDeclarations = List.of(field("EntityManager", "em"));

            RepositoryModel repo = extractor.extractJakarta(
                    singleTypeAnalysis("com.example.Repo", type)).get(0);

            TransactionManagementModel tx = repo.getTransactionManagement();
            assertNotNull(tx);
            assertEquals("CONTAINER", tx.getType());
            assertFalse(tx.isHasManualTransactions());
        }

        @Test
        void transactionManagement_beanType_detected() {
            Type type = new Type();
            type.annotations = List.of(
                    "@Stateless",
                    "@TransactionManagement(TransactionManagementType.BEAN)");
            type.fieldDeclarations = List.of(field("EntityManager", "em"));

            RepositoryModel repo = extractor.extractJakarta(
                    singleTypeAnalysis("com.example.Repo", type)).get(0);

            assertEquals("BEAN", repo.getTransactionManagement().getType());
        }

        @Test
        void crudOperations_populateManagedEntitiesAndOperations() {
            Type type = new Type();
            type.annotations = List.of("@Stateless");
            type.fieldDeclarations = List.of(field("EntityManager", "em"));
            type.callableDeclarations = new LinkedHashMap<>();
            type.callableDeclarations.put("save(Order)", callableWithCrud("CREATE", "Order"));
            type.callableDeclarations.put("find(long)", callableWithCrud("READ", "Order"));

            RepositoryModel repo = extractor.extractJakarta(
                    singleTypeAnalysis("com.example.OrderRepo", type)).get(0);

            assertTrue(repo.getManagedEntities().contains("Order"));
            assertTrue(repo.getOperations().contains("CREATE"));
            assertTrue(repo.getOperations().contains("READ"));
        }

        @Test
        void packageName_isEmptyForDefaultPackage() {
            Type type = new Type();
            type.annotations = List.of("@Stateless");
            type.fieldDeclarations = List.of(field("EntityManager", "em"));

            RepositoryModel repo = extractor.extractJakarta(
                    singleTypeAnalysis("SimpleRepo", type)).get(0);

            assertEquals("", repo.getPackageName());
            assertEquals("SimpleRepo", repo.getClassName());
        }

        @Test
        void originalFile_isSet() {
            Type type = new Type();
            type.annotations = List.of("@Stateless");
            type.fieldDeclarations = List.of(field("EntityManager", "em"));

            RepositoryModel repo = extractor.extractJakarta(
                    singleTypeAnalysis("com.example.Repo", type)).get(0);

            assertNotNull(repo.getOriginalFile());
            assertTrue(repo.getOriginalFile().endsWith(".java"));
        }
    }

    // -----------------------------------------------------------------------
    // extractSpring
    // -----------------------------------------------------------------------

    @Nested
    class ExtractSpring {

        @Test
        void repositoryAnnotation_isExtracted() {
            Type type = new Type();
            type.annotations = List.of("@Repository");

            List<RepositoryModel> repos = extractor.extractSpring(
                    singleTypeAnalysis("com.example.ProductRepo", type));

            assertEquals(1, repos.size());
            assertEquals("ProductRepo", repos.get(0).getClassName());
        }

        @Test
        void entityManagerField_isExtracted() {
            Type type = new Type();
            type.fieldDeclarations = List.of(field("EntityManager", "em"));

            List<RepositoryModel> repos = extractor.extractSpring(
                    singleTypeAnalysis("com.example.CustomRepo", type));

            assertEquals(1, repos.size());
            assertTrue(repos.get(0).isUsesEntityManager());
        }

        @Test
        void springDataInterface_jpaRepository_isExtracted() {
            Type type = new Type();
            type.isInterface = true;
            type.extendsList = List.of("JpaRepository<Product, Long>");

            List<RepositoryModel> repos = extractor.extractSpring(
                    singleTypeAnalysis("com.example.ProductRepo", type));

            assertEquals(1, repos.size());
        }

        @Test
        void springDataInterface_crudRepository_isExtracted() {
            Type type = new Type();
            type.isInterface = true;
            type.extendsList = List.of("CrudRepository<Item, Long>");

            assertFalse(extractor.extractSpring(
                    singleTypeAnalysis("com.example.ItemRepo", type)).isEmpty());
        }

        @Test
        void springDataInterface_pagingAndSortingRepository_isExtracted() {
            Type type = new Type();
            type.isInterface = true;
            type.extendsList = List.of("PagingAndSortingRepository<Order, UUID>");

            assertFalse(extractor.extractSpring(
                    singleTypeAnalysis("com.example.OrderRepo", type)).isEmpty());
        }

        @Test
        void plainClass_noAnnotationNoEM_notExtracted() {
            Type type = new Type();
            type.annotations = List.of("@Service");

            assertTrue(extractor.extractSpring(
                    singleTypeAnalysis("com.example.SomeService", type)).isEmpty());
        }

        @Test
        void autowiredDependencies_areCollected() {
            Type type = new Type();
            type.annotations = List.of("@Repository");
            type.fieldDeclarations = List.of(
                    field("EventPublisher", "publisher", "@Autowired"));

            RepositoryModel repo = extractor.extractSpring(
                    singleTypeAnalysis("com.example.Repo", type)).get(0);

            assertNotNull(repo.getAutowiredDependencies());
            assertEquals(1, repo.getAutowiredDependencies().size());
            assertEquals("publisher", repo.getAutowiredDependencies().get(0).get("name"));
            assertEquals("EventPublisher", repo.getAutowiredDependencies().get(0).get("type"));
        }

        @Test
        void injectAnnotation_qualifiesAsDependency() {
            Type type = new Type();
            type.annotations = List.of("@Repository");
            type.fieldDeclarations = List.of(
                    field("EventBus", "bus", "@Inject"));

            List<Map<String, Object>> deps = extractor.extractSpring(
                    singleTypeAnalysis("com.example.Repo", type)).get(0).getAutowiredDependencies();

            assertEquals(1, deps.size());
            assertEquals("bus", deps.get(0).get("name"));
        }

        @Test
        void autowiredDependencies_emptyWhenNonePresentButFieldExists() {
            Type type = new Type();
            type.annotations = List.of("@Repository");
            // field exists but has no injection annotation
            type.fieldDeclarations = List.of(field("SomeHelper", "helper"));

            List<Map<String, Object>> deps = extractor.extractSpring(
                    singleTypeAnalysis("com.example.Repo", type)).get(0).getAutowiredDependencies();

            assertNotNull(deps);
            assertTrue(deps.isEmpty());
        }

        @Test
        void transactionalMethod_defaultPropagation_isRequired() {
            Type type = new Type();
            type.annotations = List.of("@Repository");
            type.callableDeclarations = new LinkedHashMap<>();
            Callable m = new Callable();
            m.annotations = List.of("@Transactional");
            type.callableDeclarations.put("save(Order)", m);

            RepositoryModel repo = extractor.extractSpring(
                    singleTypeAnalysis("com.example.Repo", type)).get(0);

            TransactionManagementModel tx = repo.getTransactionManagement();
            assertEquals(1, tx.getTransactionAttributes().size());
            assertEquals("REQUIRED", tx.getTransactionAttributes().get(0).getPropagation());
        }

        @Test
        void transactionalMethod_explicitPropagation_isExtracted() {
            Type type = new Type();
            type.annotations = List.of("@Repository");
            type.callableDeclarations = new LinkedHashMap<>();
            Callable m = new Callable();
            m.annotations = List.of("@Transactional(propagation = REQUIRES_NEW)");
            type.callableDeclarations.put("create(Item)", m);

            RepositoryModel repo = extractor.extractSpring(
                    singleTypeAnalysis("com.example.Repo", type)).get(0);

            assertEquals("REQUIRES_NEW",
                    repo.getTransactionManagement().getTransactionAttributes().get(0)
                            .getPropagation());
        }

        @Test
        void transactionalMethod_qualifiedPropagation_isStripped() {
            Type type = new Type();
            type.annotations = List.of("@Repository");
            type.callableDeclarations = new LinkedHashMap<>();
            Callable m = new Callable();
            m.annotations = List.of("@Transactional(propagation = Propagation.MANDATORY)");
            type.callableDeclarations.put("fetch(long)", m);

            RepositoryModel repo = extractor.extractSpring(
                    singleTypeAnalysis("com.example.Repo", type)).get(0);

            assertEquals("MANDATORY",
                    repo.getTransactionManagement().getTransactionAttributes().get(0)
                            .getPropagation());
        }

        @Test
        void transactionalMethod_methodNameExtractedFromSig() {
            Type type = new Type();
            type.annotations = List.of("@Repository");
            type.callableDeclarations = new LinkedHashMap<>();
            Callable m = new Callable();
            m.annotations = List.of("@Transactional");
            // fully-qualified signature as codeanalyzer emits it
            type.callableDeclarations.put("com.example.Repo.findAll()", m);

            RepositoryModel repo = extractor.extractSpring(
                    singleTypeAnalysis("com.example.Repo", type)).get(0);

            assertEquals("findAll",
                    repo.getTransactionManagement().getTransactionAttributes().get(0).getMethod());
        }

        @Test
        void springDataInterface_genericEntityIsAddedToManagedEntities() {
            Type type = new Type();
            type.isInterface = true;
            type.extendsList = List.of("JpaRepository<Invoice, Long>");

            RepositoryModel repo = extractor.extractSpring(
                    singleTypeAnalysis("com.example.InvoiceRepo", type)).get(0);

            assertTrue(repo.getManagedEntities().contains("Invoice"),
                    "First generic parameter of JpaRepository should be in managedEntities");
        }

        @Test
        void springDataInterface_fullyQualifiedGenericEntityIsStripped() {
            Type type = new Type();
            type.isInterface = true;
            type.extendsList = List.of("JpaRepository<com.example.domain.Order, Long>");

            RepositoryModel repo = extractor.extractSpring(
                    singleTypeAnalysis("com.example.OrderRepo", type)).get(0);

            assertTrue(repo.getManagedEntities().contains("Order"),
                    "Package should be stripped from entity type name");
        }

        @Test
        void transactionManagementType_isNone() {
            Type type = new Type();
            type.annotations = List.of("@Repository");

            RepositoryModel repo = extractor.extractSpring(
                    singleTypeAnalysis("com.example.Repo", type)).get(0);

            assertEquals("NONE", repo.getTransactionManagement().getType());
        }

        @Test
        void crudOperations_populateManagedEntitiesAndOperations() {
            Type type = new Type();
            type.annotations = List.of("@Repository");
            type.callableDeclarations = new LinkedHashMap<>();
            type.callableDeclarations.put("delete(long)", callableWithCrud("DELETE", "Invoice"));

            RepositoryModel repo = extractor.extractSpring(
                    singleTypeAnalysis("com.example.InvoiceRepo", type)).get(0);

            assertTrue(repo.getOperations().contains("DELETE"));
            assertTrue(repo.getManagedEntities().contains("Invoice"));
        }

        @Test
        void packageName_isEmptyForDefaultPackage() {
            Type type = new Type();
            type.annotations = List.of("@Repository");

            RepositoryModel repo = extractor.extractSpring(
                    singleTypeAnalysis("SimpleRepo", type)).get(0);

            assertEquals("", repo.getPackageName());
            assertEquals("SimpleRepo", repo.getClassName());
        }
    }

    // -----------------------------------------------------------------------
    // extractPanache
    // -----------------------------------------------------------------------

    @Nested
    class ExtractPanache {

        @Test
        void panacheRepository_entityExtracted() {
            Type type = new Type();
            type.implementsList = List.of("PanacheRepository<Order>");

            RepositoryModel repo = extractor.extractPanache(
                    singleTypeAnalysis("com.example.OrderRepo", type)).get(0);

            assertEquals("OrderRepo", repo.getClassName());
            assertEquals("com.example", repo.getPackageName());
            assertTrue(repo.getManagedEntities().contains("Order"));
        }

        @Test
        void panacheRepositoryBase_entityExtracted_idTypeIgnored() {
            Type type = new Type();
            type.implementsList = List.of("PanacheRepositoryBase<Product, Long>");

            RepositoryModel repo = extractor.extractPanache(
                    singleTypeAnalysis("com.example.ProductRepo", type)).get(0);

            assertTrue(repo.getManagedEntities().contains("Product"));
            assertFalse(repo.getManagedEntities().contains("Long"));
        }

        @Test
        void injectedDependency_isCollected() {
            Type type = new Type();
            type.implementsList = List.of("PanacheRepository<Order>");
            type.fieldDeclarations = List.of(field("EventBus", "bus", "@Inject"));

            RepositoryModel repo = extractor.extractPanache(
                    singleTypeAnalysis("com.example.OrderRepo", type)).get(0);

            assertNotNull(repo.getAutowiredDependencies());
            assertEquals(1, repo.getAutowiredDependencies().size());
            assertEquals("bus", repo.getAutowiredDependencies().get(0).get("name"));
        }

        @Test
        void transactionManagement_isNone() {
            Type type = new Type();
            type.implementsList = List.of("PanacheRepository<Order>");

            RepositoryModel repo = extractor.extractPanache(
                    singleTypeAnalysis("com.example.OrderRepo", type)).get(0);

            assertEquals("NONE", repo.getTransactionManagement().getType());
        }

        @Test
        void crudOperations_populateOperations() {
            Type type = new Type();
            type.implementsList = List.of("PanacheRepository<Order>");
            type.callableDeclarations = new LinkedHashMap<>();
            type.callableDeclarations.put("persist(Order)", callableWithCrud("CREATE", "Order"));

            RepositoryModel repo = extractor.extractPanache(
                    singleTypeAnalysis("com.example.OrderRepo", type)).get(0);

            assertTrue(repo.getOperations().contains("CREATE"));
        }

        @Test
        void fullyQualifiedGeneric_stripsPackage() {
            Type type = new Type();
            type.implementsList = List.of("PanacheRepository<com.example.domain.Invoice>");

            RepositoryModel repo = extractor.extractPanache(
                    singleTypeAnalysis("com.example.repo.InvoiceRepository", type)).get(0);

            assertEquals("InvoiceRepository", repo.getClassName());
            assertTrue(repo.getManagedEntities().contains("Invoice"),
                    "Package prefix should be stripped from entity type");
        }

        @Test
        void setsNoEjbMetadata() {
            Type type = new Type();
            type.implementsList = List.of("PanacheRepository<Order>");

            RepositoryModel repo = extractor.extractPanache(
                    singleTypeAnalysis("com.example.OrderRepository", type)).get(0);

            assertNull(repo.getEjbType(), "Panache repos should not have ejb_type set");
            assertNull(repo.isPersistenceContext(),
                    "Panache repos should not have persistence_context set");
            assertNull(repo.isPersistenceUnit(), "Panache repos should not have persistence_unit set");
        }

        @Test
        void extendsOnlyPanacheEntity_notExtracted() {
            Type type = new Type();
            type.extendsList = List.of("PanacheEntity"); // Active Record — not a repository

            assertTrue(extractor.extractPanache(
                    singleTypeAnalysis("com.example.Order", type)).isEmpty());
        }

        @Test
        void plainClass_notExtracted() {
            Type type = new Type(); // no implementsList, no extendsList

            assertTrue(extractor.extractPanache(
                    singleTypeAnalysis("com.example.SomeService", type)).isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // extractQuarkus — deduplication
    // -----------------------------------------------------------------------

    @Nested
    class ExtractQuarkus {

        @Test
        void mergesJakartaAndPanacheResults() {
            JavaCompilationUnit ejbCu = new JavaCompilationUnit();
            ejbCu.filePath = "/src/EjbOrderRepo.java";
            ejbCu.typeDeclarations = new LinkedHashMap<>();
            Type ejbType = new Type();
            ejbType.annotations = List.of("@Stateless");
            ejbType.fieldDeclarations = List.of(field("EntityManager", "em"));
            ejbCu.typeDeclarations.put("com.example.EjbOrderRepo", ejbType);

            JavaCompilationUnit panacheCu = new JavaCompilationUnit();
            panacheCu.filePath = "/src/PanacheProductRepo.java";
            panacheCu.typeDeclarations = new LinkedHashMap<>();
            Type panacheType = new Type();
            panacheType.implementsList = List.of("PanacheRepository<Product>");
            panacheCu.typeDeclarations.put("com.example.PanacheProductRepo", panacheType);

            AnalysisResult analysis = new AnalysisResult();
            analysis.symbolTable = new LinkedHashMap<>();
            analysis.symbolTable.put(ejbCu.filePath, ejbCu);
            analysis.symbolTable.put(panacheCu.filePath, panacheCu);

            List<RepositoryModel> repos = extractor.extractQuarkus(analysis);

            assertEquals(2, repos.size());
            assertTrue(repos.stream().anyMatch(r -> "EjbOrderRepo".equals(r.getClassName())));
            assertTrue(repos.stream().anyMatch(r -> "PanacheProductRepo".equals(r.getClassName())));
        }

        @Test
        void jakartaTakesPrecedenceOverPanacheForSameFqn() {
            // Same FQN qualifies for both Jakarta (has @Stateless + EntityManager) and
            // Panache
            JavaCompilationUnit cu = new JavaCompilationUnit();
            cu.filePath = "/src/HybridRepo.java";
            cu.typeDeclarations = new LinkedHashMap<>();
            Type type = new Type();
            type.annotations = List.of("@Stateless");
            type.fieldDeclarations = List.of(field("EntityManager", "em"));
            type.implementsList = List.of("PanacheRepository<Order>");
            cu.typeDeclarations.put("com.example.HybridRepo", type);

            AnalysisResult analysis = new AnalysisResult();
            analysis.symbolTable = new LinkedHashMap<>();
            analysis.symbolTable.put(cu.filePath, cu);

            List<RepositoryModel> repos = extractor.extractQuarkus(analysis);

            assertEquals(1, repos.size(), "Duplicate FQN should appear only once");
            assertEquals("STATELESS", repos.get(0).getEjbType(),
                    "Jakarta result should take precedence over Panache for the same class");
        }

        @Test
        void quarkus_pureJakarta_noOverlap_returnsJakartaRepo() {
            Type type = new Type();
            type.annotations = List.of("@ApplicationScoped");
            type.fieldDeclarations = List.of(field("EntityManager", "em"));

            List<RepositoryModel> repos = extractor.extractQuarkus(
                    singleTypeAnalysis("com.example.CdiRepo", type));

            assertEquals(1, repos.size());
            assertEquals("CdiRepo", repos.get(0).getClassName());
        }
    }
}
