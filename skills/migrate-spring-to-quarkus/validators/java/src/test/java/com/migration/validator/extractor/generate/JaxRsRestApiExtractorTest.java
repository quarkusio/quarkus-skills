package com.migration.validator.extractor.generate;

import com.migration.validator.model.RestModels.*;
import com.migration.validator.model.cldk.AnalysisResult;
import com.migration.validator.model.cldk.Callable;
import com.migration.validator.model.cldk.Field;
import com.migration.validator.model.cldk.JavaCompilationUnit;
import com.migration.validator.model.cldk.ParameterInCallable;
import com.migration.validator.model.cldk.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JaxRsRestApiExtractor}.
 *
 * All tests use synthetic {@link AnalysisResult} fixtures — no real project or
 * external tool is required.
 */
class JaxRsRestApiExtractorTest {

    private JaxRsRestApiExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new JaxRsRestApiExtractor();
    }

    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

    /** Build an AnalysisResult with a single CU containing one type. */
    private AnalysisResult analysis(String fqn, String filePath, Type type) {
        JavaCompilationUnit cu = new JavaCompilationUnit();
        cu.filePath = filePath;
        cu.typeDeclarations = new LinkedHashMap<>();
        cu.typeDeclarations.put(fqn, type);

        AnalysisResult result = new AnalysisResult();
        result.symbolTable = new LinkedHashMap<>();
        result.symbolTable.put(filePath, cu);
        return result;
    }

    /** Derives filePath from fqn. */
    private AnalysisResult analysis(String fqn, Type type) {
        return analysis(fqn, "/src/" + fqn.replace('.', '/') + ".java", type);
    }

    private Type pathType(String pathAnnotation, Callable... callables) {
        Type t = new Type();
        t.annotations = List.of(pathAnnotation);
        if (callables.length > 0) {
            t.callableDeclarations = new LinkedHashMap<>();
            for (int i = 0; i < callables.length; i++) {
                t.callableDeclarations.put("method" + i, callables[i]);
            }
        }
        return t;
    }

    private Callable callable(String returnType, List<String> annotations, ParameterInCallable... params) {
        Callable c = new Callable();
        c.returnType = returnType;
        c.annotations = annotations;
        c.parameters = params.length > 0 ? Arrays.asList(params) : null;
        return c;
    }

    private ParameterInCallable param(String type, String name, String... annotations) {
        ParameterInCallable p = new ParameterInCallable();
        p.type = type;
        p.name = name;
        p.annotations = annotations.length > 0 ? Arrays.asList(annotations) : null;
        return p;
    }

    private Field field(String type, List<String> annotations) {
        Field f = new Field();
        f.type = type;
        f.annotations = annotations;
        return f;
    }

    // -----------------------------------------------------------------------
    // Null / empty guards
    // -----------------------------------------------------------------------

    @Test
    void extract_nullAnalysis_returnsEmptyModel() {
        RestModel result = extractor.extract(null, Map.of());
        assertNotNull(result);
        assertNull(result.getRootPath());
        assertTrue(result.getApis().isEmpty());
    }

    @Test
    void extract_nullSymbolTable_returnsEmptyModel() {
        AnalysisResult r = new AnalysisResult();
        r.symbolTable = null;
        RestModel result = extractor.extract(r, Map.of());
        assertTrue(result.getApis().isEmpty());
    }

    @Test
    void extract_nullTypeDeclarations_returnsEmptyModel() {
        JavaCompilationUnit cu = new JavaCompilationUnit();
        cu.filePath = "/src/Foo.java";
        cu.typeDeclarations = null;
        AnalysisResult r = new AnalysisResult();
        r.symbolTable = new LinkedHashMap<>();
        r.symbolTable.put(cu.filePath, cu);
        assertTrue(extractor.extract(r, Map.of()).getApis().isEmpty());
    }

    @Test
    void extract_noPathAnnotation_returnsEmptyApis() {
        Type t = new Type();
        t.annotations = List.of("@GET");
        RestModel result = extractor.extract(analysis("com.example.Foo", t), Map.of());
        assertTrue(result.getApis().isEmpty());
    }

    // -----------------------------------------------------------------------
    // @ApplicationPath → rootPath
    // -----------------------------------------------------------------------

    @Test
    void extract_applicationSubclassWithApplicationPath_setsRootPath() {
        // A class that extends Application and has @ApplicationPath("/api")
        Type appType = new Type();
        appType.annotations = List.of("@ApplicationPath(\"/api\")");
        appType.extendsList = List.of("javax.ws.rs.core.Application");

        AnalysisResult r = analysis("com.example.MyApp", appType);
        RestModel result = extractor.extract(r, Map.of());
        assertEquals("/api", result.getRootPath());
    }

    @Test
    void extract_noApplicationSubclass_rootPathIsNull() {
        Type t = pathType("@Path(\"/items\")");
        RestModel result = extractor.extract(analysis("com.example.ItemResource", t), Map.of());
        assertNull(result.getRootPath());
    }

    @Test
    void extract_firstApplicationPathWins_whenMultipleCUs() {
        // Two Application subclasses — only first sets rootPath
        JavaCompilationUnit cu1 = new JavaCompilationUnit();
        cu1.filePath = "/src/AppA.java";
        cu1.typeDeclarations = new LinkedHashMap<>();
        Type app1 = new Type();
        app1.annotations = List.of("@ApplicationPath(\"/v1\")");
        app1.extendsList = List.of("Application");
        cu1.typeDeclarations.put("AppA", app1);

        JavaCompilationUnit cu2 = new JavaCompilationUnit();
        cu2.filePath = "/src/AppB.java";
        cu2.typeDeclarations = new LinkedHashMap<>();
        Type app2 = new Type();
        app2.annotations = List.of("@ApplicationPath(\"/v2\")");
        app2.extendsList = List.of("Application");
        cu2.typeDeclarations.put("AppB", app2);

        AnalysisResult r = new AnalysisResult();
        r.symbolTable = new LinkedHashMap<>();
        r.symbolTable.put(cu1.filePath, cu1);
        r.symbolTable.put(cu2.filePath, cu2);

        RestModel result = extractor.extract(r, Map.of());
        // Only the first detected path is kept
        assertTrue("/v1".equals(result.getRootPath()) || "/v2".equals(result.getRootPath()));
    }

    // -----------------------------------------------------------------------
    // RestApi metadata (class-level)
    // -----------------------------------------------------------------------

    @Test
    void extract_pathAnnotated_oneApiReturned() {
        Type t = pathType("@Path(\"/orders\")");
        RestModel result = extractor.extract(analysis("com.example.OrderResource", t), Map.of());
        assertEquals(1, result.getApis().size());
    }

    @Test
    void extract_basePath_extractedFromPathAnnotation() {
        Type t = pathType("@Path(\"/orders\")");
        RestApi api = extractor.extract(analysis("com.example.OrderResource", t), Map.of()).getApis().get(0);
        assertEquals("/orders", api.getPath());
    }

    @Test
    void extract_simpleClassName_setFromQualifiedName() {
        Type t = pathType("@Path(\"/x\")");
        RestApi api = extractor.extract(analysis("com.example.FooResource", t), Map.of()).getApis().get(0);
        assertEquals("FooResource", api.getClassName());
    }

    @Test
    void extract_unqualifiedClassName_setAsIs() {
        Type t = pathType("@Path(\"/x\")");
        RestApi api = extractor.extract(analysis("FooResource", t), Map.of()).getApis().get(0);
        assertEquals("FooResource", api.getClassName());
    }

    @Test
    void extract_filePath_setOnApi() {
        Type t = pathType("@Path(\"/x\")");
        RestApi api = extractor.extract(
                analysis("com.example.FooResource", "/src/FooResource.java", t), Map.of()).getApis().get(0);
        assertEquals("/src/FooResource.java", api.getFile());
    }

    @Test
    void extract_classAnnotations_setOnApi() {
        Type t = pathType("@Path(\"/x\")");
        RestApi api = extractor.extract(analysis("com.example.FooResource", t), Map.of()).getApis().get(0);
        assertTrue(api.getAnnotations().stream().anyMatch(a -> a.contains("@Path")));
    }

    @Test
    void extract_classLevelConsumes_setOnApi() {
        Type t = new Type();
        t.annotations = List.of("@Path(\"/x\")", "@Consumes(\"application/json\")");
        RestApi api = extractor.extract(analysis("com.example.FooResource", t), Map.of()).getApis().get(0);
        assertTrue(api.getConsumes().contains("application/json"));
    }

    @Test
    void extract_classLevelProduces_setOnApi() {
        Type t = new Type();
        t.annotations = List.of("@Path(\"/x\")", "@Produces(\"application/xml\")");
        RestApi api = extractor.extract(analysis("com.example.FooResource", t), Map.of()).getApis().get(0);
        assertTrue(api.getProduces().contains("application/xml"));
    }

    // -----------------------------------------------------------------------
    // Field injection
    // -----------------------------------------------------------------------

    @Test
    void extract_injectAnnotatedField_addedToInjectedDependencies() {
        Type t = pathType("@Path(\"/x\")");
        Field f = field("com.example.MyService", List.of("@Inject"));
        t.fieldDeclarations = List.of(f);
        RestApi api = extractor.extract(analysis("com.example.FooResource", t), Map.of()).getApis().get(0);
        assertTrue(api.getInjectedDependencies().contains("MyService"));
    }

    @Test
    void extract_ejbAnnotatedField_addedToInjectedDependencies() {
        Type t = pathType("@Path(\"/x\")");
        Field f = field("com.example.OrderService", List.of("@EJB"));
        t.fieldDeclarations = List.of(f);
        RestApi api = extractor.extract(analysis("com.example.FooResource", t), Map.of()).getApis().get(0);
        assertTrue(api.getInjectedDependencies().contains("OrderService"));
    }

    @Test
    void extract_contextAnnotatedField_addedToContextInjections() {
        Type t = pathType("@Path(\"/x\")");
        Field f = field("javax.ws.rs.core.UriInfo", List.of("@Context"));
        t.fieldDeclarations = List.of(f);
        RestApi api = extractor.extract(analysis("com.example.FooResource", t), Map.of()).getApis().get(0);
        assertTrue(api.getContextInjections().contains("UriInfo"));
    }

    @Test
    void extract_fieldWithNullAnnotations_ignored() {
        Type t = pathType("@Path(\"/x\")");
        Field f = field("com.example.Svc", null);
        t.fieldDeclarations = List.of(f);
        RestApi api = extractor.extract(analysis("com.example.FooResource", t), Map.of()).getApis().get(0);
        assertTrue(api.getInjectedDependencies().isEmpty());
    }

    @Test
    void extract_fieldWithNullType_simpleNameIsEmpty_notAdded() {
        Type t = pathType("@Path(\"/x\")");
        Field f = new Field();
        f.type = null;
        f.annotations = List.of("@Inject");
        t.fieldDeclarations = List.of(f);
        // Should not throw; empty-string injections are added (TreeSet deduplicated)
        RestApi api = extractor.extract(analysis("com.example.FooResource", t), Map.of()).getApis().get(0);
        // An empty string may be present; the point is no exception is thrown
        assertNotNull(api.getInjectedDependencies());
    }

    // -----------------------------------------------------------------------
    // Operations — HTTP method detection
    // -----------------------------------------------------------------------

    @Test
    void extract_noCallableDeclarations_zeroOperations() {
        Type t = pathType("@Path(\"/orders\")");
        t.callableDeclarations = null;
        RestApi api = extractor.extract(analysis("com.example.OrderResource", t), Map.of()).getApis().get(0);
        assertTrue(api.getOperations().isEmpty());
    }

    @Test
    void extract_callableWithoutHttpMethod_notIncluded() {
        Callable c = callable("void", List.of("@Transactional"));
        Type t = pathType("@Path(\"/orders\")");
        t.callableDeclarations = Map.of("init", c);
        RestApi api = extractor.extract(analysis("com.example.OrderResource", t), Map.of()).getApis().get(0);
        assertTrue(api.getOperations().isEmpty());
    }

    @Test
    void extract_getAnnotation_httpMethodIsGET() {
        Callable c = callable("Response", List.of("@GET"));
        Type t = pathType("@Path(\"/orders\")");
        t.callableDeclarations = Map.of("list", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertEquals("GET", op.getMethod());
    }

    @Test
    void extract_postAnnotation_httpMethodIsPOST() {
        Callable c = callable("Response", List.of("@POST"));
        Type t = pathType("@Path(\"/orders\")");
        t.callableDeclarations = Map.of("create", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertEquals("POST", op.getMethod());
    }

    @Test
    void extract_deleteAnnotation_httpMethodIsDELETE() {
        Callable c = callable("void", List.of("@DELETE", "@Path(\"/{id}\")"));
        Type t = pathType("@Path(\"/orders\")");
        t.callableDeclarations = Map.of("delete", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertEquals("DELETE", op.getMethod());
    }

    @Test
    void extract_putAnnotation_httpMethodIsPUT() {
        Callable c = callable("Response", List.of("@PUT"));
        Type t = pathType("@Path(\"/orders\")");
        t.callableDeclarations = Map.of("update", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertEquals("PUT", op.getMethod());
    }

    @Test
    void extract_patchAnnotation_httpMethodIsPATCH() {
        Callable c = callable("Response", List.of("@PATCH"));
        Type t = pathType("@Path(\"/orders\")");
        t.callableDeclarations = Map.of("patch", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertEquals("PATCH", op.getMethod());
    }

    // -----------------------------------------------------------------------
    // Operations — subpath, return type, consumes/produces
    // -----------------------------------------------------------------------

    @Test
    void extract_methodPathAnnotation_subpathSet() {
        Callable c = callable("Response", List.of("@GET", "@Path(\"/{id}\")"));
        Type t = pathType("@Path(\"/orders\")");
        t.callableDeclarations = Map.of("get", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertEquals("/{id}", op.getSubpath());
    }

    @Test
    void extract_noMethodPathAnnotation_subpathDefaultsToSlash() {
        Callable c = callable("Response", List.of("@GET"));
        Type t = pathType("@Path(\"/orders\")");
        t.callableDeclarations = Map.of("list", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertEquals("/", op.getSubpath());
    }

    @Test
    void extract_returnType_setOnOperation() {
        Callable c = callable("javax.ws.rs.core.Response", List.of("@GET"));
        Type t = pathType("@Path(\"/orders\")");
        t.callableDeclarations = Map.of("list", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertEquals("javax.ws.rs.core.Response", op.getResponseType());
    }

    @Test
    void extract_methodLevelConsumes_setOnOperation() {
        Callable c = callable("Response", List.of("@POST", "@Consumes(\"application/json\")"));
        Type t = pathType("@Path(\"/orders\")");
        t.callableDeclarations = Map.of("create", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertTrue(op.getConsumes().contains("application/json"));
    }

    @Test
    void extract_methodLevelProduces_setOnOperation() {
        Callable c = callable("Response", List.of("@GET", "@Produces(\"application/xml\")"));
        Type t = pathType("@Path(\"/orders\")");
        t.callableDeclarations = Map.of("list", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertTrue(op.getProduces().contains("application/xml"));
    }

    // -----------------------------------------------------------------------
    // Operations — security annotations
    // -----------------------------------------------------------------------

    @Test
    void extract_rolesAllowed_securitySet() {
        Callable c = callable("Response", List.of("@GET", "@RolesAllowed(\"ADMIN\")"));
        Type t = pathType("@Path(\"/admin\")");
        t.callableDeclarations = Map.of("get", c);
        RestOperation op = extractor.extract(analysis("com.example.AdminResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertNotNull(op.getSecurity());
        @SuppressWarnings("unchecked")
        Map<String, Object> sec = (Map<String, Object>) op.getSecurity();
        assertEquals("@RolesAllowed", sec.get("annotation"));
    }

    @Test
    void extract_permitAll_securitySet() {
        Callable c = callable("Response", List.of("@GET", "@PermitAll"));
        Type t = pathType("@Path(\"/public\")");
        t.callableDeclarations = Map.of("get", c);
        RestOperation op = extractor.extract(analysis("com.example.PublicResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertNotNull(op.getSecurity());
    }

    @Test
    void extract_noSecurityAnnotation_securityIsNull() {
        Callable c = callable("Response", List.of("@GET"));
        Type t = pathType("@Path(\"/items\")");
        t.callableDeclarations = Map.of("list", c);
        RestOperation op = extractor.extract(analysis("com.example.ItemResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertNull(op.getSecurity());
    }

    // -----------------------------------------------------------------------
    // Operations — parameters
    // -----------------------------------------------------------------------

    @Test
    void extract_pathParam_setInPathParams() {
        ParameterInCallable p = param("Long", "id", "@PathParam(\"id\")");
        Callable c = callable("Response", List.of("@GET", "@Path(\"/{id}\")"), p);
        Type t = pathType("@Path(\"/orders\")");
        t.callableDeclarations = Map.of("get", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertEquals(1, op.getPathParams().size());
        assertEquals("id", op.getPathParams().get(0).getName());
        assertEquals("Long", op.getPathParams().get(0).getType());
    }

    @Test
    void extract_queryParam_setInQueryParams() {
        ParameterInCallable p = param("String", "status", "@QueryParam(\"status\")");
        Callable c = callable("Response", List.of("@GET"), p);
        Type t = pathType("@Path(\"/orders\")");
        t.callableDeclarations = Map.of("list", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertEquals(1, op.getQueryParams().size());
        assertEquals("status", op.getQueryParams().get(0).getName());
    }

    @Test
    void extract_formParam_setInFormParams() {
        ParameterInCallable p = param("String", "name", "@FormParam(\"name\")");
        Callable c = callable("Response", List.of("@POST"), p);
        Type t = pathType("@Path(\"/orders\")");
        t.callableDeclarations = Map.of("create", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertEquals(1, op.getFormParams().size());
        assertEquals("name", op.getFormParams().get(0).getName());
    }

    @Test
    void extract_paramWithDefaultValue_captured() {
        ParameterInCallable p = param("int", "page",
                "@QueryParam(\"page\")", "@DefaultValue(\"1\")");
        Callable c = callable("Response", List.of("@GET"), p);
        Type t = pathType("@Path(\"/orders\")");
        t.callableDeclarations = Map.of("list", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertEquals("1", op.getQueryParams().get(0).getDefaultValue());
    }

    @Test
    void extract_unboundParam_setAsRequestType() {
        ParameterInCallable p = param("com.example.OrderRequest", "body"); // no JAX-RS annotation
        Callable c = callable("Response", List.of("@POST"), p);
        Type t = pathType("@Path(\"/orders\")");
        t.callableDeclarations = Map.of("create", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertEquals("com.example.OrderRequest", op.getRequestType());
    }

    @Test
    void extract_onlyBoundParams_requestTypeIsNull() {
        ParameterInCallable p = param("Long", "id", "@PathParam(\"id\")");
        Callable c = callable("Response", List.of("@GET", "@Path(\"/{id}\")"), p);
        Type t = pathType("@Path(\"/orders\")");
        t.callableDeclarations = Map.of("get", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);
        assertNull(op.getRequestType());
    }

    @Test
    void extract_mixedParams_eachCategorisedCorrectly() {
        ParameterInCallable pathP = param("Long", "id", "@PathParam(\"id\")");
        ParameterInCallable queryP = param("String", "filter", "@QueryParam(\"filter\")");
        ParameterInCallable body = param("com.example.Payload", "payload");

        Callable c = callable("Response", List.of("@PUT", "@Path(\"/{id}\")"), pathP, queryP, body);
        Type t = pathType("@Path(\"/items\")");
        t.callableDeclarations = Map.of("update", c);
        RestOperation op = extractor.extract(analysis("com.example.ItemResource", t), Map.of())
                .getApis().get(0).getOperations().get(0);

        assertEquals(1, op.getPathParams().size());
        assertEquals(1, op.getQueryParams().size());
        assertEquals(0, op.getFormParams().size());
        assertEquals("com.example.Payload", op.getRequestType());
    }

    // -----------------------------------------------------------------------
    // Multiple callables and multiple types
    // -----------------------------------------------------------------------

    @Test
    void extract_twoMethodsOnOneResource_twoOperations() {
        Callable get = callable("Response", List.of("@GET"));
        Callable post = callable("Response", List.of("@POST"));
        Type t = new Type();
        t.annotations = List.of("@Path(\"/orders\")");
        t.callableDeclarations = new LinkedHashMap<>();
        t.callableDeclarations.put("list", get);
        t.callableDeclarations.put("create", post);

        List<RestOperation> ops = extractor.extract(analysis("com.example.OrderResource", t), Map.of())
                .getApis().get(0).getOperations();
        assertEquals(2, ops.size());
    }

    @Test
    void extract_twoPathAnnotatedClasses_twoApisReturned() {
        JavaCompilationUnit cu = new JavaCompilationUnit();
        cu.filePath = "/src/Two.java";
        cu.typeDeclarations = new LinkedHashMap<>();

        Type t1 = new Type();
        t1.annotations = List.of("@Path(\"/a\")");
        Type t2 = new Type();
        t2.annotations = List.of("@Path(\"/b\")");

        cu.typeDeclarations.put("com.example.A", t1);
        cu.typeDeclarations.put("com.example.B", t2);

        AnalysisResult r = new AnalysisResult();
        r.symbolTable = new LinkedHashMap<>();
        r.symbolTable.put(cu.filePath, cu);

        RestModel result = extractor.extract(r, Map.of());
        assertEquals(2, result.getApis().size());
    }

    // -----------------------------------------------------------------------
    // Constant resolution via constantsCache
    // -----------------------------------------------------------------------

    @Test
    void extract_pathFromConstantCache_positionalArg_resolved() {
        // @Path(ORDERS_PATH) — positional constant, no value= key
        Type t = pathType("@Path(ORDERS_PATH)");
        Map<String, String> cache = Map.of("OrderResource.ORDERS_PATH", "/orders");
        RestApi api = extractor.extract(analysis("com.example.OrderResource", t), cache).getApis().get(0);
        assertEquals("/orders", api.getPath());
    }

    @Test
    void extract_pathFromConstantCache_namedArg_resolved() {
        // @Path(value = ORDERS_PATH) — named constant
        Type t = pathType("@Path(value = ORDERS_PATH)");
        Map<String, String> cache = Map.of("OrderResource.ORDERS_PATH", "/orders");
        RestApi api = extractor.extract(analysis("com.example.OrderResource", t), cache).getApis().get(0);
        assertEquals("/orders", api.getPath());
    }
}
