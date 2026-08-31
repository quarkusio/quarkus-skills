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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SpringRestApiExtractor}.
 *
 * All tests use synthetic {@link AnalysisResult} fixtures — no real project or
 * external tool is required. A JUnit 5 {@code @TempDir} is passed as
 * {@code projectRoot} so that {@code PropertyUtils.findSpringRestPath} walks an
 * empty directory and returns empty (rootPath == null).
 */
class SpringRestApiExtractorTest {

    @TempDir
    Path projectRoot;

    private SpringRestApiExtractor extractor;
    private static final Map<String, String> NO_CACHE = Map.of();

    @BeforeEach
    void setUp() {
        extractor = new SpringRestApiExtractor();
    }

    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

    /** Build an AnalysisResult with a single CU containing one type. */
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

    /** A Type with @RestController and an optional @RequestMapping class path. */
    private Type restControllerType(String requestMappingPath, Callable... callables) {
        Type t = new Type();
        if (requestMappingPath != null) {
            t.annotations = Arrays.asList("@RestController",
                    "@RequestMapping(\"" + requestMappingPath + "\")");
        } else {
            t.annotations = List.of("@RestController");
        }
        if (callables.length > 0) {
            t.callableDeclarations = new LinkedHashMap<>();
            for (int i = 0; i < callables.length; i++) {
                t.callableDeclarations.put("method" + i, callables[i]);
            }
        }
        return t;
    }

    /** A Type with @Controller. */
    private Type controllerType(String requestMappingPath) {
        Type t = new Type();
        if (requestMappingPath != null) {
            t.annotations = Arrays.asList("@Controller",
                    "@RequestMapping(\"" + requestMappingPath + "\")");
        } else {
            t.annotations = List.of("@Controller");
        }
        return t;
    }

    private Callable callable(String returnType, List<String> annotations,
            ParameterInCallable... params) {
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

    private Field field(String type, String... annotations) {
        Field f = new Field();
        f.type = type;
        f.annotations = annotations.length > 0 ? Arrays.asList(annotations) : null;
        return f;
    }

    // -----------------------------------------------------------------------
    // Null / empty guards
    // -----------------------------------------------------------------------

    @Test
    void extract_nullAnalysis_returnsEmptyApis() {
        RestModel result = extractor.extract(null, projectRoot, NO_CACHE);
        assertNotNull(result);
        assertTrue(result.getApis().isEmpty());
    }

    @Test
    void extract_nullSymbolTable_returnsEmptyApis() {
        AnalysisResult r = new AnalysisResult();
        r.symbolTable = null;
        RestModel result = extractor.extract(r, projectRoot, NO_CACHE);
        assertTrue(result.getApis().isEmpty());
    }

    @Test
    void extract_nullTypeDeclarations_returnsEmptyApis() {
        JavaCompilationUnit cu = new JavaCompilationUnit();
        cu.filePath = "/src/Foo.java";
        cu.typeDeclarations = null;
        AnalysisResult r = new AnalysisResult();
        r.symbolTable = new LinkedHashMap<>();
        r.symbolTable.put(cu.filePath, cu);
        assertTrue(extractor.extract(r, projectRoot, NO_CACHE).getApis().isEmpty());
    }

    // -----------------------------------------------------------------------
    // Controller detection — only @Controller / @RestController processed
    // -----------------------------------------------------------------------

    @Test
    void extract_restController_oneApiReturned() {
        Type t = restControllerType(null);
        RestModel result = extractor.extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE);
        assertEquals(1, result.getApis().size());
    }

    @Test
    void extract_controller_oneApiReturned() {
        Type t = controllerType(null);
        RestModel result = extractor.extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE);
        assertEquals(1, result.getApis().size());
    }

    @Test
    void extract_nonController_ignored() {
        Type t = new Type();
        t.annotations = List.of("@Service");
        RestModel result = extractor.extract(analysis("com.example.FooService", t), projectRoot, NO_CACHE);
        assertTrue(result.getApis().isEmpty());
    }

    @Test
    void extract_noAnnotations_typeIgnored() {
        Type t = new Type();
        t.annotations = null;
        RestModel result = extractor.extract(analysis("com.example.Foo", t), projectRoot, NO_CACHE);
        assertTrue(result.getApis().isEmpty());
    }

    // -----------------------------------------------------------------------
    // RestApi metadata (class-level)
    // -----------------------------------------------------------------------

    @Test
    void extract_simpleClassName_setFromQualifiedName() {
        Type t = restControllerType("/items");
        RestApi api = extractor.extract(analysis("com.example.ItemController", t), projectRoot, NO_CACHE)
                .getApis().get(0);
        assertEquals("ItemController", api.getClassName());
    }

    @Test
    void extract_unqualifiedClassName_setAsIs() {
        Type t = restControllerType(null);
        RestApi api = extractor.extract(analysis("ItemController", t), projectRoot, NO_CACHE)
                .getApis().get(0);
        assertEquals("ItemController", api.getClassName());
    }

    @Test
    void extract_filePath_setOnApi() {
        Type t = restControllerType(null);
        RestApi api = extractor.extract(analysis("com.example.ItemController", t), projectRoot, NO_CACHE)
                .getApis().get(0);
        assertTrue(api.getFile().endsWith("ItemController.java"));
    }

    @Test
    void extract_requestMappingPath_setAsBasePath() {
        Type t = restControllerType("/orders");
        RestApi api = extractor.extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0);
        assertEquals("/orders", api.getPath());
    }

    @Test
    void extract_noRequestMapping_basePathIsEmpty() {
        Type t = restControllerType(null);
        RestApi api = extractor.extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE)
                .getApis().get(0);
        assertEquals("", api.getPath());
    }

    @Test
    void extract_classAnnotations_setOnApi() {
        Type t = restControllerType("/x");
        RestApi api = extractor.extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE)
                .getApis().get(0);
        assertTrue(api.getAnnotations().stream().anyMatch(a -> a.contains("@RestController")));
    }

    @Test
    void extract_classLevelConsumes_setOnApi() {
        Type t = new Type();
        t.annotations = Arrays.asList("@RestController",
                "@RequestMapping(value=\"/x\", consumes=\"application/json\")");
        RestApi api = extractor.extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE)
                .getApis().get(0);
        assertTrue(api.getConsumes().contains("application/json"));
    }

    @Test
    void extract_classLevelProduces_setOnApi() {
        Type t = new Type();
        t.annotations = Arrays.asList("@RestController",
                "@RequestMapping(value=\"/x\", produces=\"application/xml\")");
        RestApi api = extractor.extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE)
                .getApis().get(0);
        assertTrue(api.getProduces().contains("application/xml"));
    }

    // -----------------------------------------------------------------------
    // Field injection
    // -----------------------------------------------------------------------

    @Test
    void extract_autowiredField_addedToInjectedDependencies() {
        Type t = restControllerType(null);
        t.fieldDeclarations = List.of(field("com.example.OrderService", "@Autowired"));
        RestApi api = extractor.extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0);
        assertTrue(api.getInjectedDependencies().contains("OrderService"));
    }

    @Test
    void extract_injectField_addedToInjectedDependencies() {
        Type t = restControllerType(null);
        t.fieldDeclarations = List.of(field("com.example.ItemService", "@Inject"));
        RestApi api = extractor.extract(analysis("com.example.ItemController", t), projectRoot, NO_CACHE)
                .getApis().get(0);
        assertTrue(api.getInjectedDependencies().contains("ItemService"));
    }

    @Test
    void extract_fieldWithNullAnnotations_notAddedToInjections() {
        Type t = restControllerType(null);
        t.fieldDeclarations = List.of(field("com.example.Svc" /* no annotations */));
        RestApi api = extractor.extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE)
                .getApis().get(0);
        assertTrue(api.getInjectedDependencies().isEmpty());
    }

    @Test
    void extract_unqualifiedFieldType_simpleNameUsed() {
        Type t = restControllerType(null);
        t.fieldDeclarations = List.of(field("MyService", "@Autowired"));
        RestApi api = extractor.extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE)
                .getApis().get(0);
        assertTrue(api.getInjectedDependencies().contains("MyService"));
    }

    // -----------------------------------------------------------------------
    // Operations — HTTP method annotations
    // -----------------------------------------------------------------------

    @Test
    void extract_noCallableDeclarations_zeroOperations() {
        Type t = restControllerType("/x");
        t.callableDeclarations = null;
        RestApi api = extractor.extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE)
                .getApis().get(0);
        assertTrue(api.getOperations().isEmpty());
    }

    @Test
    void extract_callableWithoutHttpMethod_notIncluded() {
        Callable c = callable("void", List.of("@Transactional"));
        Type t = restControllerType(null, c);
        RestApi api = extractor.extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE)
                .getApis().get(0);
        assertTrue(api.getOperations().isEmpty());
    }

    @Test
    void extract_getMappingAnnotation_httpMethodIsGET() {
        Callable c = callable("ResponseEntity", List.of("@GetMapping(\"/list\")"));
        Type t = restControllerType("/orders", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertEquals("GET", op.getMethod());
    }

    @Test
    void extract_postMappingAnnotation_httpMethodIsPOST() {
        Callable c = callable("ResponseEntity", List.of("@PostMapping"));
        Type t = restControllerType("/orders", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertEquals("POST", op.getMethod());
    }

    @Test
    void extract_putMappingAnnotation_httpMethodIsPUT() {
        Callable c = callable("ResponseEntity", List.of("@PutMapping(\"/{id}\")"));
        Type t = restControllerType("/orders", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertEquals("PUT", op.getMethod());
    }

    @Test
    void extract_deleteMappingAnnotation_httpMethodIsDELETE() {
        Callable c = callable("void", List.of("@DeleteMapping(\"/{id}\")"));
        Type t = restControllerType("/orders", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertEquals("DELETE", op.getMethod());
    }

    @Test
    void extract_patchMappingAnnotation_httpMethodIsPATCH() {
        Callable c = callable("ResponseEntity", List.of("@PatchMapping(\"/{id}\")"));
        Type t = restControllerType("/orders", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertEquals("PATCH", op.getMethod());
    }

    // -----------------------------------------------------------------------
    // Operations — @RequestMapping with explicit method attribute
    // -----------------------------------------------------------------------

    @Test
    void extract_requestMappingWithMethodGet_httpMethodIsGET() {
        Callable c = callable("ResponseEntity",
                List.of("@RequestMapping(value=\"/list\", method=RequestMethod.GET)"));
        Type t = restControllerType("/orders", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertEquals("GET", op.getMethod());
    }

    @Test
    void extract_requestMappingWithMethodPost_httpMethodIsPOST() {
        Callable c = callable("ResponseEntity",
                List.of("@RequestMapping(value=\"/create\", method=RequestMethod.POST)"));
        Type t = restControllerType("/orders", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertEquals("POST", op.getMethod());
    }

    @Test
    void extract_requestMappingWithNoMethod_defaultsToGET() {
        Callable c = callable("ResponseEntity",
                List.of("@RequestMapping(\"/list\")"));
        Type t = restControllerType("/orders", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertEquals("GET", op.getMethod());
    }

    // -----------------------------------------------------------------------
    // Operations — subpath extraction
    // -----------------------------------------------------------------------

    @Test
    void extract_getMappingWithExplicitPath_subpathSet() {
        Callable c = callable("ResponseEntity", List.of("@GetMapping(\"/active\")"));
        Type t = restControllerType("/orders", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertEquals("/active", op.getSubpath());
    }

    @Test
    void extract_getMappingNoPath_subpathDefaultsToSlash() {
        Callable c = callable("ResponseEntity", List.of("@GetMapping"));
        Type t = restControllerType("/orders", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertEquals("/", op.getSubpath());
    }

    // -----------------------------------------------------------------------
    // Operations — consumes / produces — method-level overrides class-level
    // -----------------------------------------------------------------------

    @Test
    void extract_methodLevelConsumes_overridesClassLevel() {
        Callable c = callable("ResponseEntity",
                List.of("@PostMapping(value=\"/create\", consumes=\"text/plain\")"));
        Type t = new Type();
        t.annotations = Arrays.asList("@RestController",
                "@RequestMapping(value=\"/x\", consumes=\"application/json\")");
        t.callableDeclarations = new LinkedHashMap<>();
        t.callableDeclarations.put("create", c);

        RestOperation op = extractor.extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertTrue(op.getConsumes().contains("text/plain"), "method-level consumes should win");
        assertFalse(op.getConsumes().contains("application/json"));
    }

    @Test
    void extract_methodWithNoConsumes_fallsBackToClassLevel() {
        Callable c = callable("ResponseEntity", List.of("@GetMapping(\"/list\")"));
        Type t = new Type();
        t.annotations = Arrays.asList("@RestController",
                "@RequestMapping(value=\"/x\", consumes=\"application/json\")");
        t.callableDeclarations = new LinkedHashMap<>();
        t.callableDeclarations.put("list", c);

        RestOperation op = extractor.extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertTrue(op.getConsumes().contains("application/json"), "should inherit class-level consumes");
    }

    @Test
    void extract_methodLevelProduces_overridesClassLevel() {
        Callable c = callable("ResponseEntity",
                List.of("@GetMapping(value=\"/list\", produces=\"application/xml\")"));
        Type t = new Type();
        t.annotations = Arrays.asList("@RestController",
                "@RequestMapping(value=\"/x\", produces=\"application/json\")");
        t.callableDeclarations = new LinkedHashMap<>();
        t.callableDeclarations.put("list", c);

        RestOperation op = extractor.extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertTrue(op.getProduces().contains("application/xml"), "method-level produces should win");
        assertFalse(op.getProduces().contains("application/json"));
    }

    // -----------------------------------------------------------------------
    // Operations — return type
    // -----------------------------------------------------------------------

    @Test
    void extract_returnType_setOnOperation() {
        Callable c = callable("org.springframework.http.ResponseEntity", List.of("@GetMapping"));
        Type t = restControllerType("/x", c);
        RestOperation op = extractor.extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertEquals("org.springframework.http.ResponseEntity", op.getResponseType());
    }

    // -----------------------------------------------------------------------
    // Operations — security annotations
    // -----------------------------------------------------------------------

    @Test
    void extract_preAuthorize_securitySet() {
        Callable c = callable("ResponseEntity",
                List.of("@GetMapping", "@PreAuthorize(\"hasRole('ADMIN')\")"));
        Type t = restControllerType("/admin", c);
        RestOperation op = extractor.extract(analysis("com.example.AdminController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertNotNull(op.getSecurity());
        @SuppressWarnings("unchecked")
        Map<String, Object> sec = (Map<String, Object>) op.getSecurity();
        assertEquals("@PreAuthorize", sec.get("annotation"));
    }

    @Test
    void extract_secured_securitySet() {
        Callable c = callable("ResponseEntity",
                List.of("@GetMapping", "@Secured(\"ROLE_ADMIN\")"));
        Type t = restControllerType("/x", c);
        RestOperation op = extractor.extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertNotNull(op.getSecurity());
        @SuppressWarnings("unchecked")
        Map<String, Object> sec = (Map<String, Object>) op.getSecurity();
        assertEquals("@Secured", sec.get("annotation"));
    }

    @Test
    void extract_rolesAllowed_securitySet() {
        Callable c = callable("ResponseEntity",
                List.of("@GetMapping", "@RolesAllowed(\"ADMIN\")"));
        Type t = restControllerType("/x", c);
        RestOperation op = extractor.extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertNotNull(op.getSecurity());
    }

    @Test
    void extract_noSecurityAnnotation_securityIsNull() {
        Callable c = callable("ResponseEntity", List.of("@GetMapping"));
        Type t = restControllerType("/x", c);
        RestOperation op = extractor.extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertNull(op.getSecurity());
    }

    // -----------------------------------------------------------------------
    // Operations — parameters
    // -----------------------------------------------------------------------

    @Test
    void extract_pathVariable_setInPathParams() {
        ParameterInCallable p = param("Long", "id", "@PathVariable(\"id\")");
        Callable c = callable("ResponseEntity", List.of("@GetMapping(\"/{id}\")"), p);
        Type t = restControllerType("/orders", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertEquals(1, op.getPathParams().size());
        assertEquals("id", op.getPathParams().get(0).getName());
        assertEquals("Long", op.getPathParams().get(0).getType());
    }

    @Test
    void extract_requestParam_setInQueryParams() {
        ParameterInCallable p = param("String", "status", "@RequestParam(\"status\")");
        Callable c = callable("ResponseEntity", List.of("@GetMapping"), p);
        Type t = restControllerType("/orders", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertEquals(1, op.getQueryParams().size());
        assertEquals("status", op.getQueryParams().get(0).getName());
    }

    @Test
    void extract_requestParamWithDefaultValue_captured() {
        ParameterInCallable p = param("int", "page",
                "@RequestParam(value=\"page\", defaultValue=\"1\")");
        Callable c = callable("ResponseEntity", List.of("@GetMapping"), p);
        Type t = restControllerType("/orders", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertEquals("1", op.getQueryParams().get(0).getDefaultValue());
    }

    @Test
    void extract_requestBody_setAsRequestType() {
        ParameterInCallable p = param("com.example.OrderRequest", "body", "@RequestBody");
        Callable c = callable("ResponseEntity", List.of("@PostMapping"), p);
        Type t = restControllerType("/orders", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertEquals("com.example.OrderRequest", op.getRequestType());
    }

    @Test
    void extract_noRequestBody_requestTypeIsNull() {
        ParameterInCallable p = param("Long", "id", "@PathVariable(\"id\")");
        Callable c = callable("ResponseEntity", List.of("@GetMapping(\"/{id}\")"), p);
        Type t = restControllerType("/orders", c);
        RestOperation op = extractor.extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertNull(op.getRequestType());
    }

    @Test
    void extract_formParamsListIsAlwaysPresent() {
        Callable c = callable("ResponseEntity", List.of("@PostMapping"));
        Type t = restControllerType("/x", c);
        RestOperation op = extractor.extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations().get(0);
        assertNotNull(op.getFormParams());
        assertTrue(op.getFormParams().isEmpty());
    }

    @Test
    void extract_mixedParams_eachCategorisedCorrectly() {
        ParameterInCallable pathP = param("Long", "id", "@PathVariable(\"id\")");
        ParameterInCallable queryP = param("String", "filter", "@RequestParam(\"filter\")");
        ParameterInCallable body = param("com.example.Payload", "payload", "@RequestBody");

        Callable c = callable("ResponseEntity",
                List.of("@PutMapping(\"/{id}\")"), pathP, queryP, body);
        Type t = restControllerType("/items", c);
        RestOperation op = extractor.extract(analysis("com.example.ItemController", t), projectRoot, NO_CACHE)
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
    void extract_twoMappedMethods_twoOperations() {
        Callable get = callable("ResponseEntity", List.of("@GetMapping"));
        Callable post = callable("ResponseEntity", List.of("@PostMapping"));
        Type t = new Type();
        t.annotations = Arrays.asList("@RestController", "@RequestMapping(\"/orders\")");
        t.callableDeclarations = new LinkedHashMap<>();
        t.callableDeclarations.put("list", get);
        t.callableDeclarations.put("create", post);

        List<RestOperation> ops = extractor
                .extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations();
        assertEquals(2, ops.size());
    }

    @Test
    void extract_twoControllersInOneCU_twoApisReturned() {
        JavaCompilationUnit cu = new JavaCompilationUnit();
        cu.filePath = "/src/Two.java";
        cu.typeDeclarations = new LinkedHashMap<>();

        Type t1 = new Type();
        t1.annotations = Arrays.asList("@RestController", "@RequestMapping(\"/a\")");
        Type t2 = new Type();
        t2.annotations = Arrays.asList("@RestController", "@RequestMapping(\"/b\")");

        cu.typeDeclarations.put("com.example.AController", t1);
        cu.typeDeclarations.put("com.example.BController", t2);

        AnalysisResult r = new AnalysisResult();
        r.symbolTable = new LinkedHashMap<>();
        r.symbolTable.put(cu.filePath, cu);

        RestModel result = extractor.extract(r, projectRoot, NO_CACHE);
        assertEquals(2, result.getApis().size());
    }

    // -----------------------------------------------------------------------
    // Multiple subpaths per method — one RestOperation per path
    // -----------------------------------------------------------------------

    @Test
    void extract_methodWithTwoPaths_twoOperationsCreated() {
        // @GetMapping({"/active", "/enabled"}) — two literal paths in value array
        Callable c = callable("ResponseEntity",
                List.of("@GetMapping(value={\"/active\", \"/enabled\"})"));
        Type t = restControllerType("/orders", c);
        List<RestOperation> ops = extractor
                .extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations();
        assertEquals(2, ops.size());
        List<String> subpaths = ops.stream().map(RestOperation::getSubpath).toList();
        assertTrue(subpaths.contains("/active"));
        assertTrue(subpaths.contains("/enabled"));
    }

    @Test
    void extract_methodWithTwoPaths_sameMethodAndPayloadOnBoth() {
        // Both operations must share the same HTTP method, return type, and request
        // body
        ParameterInCallable body = param("com.example.Payload", "payload", "@RequestBody");
        Callable c = callable("ResponseEntity",
                List.of("@PostMapping(value={\"/a\", \"/b\"})"), body);
        Type t = restControllerType("/x", c);
        List<RestOperation> ops = extractor
                .extract(analysis("com.example.FooController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations();
        assertEquals(2, ops.size());
        for (RestOperation op : ops) {
            assertEquals("POST", op.getMethod());
            assertEquals("com.example.Payload", op.getRequestType());
            assertEquals("ResponseEntity", op.getResponseType());
        }
    }

    @Test
    void extract_requestMappingWithTwoPaths_twoOperationsCreated() {
        // @RequestMapping(value={"/v1/orders","/v2/orders"}, method=RequestMethod.GET)
        Callable c = callable("ResponseEntity",
                List.of("@RequestMapping(value={\"/v1/orders\", \"/v2/orders\"}, method=RequestMethod.GET)"));
        Type t = restControllerType(null, c);
        List<RestOperation> ops = extractor
                .extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations();
        assertEquals(2, ops.size());
        List<String> subpaths = ops.stream().map(RestOperation::getSubpath).toList();
        assertTrue(subpaths.contains("/v1/orders"));
        assertTrue(subpaths.contains("/v2/orders"));
    }

    @Test
    void extract_methodWithPathProperty_twoOperationsCreated() {
        // @GetMapping(path={"/active", "/alive"}) — uses 'path' attribute, not 'value'
        Callable c = callable("ResponseEntity",
                List.of("@GetMapping(path={\"/active\", \"/alive\"})"));
        Type t = restControllerType("/orders", c);
        List<RestOperation> ops = extractor
                .extract(analysis("com.example.OrderController", t), projectRoot, NO_CACHE)
                .getApis().get(0).getOperations();
        assertEquals(2, ops.size());
        List<String> subpaths = ops.stream().map(RestOperation::getSubpath).toList();
        assertTrue(subpaths.contains("/active"));
        assertTrue(subpaths.contains("/alive"));
    }

    // -----------------------------------------------------------------------
    // Constant resolution via constantsCache
    // -----------------------------------------------------------------------

    @Test
    void extract_classRequestMapping_namedValueConstant_resolved() {
        // @RequestMapping(value = ORDERS_PATH) — named attribute form
        Type t = new Type();
        t.annotations = Arrays.asList("@RestController",
                "@RequestMapping(value = ORDERS_PATH)");
        Map<String, String> cache = Map.of("OrderController.ORDERS_PATH", "/orders");
        RestApi api = extractor.extract(analysis("com.example.OrderController", t), projectRoot, cache)
                .getApis().get(0);
        assertEquals("/orders", api.getPath());
    }

    @Test
    void extract_classRequestMapping_positionalConstant_resolved() {
        // @RequestMapping(ORDERS_PATH) — positional (no key=) form, resolved via cache
        Type t = new Type();
        t.annotations = Arrays.asList("@RestController",
                "@RequestMapping(ORDERS_PATH)");
        Map<String, String> cache = Map.of("OrderController.ORDERS_PATH", "/orders");
        RestApi api = extractor.extract(analysis("com.example.OrderController", t), projectRoot, cache)
                .getApis().get(0);
        assertEquals("/orders", api.getPath());
    }

    @Test
    void extract_classRequestMapping_qualifiedConstant_resolved() {
        // @RequestMapping(value = ApiPaths.ORDERS) — qualified constant from another
        // class
        Type t = new Type();
        t.annotations = Arrays.asList("@RestController",
                "@RequestMapping(value = ApiPaths.ORDERS)");
        Map<String, String> cache = Map.of("ApiPaths.ORDERS", "/orders");
        RestApi api = extractor.extract(analysis("com.example.OrderController", t), projectRoot, cache)
                .getApis().get(0);
        assertEquals("/orders", api.getPath());
    }

    @Test
    void extract_methodGetMapping_positionalConstant_subpathResolved() {
        // @GetMapping(LIST_PATH) — positional constant on method annotation
        Callable c = callable("ResponseEntity", List.of("@GetMapping(LIST_PATH)"));
        Type t = restControllerType("/orders", c);
        Map<String, String> cache = Map.of("OrderController.LIST_PATH", "/list");
        RestOperation op = extractor
                .extract(analysis("com.example.OrderController", t), projectRoot, cache)
                .getApis().get(0).getOperations().get(0);
        assertEquals("/list", op.getSubpath());
    }

    @Test
    void extract_methodGetMapping_namedValueConstant_subpathResolved() {
        // @GetMapping(value = LIST_PATH)
        Callable c = callable("ResponseEntity", List.of("@GetMapping(value = LIST_PATH)"));
        Type t = restControllerType("/orders", c);
        Map<String, String> cache = Map.of("OrderController.LIST_PATH", "/list");
        RestOperation op = extractor
                .extract(analysis("com.example.OrderController", t), projectRoot, cache)
                .getApis().get(0).getOperations().get(0);
        assertEquals("/list", op.getSubpath());
    }

    @Test
    void extract_methodGetMapping_pathPropertyConstant_subpathResolved() {
        // @GetMapping(path = LIST_PATH) — uses the 'path' attribute fallback
        Callable c = callable("ResponseEntity", List.of("@GetMapping(path = LIST_PATH)"));
        Type t = restControllerType("/orders", c);
        Map<String, String> cache = Map.of("OrderController.LIST_PATH", "/list");
        RestOperation op = extractor
                .extract(analysis("com.example.OrderController", t), projectRoot, cache)
                .getApis().get(0).getOperations().get(0);
        assertEquals("/list", op.getSubpath());
    }

    @Test
    void extract_pathVariableName_constantResolved() {
        // @PathVariable(name = ID_PARAM) — mapsTo resolved from cache
        ParameterInCallable p = param("Long", "id", "@PathVariable(name = ID_PARAM)");
        Callable c = callable("ResponseEntity", List.of("@GetMapping(\"/{id}\")"), p);
        Type t = restControllerType("/orders", c);
        Map<String, String> cache = Map.of("OrderController.ID_PARAM", "orderId");
        RestOperation op = extractor
                .extract(analysis("com.example.OrderController", t), projectRoot, cache)
                .getApis().get(0).getOperations().get(0);
        assertEquals("orderId", op.getPathParams().get(0).getMapsTo());
    }

    @Test
    void extract_requestParamValue_constantResolved() {
        // @RequestParam(value = STATUS_PARAM) — mapsTo resolved from cache
        ParameterInCallable p = param("String", "status", "@RequestParam(value = STATUS_PARAM)");
        Callable c = callable("ResponseEntity", List.of("@GetMapping"), p);
        Type t = restControllerType("/orders", c);
        Map<String, String> cache = Map.of("OrderController.STATUS_PARAM", "orderStatus");
        RestOperation op = extractor
                .extract(analysis("com.example.OrderController", t), projectRoot, cache)
                .getApis().get(0).getOperations().get(0);
        assertEquals("orderStatus", op.getQueryParams().get(0).getMapsTo());
    }

    // -----------------------------------------------------------------------
    // JAX-RS merge
    // -----------------------------------------------------------------------

    @Test
    void extract_emptyAnalysis_jaxRsApisStillMerged() {
        // With a null analysis the Spring side produces nothing but any JAX-RS
        // results (which also receive null analysis here → empty) should still
        // lead to a non-null, empty apis list.
        RestModel result = extractor.extract(null, projectRoot, NO_CACHE);
        assertNotNull(result.getApis());
    }
}
