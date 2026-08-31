package com.migration.validator.extractor.generate;

import com.migration.validator.model.PersistenceModels.RepositoryModel;
import com.migration.validator.model.PersistenceModels.TransactionAttribute;
import com.migration.validator.model.PersistenceModels.TransactionManagementModel;
import com.migration.validator.model.cldk.AnalysisResult;
import com.migration.validator.model.cldk.Callable;
import com.migration.validator.model.cldk.CRUDOperation;
import com.migration.validator.model.cldk.Field;
import com.migration.validator.model.cldk.JavaCompilationUnit;
import com.migration.validator.model.cldk.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts repository / DAO classes from an AnalysisResult.
 *
 * Two modes:
 * - Jakarta EE: @Stateless, @Stateful, @Singleton with EntityManager field
 * - Spring: @Repository or extends JpaRepository/CrudRepository; also classes
 * with @Repository or EntityManager field.
 */
public class RepositoryExtractor {

    private static final List<String> CDI_EJB_ANNOTATIONS = Arrays.asList(
            "@Stateless", "@Singleton", "@Stateful",
            "@ApplicationScoped", "@RequestScoped", "@SessionScoped");

    private static final List<String> SPRING_REPO_SUPERS = Arrays.asList(
            "JpaRepository", "CrudRepository", "PagingAndSortingRepository",
            "ReactiveCrudRepository", "ReactiveMongoRepository", "Repository");

    private static final List<String> PANACHE_REPO_INTERFACES = Arrays.asList(
            "PanacheRepository", "PanacheRepositoryBase");

    /**
     * Matches the first type argument in a generic clause, e.g.
     * {@code <Order, Long>} -> {@code Order}.
     */
    private static final Pattern GENERIC_FIRST_ARG = Pattern.compile("<\\s*(.+?)\\s*,");
    /**
     * Stricter variant that also handles single-argument generics, e.g.
     * {@code <Order>} -> {@code Order}.
     */
    private static final Pattern GENERIC_FIRST_ARG_STRICT = Pattern.compile("<\\s*([^,>]+?)\\s*[,>]");

    // -----------------------------------------------------------------------
    // Jakarta EE / Quarkus repositories
    // -----------------------------------------------------------------------

    public List<RepositoryModel> extractJakarta(AnalysisResult analysis) {
        List<RepositoryModel> repos = new ArrayList<>();
        if (analysis == null || analysis.symbolTable == null)
            return repos;

        for (JavaCompilationUnit cu : analysis.symbolTable.values()) {
            if (cu.typeDeclarations == null)
                continue;
            for (Map.Entry<String, Type> e : cu.typeDeclarations.entrySet()) {
                String qname = e.getKey();
                Type jtype = e.getValue();
                List<String> annotations = annotations(jtype);
                List<Field> fields = fields(jtype);

                boolean usesEntityManager = fields.stream()
                        .anyMatch(f -> f.type != null && f.type.contains("EntityManager"));
                boolean hasCdiEjb = annotations.stream()
                        .anyMatch(a -> CDI_EJB_ANNOTATIONS.stream().anyMatch(a::contains));
                if (!hasCdiEjb || !usesEntityManager)
                    continue;

                repos.add(buildJakartaRepo(cu.filePath, qname, jtype, fields, annotations));
            }
        }
        return repos;
    }

    // -----------------------------------------------------------------------
    // Spring repositories
    // -----------------------------------------------------------------------

    public List<RepositoryModel> extractSpring(AnalysisResult analysis) {
        List<RepositoryModel> repos = new ArrayList<>();
        if (analysis == null || analysis.symbolTable == null)
            return repos;

        for (JavaCompilationUnit cu : analysis.symbolTable.values()) {
            if (cu.typeDeclarations == null)
                continue;
            for (Map.Entry<String, Type> e : cu.typeDeclarations.entrySet()) {
                String qname = e.getKey();
                Type jtype = e.getValue();

                // Spring Data interfaces
                if (jtype.isInterface && jtype.extendsList != null &&
                        jtype.extendsList.stream()
                                .anyMatch(ext -> SPRING_REPO_SUPERS.stream().anyMatch(ext::contains))) {
                    repos.add(buildSpringRepo(cu.filePath, qname, jtype));
                    continue;
                }

                // @Repository annotation or EntityManager field
                List<String> annotations = annotations(jtype);
                List<Field> fields = fields(jtype);
                boolean hasRepoAnn = annotations.stream().anyMatch(a -> a.contains("@Repository"));
                boolean usesEM = fields.stream().anyMatch(f -> f.type != null && f.type.contains("EntityManager"));
                if (hasRepoAnn || usesEM) {
                    repos.add(buildSpringRepo(cu.filePath, qname, jtype));
                }
            }
        }
        return repos;
    }

    // -----------------------------------------------------------------------
    // Quarkus repositories (Jakarta EJB + Panache, deduplicated)
    // -----------------------------------------------------------------------

    public List<RepositoryModel> extractQuarkus(AnalysisResult analysis) {
        // Deduplicate by fully-qualified class name; Jakarta results take precedence
        Map<String, RepositoryModel> seen = new LinkedHashMap<>();
        for (RepositoryModel repo : extractJakarta(analysis)) {
            seen.put(repo.getPackageName() + "." + repo.getClassName(), repo);
        }
        for (RepositoryModel repo : extractPanache(analysis)) {
            seen.putIfAbsent(repo.getPackageName() + "." + repo.getClassName(), repo);
        }
        return new ArrayList<>(seen.values());
    }

    // -----------------------------------------------------------------------
    // Panache repositories
    // -----------------------------------------------------------------------

    public List<RepositoryModel> extractPanache(AnalysisResult analysis) {
        List<RepositoryModel> repos = new ArrayList<>();
        if (analysis == null || analysis.symbolTable == null)
            return repos;

        for (JavaCompilationUnit cu : analysis.symbolTable.values()) {
            if (cu.typeDeclarations == null)
                continue;
            for (Map.Entry<String, Type> e : cu.typeDeclarations.entrySet()) {
                String qname = e.getKey();
                Type jtype = e.getValue();
                List<String> implList = jtype.implementsList != null ? jtype.implementsList : List.of();

                String matchedImpl = implList.stream()
                        .filter(impl -> PANACHE_REPO_INTERFACES.stream().anyMatch(impl::contains))
                        .findFirst()
                        .orElse(null);
                if (matchedImpl == null)
                    continue;

                repos.add(buildPanacheRepo(cu.filePath, qname, jtype, matchedImpl));
            }
        }
        return repos;
    }

    // -----------------------------------------------------------------------
    // Internal builders
    // -----------------------------------------------------------------------

    private RepositoryModel buildJakartaRepo(String filePath, String qname, Type jtype,
            List<Field> fields, List<String> annotations) {
        RepositoryModel repo = newRepo(filePath, qname, fields);

        // EJB type
        for (String ann : annotations) {
            if (ann.contains("@Stateless")) {
                repo.setEjbType("STATELESS");
                break;
            }
            if (ann.contains("@Stateful")) {
                repo.setEjbType("STATEFUL");
                break;
            }
            if (ann.contains("@Singleton")) {
                repo.setEjbType("SINGLETON");
                break;
            }
        }

        // PersistenceContext / PersistenceUnit
        boolean hasPersistenceContext = false;
        boolean hasPersistenceUnit = false;
        for (Field f : fields) {
            if (f.annotations == null)
                continue;
            if (f.annotations.stream().anyMatch(a -> a.contains("@PersistenceContext")))
                hasPersistenceContext = true;
            if (f.annotations.stream().anyMatch(a -> a.contains("@PersistenceUnit")))
                hasPersistenceUnit = true;
        }
        repo.setPersistenceContext(hasPersistenceContext);
        repo.setPersistenceUnit(hasPersistenceUnit);

        repo.setTransactionManagement(buildTxMgmt(detectTxManagementType(annotations), new ArrayList<>()));

        CrudSets crud = collectCrud(jtype);
        repo.setManagedEntities(new ArrayList<>(crud.entities));
        repo.setOperations(new ArrayList<>(crud.operations));
        repo.setEntityOperations(new LinkedHashMap<>());
        return repo;
    }

    /**
     * Build a Spring repository model.
     */
    private RepositoryModel buildSpringRepo(String filePath, String qname, Type jtype) {
        List<Field> fields = fields(jtype);
        RepositoryModel repo = newRepo(filePath, qname, fields);

        repo.setAutowiredDependencies(collectInjectedDeps(fields));
        repo.setTransactionManagement(buildTxMgmt("NONE", extractTransactionAttributes(jtype.callableDeclarations)));

        CrudSets crud = collectCrud(jtype);
        // Spring Data interface: also extract entity type from extends-clause generics
        if (jtype.isInterface && jtype.extendsList != null) {
            for (String ext : jtype.extendsList) {
                Matcher gm = GENERIC_FIRST_ARG.matcher(ext);
                if (gm.find()) {
                    crud.entities.add(simpleName(gm.group(1)));
                }
            }
        }
        repo.setManagedEntities(new ArrayList<>(crud.entities));
        repo.setOperations(new ArrayList<>(crud.operations));
        repo.setEntityOperations(new LinkedHashMap<>());
        return repo;
    }

    /**
     * Scans all callable declarations for {@code @Transactional} annotations and
     * returns
     * one {@link TransactionAttribute} per annotated method.
     *
     * @param callableDeclarations the type's callable map; may be {@code null}
     * @return list of transaction attributes, never {@code null}
     */
    private static List<TransactionAttribute> extractTransactionAttributes(
            Map<String, Callable> callableDeclarations) {
        List<TransactionAttribute> txAttrs = new ArrayList<>();
        if (callableDeclarations == null)
            return txAttrs;
        for (Map.Entry<String, Callable> entry : callableDeclarations.entrySet()) {
            String methodSig = entry.getKey();
            Callable callable = entry.getValue();
            if (callable.annotations == null)
                continue;
            for (String ann : callable.annotations) {
                if (!ann.contains("@Transactional"))
                    continue;
                String propagation = AnnotationUtils.extractAnnotationField(ann, "propagation");
                propagation = propagation != null ? simpleName(propagation) : "REQUIRED";
                TransactionAttribute ta = new TransactionAttribute();
                ta.setMethod(methodNameFromSig(methodSig));
                ta.setPropagation(propagation);
                ta.setAnnotation(ann);
                txAttrs.add(ta);
            }
        }
        return txAttrs;
    }

    /**
     * Build a Panache repository model.
     * Panache repos implement PanacheRepository&lt;E&gt; or
     * PanacheRepositoryBase&lt;E, ID&gt;.
     * They do not use EntityManager directly and carry no EJB metadata.
     */
    private RepositoryModel buildPanacheRepo(String filePath, String qname, Type jtype,
            String matchedImpl) {
        List<Field> fields = fields(jtype);
        RepositoryModel repo = newRepo(filePath, qname, fields);

        // Extract managed entity from first generic parameter
        CrudSets crud = collectCrud(jtype);
        Matcher gm = GENERIC_FIRST_ARG_STRICT.matcher(matchedImpl);
        if (gm.find()) {
            crud.entities.add(simpleName(gm.group(1)));
        }

        repo.setAutowiredDependencies(collectInjectedDeps(fields));
        // Panache handles transactions via interceptors; report NONE
        repo.setTransactionManagement(buildTxMgmt("NONE", new ArrayList<>()));
        repo.setManagedEntities(new ArrayList<>(crud.entities));
        repo.setOperations(new ArrayList<>(crud.operations));
        repo.setEntityOperations(new LinkedHashMap<>());
        return repo;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /** Null-safe accessor for type annotations. */
    private static List<String> annotations(Type t) {
        return t.annotations != null ? t.annotations : List.of();
    }

    /** Null-safe accessor for type field declarations. */
    private static List<Field> fields(Type t) {
        return t.fieldDeclarations != null ? t.fieldDeclarations : List.of();
    }

    /** Populate the common repo fields shared by all three builder paths. */
    private static RepositoryModel newRepo(String filePath, String qname, List<Field> fields) {
        RepositoryModel repo = new RepositoryModel();
        repo.setOriginalFile(filePath);
        int dot = qname.lastIndexOf('.');
        repo.setPackageName(dot >= 0 ? qname.substring(0, dot) : "");
        repo.setClassName(simpleName(qname));
        repo.setUsesEntityManager(fields.stream().anyMatch(f -> f.type != null && f.type.contains("EntityManager")));
        return repo;
    }

    /** Build a fully initialised TransactionManagementModel. */
    private static TransactionManagementModel buildTxMgmt(String type,
            List<TransactionAttribute> attrs) {
        TransactionManagementModel m = new TransactionManagementModel();
        m.setType(type);
        m.setUserTransaction(null);
        m.setHasManualTransactions(false);
        m.setManualTransactionCalls(new ArrayList<>());
        m.setTransactionAttributes(attrs);
        return m;
    }

    /** Accumulate CRUD operation types and target tables from all callables. */
    private static CrudSets collectCrud(Type jtype) {
        Set<String> ops = new TreeSet<>();
        Set<String> entities = new TreeSet<>();
        if (jtype.callableDeclarations != null) {
            for (Callable c : jtype.callableDeclarations.values()) {
                if (c.crudOperations == null)
                    continue;
                for (CRUDOperation op : c.crudOperations) {
                    if (op.operationType != null)
                        ops.add(op.operationType);
                    if (op.targetTable != null)
                        entities.add(op.targetTable);
                }
            }
        }
        return new CrudSets(ops, entities);
    }

    /** Collect fields annotated with @Autowired, @Inject, or @Resource. */
    private static List<Map<String, Object>> collectInjectedDeps(List<Field> fields) {
        List<Map<String, Object>> deps = new ArrayList<>();
        for (Field f : fields) {
            if (f.annotations == null)
                continue;
            boolean injected = f.annotations.stream()
                    .anyMatch(a -> a.contains("@Autowired") || a.contains("@Inject") || a.contains("@Resource"));
            if (injected && f.variables != null && !f.variables.isEmpty() && f.type != null) {
                Map<String, Object> dep = new LinkedHashMap<>();
                dep.put("name", f.variables.get(0));
                dep.put("type", f.type);
                dep.put("annotations", f.annotations);
                deps.add(dep);
            }
        }
        return deps;
    }

    /** Extract a bare method name from a codeanalyzer callable signature. */
    private static String methodNameFromSig(String sig) {
        String beforeParen = sig.contains("(") ? sig.substring(0, sig.indexOf('(')) : sig;
        return simpleName(beforeParen);
    }

    /**
     * Returns the simple (unqualified) name from a dot-separated fully-qualified
     * name.
     * E.g. {@code "org.example.Order"} -> {@code "Order"}, {@code "Order"} ->
     * {@code "Order"}.
     */
    private static String simpleName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private static String detectTxManagementType(List<String> annotations) {
        for (String ann : annotations) {
            if (ann.contains("@TransactionManagement")) {
                if (ann.contains("BEAN"))
                    return "BEAN";
                if (ann.contains("CONTAINER"))
                    return "CONTAINER";
            }
        }
        return "CONTAINER"; // EJB default
    }

    /** Simple holder for the two sets returned by {@link #collectCrud}. */
    private static class CrudSets {
        final Set<String> operations;
        final Set<String> entities;

        CrudSets(Set<String> operations, Set<String> entities) {
            this.operations = operations;
            this.entities = entities;
        }
    }
}
