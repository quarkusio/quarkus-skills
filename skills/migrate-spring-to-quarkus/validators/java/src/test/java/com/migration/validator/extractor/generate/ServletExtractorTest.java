package com.migration.validator.extractor.generate;

import com.migration.validator.model.RestModels.ServletModel;
import com.migration.validator.model.cldk.AnalysisResult;
import com.migration.validator.model.cldk.Callable;
import com.migration.validator.model.cldk.JavaCompilationUnit;
import com.migration.validator.model.cldk.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ServletExtractor}.
 *
 * All tests use synthetic {@link AnalysisResult} fixtures.
 */
class ServletExtractorTest {

    private ServletExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ServletExtractor();
    }

    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

    /** Build an AnalysisResult that contains a single type declaration. */
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

    /**
     * Build a Type that carries a @WebServlet annotation with a literal URL
     * pattern.
     */
    private Type webServletType(String urlPattern) {
        Type type = new Type();
        type.annotations = List.of("@WebServlet(\"" + urlPattern + "\")");
        return type;
    }

    /** Add a callable declaration (e.g. doGet) to an existing Type. */
    private void addCallable(Type type, String sig) {
        if (type.callableDeclarations == null) {
            type.callableDeclarations = new LinkedHashMap<>();
        }
        type.callableDeclarations.put(sig, new Callable());
    }

    // -----------------------------------------------------------------------
    // Null / empty guard
    // -----------------------------------------------------------------------

    @Nested
    class NullAndEmptyGuards {

        @Test
        void nullAnalysis_returnsEmptyList() {
            assertTrue(extractor.extract(null, Map.of()).isEmpty());
        }

        @Test
        void nullSymbolTable_returnsEmptyList() {
            AnalysisResult analysis = new AnalysisResult();
            analysis.symbolTable = null;
            assertTrue(extractor.extract(analysis, Map.of()).isEmpty());
        }

        @Test
        void typeWithoutWebServletAnnotation_isIgnored() {
            Type type = new Type();
            type.annotations = List.of("@Service");

            assertTrue(extractor.extract(singleTypeAnalysis("com.example.MyService", type), Map.of()).isEmpty());
        }

        @Test
        void typeWithNullAnnotations_isIgnored() {
            Type type = new Type();
            type.annotations = null;

            assertTrue(extractor.extract(singleTypeAnalysis("com.example.AClass", type), Map.of()).isEmpty());
        }

        @Test
        void typeWithNullTypeDeclarations_isSkipped() {
            JavaCompilationUnit cu = new JavaCompilationUnit();
            cu.filePath = "/src/Foo.java";
            cu.typeDeclarations = null; // intentionally null

            AnalysisResult analysis = new AnalysisResult();
            analysis.symbolTable = new LinkedHashMap<>();
            analysis.symbolTable.put(cu.filePath, cu);

            assertTrue(extractor.extract(analysis, Map.of()).isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // Basic extraction
    // -----------------------------------------------------------------------

    @Nested
    class BasicExtraction {

        @Test
        void webServletClass_isExtracted() {
            Type type = webServletType("/orders");

            List<ServletModel> result = extractor.extract(
                    singleTypeAnalysis("com.example.OrderServlet", type), Map.of());

            assertEquals(1, result.size());
        }

        @Test
        void className_isSimpleNameNotFqn() {
            Type type = webServletType("/orders");

            ServletModel model = extractor.extract(
                    singleTypeAnalysis("com.example.OrderServlet", type), Map.of()).get(0);

            assertEquals("OrderServlet", model.getClassName());
        }

        @Test
        void className_defaultPackage_returnedAsIs() {
            Type type = webServletType("/hello");

            ServletModel model = extractor.extract(
                    singleTypeAnalysis("HelloServlet", type), Map.of()).get(0);

            assertEquals("HelloServlet", model.getClassName());
        }

        @Test
        void filePath_isSetFromCompilationUnit() {
            Type type = webServletType("/ping");

            ServletModel model = extractor.extract(
                    singleTypeAnalysis("com.example.PingServlet", type), Map.of()).get(0);

            assertEquals("/src/com/example/PingServlet.java", model.getFile());
        }

        @Test
        void annotations_areCarriedThrough() {
            Type type = new Type();
            type.annotations = List.of("@WebServlet(\"/orders\")", "@SomeOtherAnnotation");

            ServletModel model = extractor.extract(
                    singleTypeAnalysis("com.example.OrderServlet", type), Map.of()).get(0);

            assertEquals(2, model.getAnnotations().size());
            assertTrue(model.getAnnotations().contains("@SomeOtherAnnotation"));
        }

        @Test
        void urlPattern_literalPositionalArg_isExtracted() {
            Type type = webServletType("/orders");

            ServletModel model = extractor.extract(
                    singleTypeAnalysis("com.example.OrderServlet", type), Map.of()).get(0);

            assertTrue(model.getUrlPatterns().contains("/orders"),
                    "URL pattern from positional string literal should be extracted");
        }

        @Test
        void urlPattern_namedValueProperty_isExtracted() {
            Type type = new Type();
            type.annotations = List.of("@WebServlet(value = \"/products\")");

            ServletModel model = extractor.extract(
                    singleTypeAnalysis("com.example.ProductServlet", type), Map.of()).get(0);

            assertTrue(model.getUrlPatterns().contains("/products"));
        }

        @Test
        void urlPattern_namedUrlPatternsArrayProperty_isExtracted() {
            Type type = new Type();
            type.annotations = List.of("@WebServlet(urlPatterns = {\"/a\", \"/b\"})");

            ServletModel model = extractor.extract(
                    singleTypeAnalysis("com.example.MultiServlet", type), Map.of()).get(0);

            assertTrue(model.getUrlPatterns().contains("/a"));
            assertTrue(model.getUrlPatterns().contains("/b"));
        }
    }

    // -----------------------------------------------------------------------
    // HTTP method detection
    // -----------------------------------------------------------------------

    @Nested
    class HttpMethodDetection {

        @Test
        void doGet_addsGetMethod() {
            Type type = webServletType("/items");
            addCallable(type, "doGet(HttpServletRequest, HttpServletResponse)");

            List<String> methods = extractor.extract(
                    singleTypeAnalysis("com.example.ItemServlet", type), Map.of()).get(0).getHttpMethods();

            assertTrue(methods.contains("GET"));
        }

        @Test
        void doPost_addsPostMethod() {
            Type type = webServletType("/items");
            addCallable(type, "doPost(HttpServletRequest, HttpServletResponse)");

            assertTrue(extractor.extract(
                    singleTypeAnalysis("com.example.ItemServlet", type), Map.of())
                    .get(0).getHttpMethods().contains("POST"));
        }

        @Test
        void doPut_addsPutMethod() {
            Type type = webServletType("/items");
            addCallable(type, "doPut(HttpServletRequest, HttpServletResponse)");

            assertTrue(extractor.extract(
                    singleTypeAnalysis("com.example.ItemServlet", type), Map.of())
                    .get(0).getHttpMethods().contains("PUT"));
        }

        @Test
        void doDelete_addsDeleteMethod() {
            Type type = webServletType("/items");
            addCallable(type, "doDelete(HttpServletRequest, HttpServletResponse)");

            assertTrue(extractor.extract(
                    singleTypeAnalysis("com.example.ItemServlet", type), Map.of())
                    .get(0).getHttpMethods().contains("DELETE"));
        }

        @Test
        void multipleDoMethods_allDetected() {
            Type type = webServletType("/items");
            addCallable(type, "doGet(HttpServletRequest, HttpServletResponse)");
            addCallable(type, "doPost(HttpServletRequest, HttpServletResponse)");

            List<String> methods = extractor.extract(
                    singleTypeAnalysis("com.example.ItemServlet", type), Map.of()).get(0).getHttpMethods();

            assertTrue(methods.contains("GET"));
            assertTrue(methods.contains("POST"));
            assertEquals(2, methods.size());
        }

        @Test
        void unrelatedMethod_doesNotAddHttpVerb() {
            Type type = webServletType("/items");
            addCallable(type, "init()");
            addCallable(type, "destroy()");

            List<String> methods = extractor.extract(
                    singleTypeAnalysis("com.example.ItemServlet", type), Map.of()).get(0).getHttpMethods();

            assertTrue(methods.isEmpty());
        }

        @Test
        void fullyQualifiedCallableSig_methodNameStillExtracted() {
            Type type = webServletType("/items");
            // codeanalyzer sometimes emits fully-qualified signatures
            addCallable(type, "com.example.ItemServlet.doGet(HttpServletRequest, HttpServletResponse)");

            List<String> methods = extractor.extract(
                    singleTypeAnalysis("com.example.ItemServlet", type), Map.of()).get(0).getHttpMethods();

            assertTrue(methods.contains("GET"),
                    "Should strip qualifier and still recognise doGet as GET");
        }

        @Test
        void nullCallableDeclarations_yieldsEmptyHttpMethods() {
            Type type = webServletType("/items");
            type.callableDeclarations = null;

            List<String> methods = extractor.extract(
                    singleTypeAnalysis("com.example.ItemServlet", type), Map.of()).get(0).getHttpMethods();

            assertTrue(methods.isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // Multiple servlets in one analysis result
    // -----------------------------------------------------------------------

    @Nested
    class MultipleServlets {

        @Test
        void multipleServlets_allExtracted() {
            JavaCompilationUnit cu1 = new JavaCompilationUnit();
            cu1.filePath = "/src/Servlet1.java";
            cu1.typeDeclarations = new LinkedHashMap<>();
            Type t1 = webServletType("/one");
            cu1.typeDeclarations.put("com.example.Servlet1", t1);

            JavaCompilationUnit cu2 = new JavaCompilationUnit();
            cu2.filePath = "/src/Servlet2.java";
            cu2.typeDeclarations = new LinkedHashMap<>();
            Type t2 = webServletType("/two");
            cu2.typeDeclarations.put("com.example.Servlet2", t2);

            AnalysisResult analysis = new AnalysisResult();
            analysis.symbolTable = new LinkedHashMap<>();
            analysis.symbolTable.put(cu1.filePath, cu1);
            analysis.symbolTable.put(cu2.filePath, cu2);

            List<ServletModel> result = extractor.extract(analysis, Map.of());

            assertEquals(2, result.size());
            assertTrue(result.stream().anyMatch(s -> "Servlet1".equals(s.getClassName())));
            assertTrue(result.stream().anyMatch(s -> "Servlet2".equals(s.getClassName())));
        }
    }

    // -----------------------------------------------------------------------
    // Constant resolution in URL patterns
    // -----------------------------------------------------------------------

    @Nested
    class ConstantResolution {

        // --- positional (no named param) ---

        @Test
        void positional_unqualifiedConstant_resolvedViaCurrentClass() {
            // @WebServlet(MY_PATH) — MY_PATH owned by the servlet class itself
            Type type = new Type();
            type.annotations = List.of("@WebServlet(MY_PATH)");

            // Cache key: SimpleClassName.CONSTANT_NAME
            Map<String, String> cache = Map.of("OrderServlet.MY_PATH", "/orders");

            ServletModel model = extractor.extract(
                    singleTypeAnalysis("com.example.OrderServlet", type), cache).get(0);

            assertEquals(List.of("/orders"), model.getUrlPatterns(),
                    "Unqualified constant in positional arg should be resolved via currentClass");
        }

        @Test
        void positional_unqualifiedConstant_resolvedViaSuffixScan() {
            // @WebServlet(SHARED_PATH) — constant is defined in a different class
            Type type = new Type();
            type.annotations = List.of("@WebServlet(SHARED_PATH)");

            Map<String, String> cache = Map.of("com.example.Constants.SHARED_PATH", "/shared");

            ServletModel model = extractor.extract(
                    singleTypeAnalysis("com.example.FooServlet", type), cache).get(0);

            assertEquals(List.of("/shared"), model.getUrlPatterns(),
                    "Unqualified constant should be found via suffix scan when not owned by currentClass");
        }

        @Test
        void positional_qualifiedConstant_resolvedDirectly() {
            // @WebServlet(Constants.BASE_PATH)
            Type type = new Type();
            type.annotations = List.of("@WebServlet(Constants.BASE_PATH)");

            Map<String, String> cache = Map.of("com.example.Constants.BASE_PATH", "/base");

            ServletModel model = extractor.extract(
                    singleTypeAnalysis("com.example.BaseServlet", type), cache).get(0);

            assertEquals(List.of("/base"), model.getUrlPatterns(),
                    "Qualified ClassName.CONSTANT in positional arg should be resolved directly");
        }

        @Test
        void positional_unknownConstant_rawRefKept() {
            // Constant is not in the cache — should fall back to the raw identifier
            Type type = new Type();
            type.annotations = List.of("@WebServlet(UNKNOWN_PATH)");

            ServletModel model = extractor.extract(
                    singleTypeAnalysis("com.example.FooServlet", type), Map.of()).get(0);

            assertEquals(List.of("UNKNOWN_PATH"), model.getUrlPatterns(),
                    "When constant cannot be resolved, the raw ref should be returned");
        }

        // --- single named property (value = / urlPatterns =) ---

        @Test
        void namedValue_singleConstant_resolved() {
            Type type = new Type();
            type.annotations = List.of("@WebServlet(value = ITEM_URL)");

            Map<String, String> cache = Map.of("com.example.Paths.ITEM_URL", "/items");

            ServletModel model = extractor.extract(
                    singleTypeAnalysis("com.example.ItemServlet", type), cache).get(0);

            assertTrue(model.getUrlPatterns().contains("/items"),
                    "Constant in value = CONSTANT form should be resolved");
        }

        @Test
        void namedUrlPatterns_singleConstant_resolved() {
            Type type = new Type();
            type.annotations = List.of("@WebServlet(urlPatterns = PRODUCT_URL)");

            Map<String, String> cache = Map.of("com.example.Paths.PRODUCT_URL", "/products");

            ServletModel model = extractor.extract(
                    singleTypeAnalysis("com.example.ProductServlet", type), cache).get(0);

            assertTrue(model.getUrlPatterns().contains("/products"),
                    "Constant in urlPatterns = CONSTANT form should be resolved");
        }

        @Test
        void namedValue_unknownConstant_rawRefKept() {
            Type type = new Type();
            type.annotations = List.of("@WebServlet(value = MISSING_URL)");

            ServletModel model = extractor.extract(
                    singleTypeAnalysis("com.example.FooServlet", type), Map.of()).get(0);

            assertTrue(model.getUrlPatterns().contains("MISSING_URL"),
                    "Unresolvable named-value constant should be returned as-is");
        }

        // --- array form: urlPatterns = {literal, CONSTANT} ---

        @Test
        void array_mixedLiteralsAndConstants_bothResolved() {
            Type type = new Type();
            type.annotations = List.of("@WebServlet(urlPatterns = {\"/literal\", ORDER_PATH})");

            Map<String, String> cache = Map.of("com.example.Paths.ORDER_PATH", "/orders");

            ServletModel model = extractor.extract(
                    singleTypeAnalysis("com.example.OrderServlet", type), cache).get(0);

            assertTrue(model.getUrlPatterns().contains("/literal"),
                    "String literal inside array should be kept");
            assertTrue(model.getUrlPatterns().contains("/orders"),
                    "Constant inside array should be resolved from cache");
        }

        @Test
        void array_constantOwnedByCurrentClass_resolved() {
            Type type = new Type();
            type.annotations = List.of("@WebServlet(urlPatterns = {MY_URL})");

            Map<String, String> cache = Map.of("MyServlet.MY_URL", "/mine");

            ServletModel model = extractor.extract(
                    singleTypeAnalysis("com.example.MyServlet", type), cache).get(0);

            assertTrue(model.getUrlPatterns().contains("/mine"),
                    "Constant owned by currentClass should be resolved inside array form");
        }

        @Test
        void array_unknownConstant_rawRefKept() {
            Type type = new Type();
            type.annotations = List.of("@WebServlet(urlPatterns = {NOPE_URL})");

            ServletModel model = extractor.extract(
                    singleTypeAnalysis("com.example.FooServlet", type), Map.of()).get(0);

            assertTrue(model.getUrlPatterns().contains("NOPE_URL"),
                    "Unresolvable constant in array form should be kept as-is");
        }
    }
}
