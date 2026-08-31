package com.migration.validator.extractor.generate;

import com.migration.validator.model.RestModels.ExceptionMapperModel;
import com.migration.validator.model.cldk.AnalysisResult;
import com.migration.validator.model.cldk.Callable;
import com.migration.validator.model.cldk.JavaCompilationUnit;
import com.migration.validator.model.cldk.ParameterInCallable;
import com.migration.validator.model.cldk.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ExceptionMapperExtractor}.
 *
 * All tests use synthetic {@link AnalysisResult} fixtures — no real project or
 * external tool is required.
 */
class ExceptionMapperExtractorTest {

    private ExceptionMapperExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ExceptionMapperExtractor();
    }

    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

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

    /** Convenience: derives filePath from fqn. */
    private AnalysisResult analysis(String fqn, Type type) {
        return analysis(fqn, "/src/" + fqn.replace('.', '/') + ".java", type);
    }

    private Type typeWithAnnotations(String... annotations) {
        Type t = new Type();
        t.annotations = Arrays.asList(annotations);
        return t;
    }

    private Type typeWithAnnotationsAndIfaces(List<String> annotations, List<String> ifaces) {
        Type t = new Type();
        t.annotations = annotations;
        t.implementsList = ifaces;
        return t;
    }

    private Callable callable(List<String> annotations, ParameterInCallable... params) {
        Callable c = new Callable();
        c.annotations = annotations;
        c.parameters = params.length > 0 ? Arrays.asList(params) : null;
        return c;
    }

    private ParameterInCallable param(String type) {
        ParameterInCallable p = new ParameterInCallable();
        p.type = type;
        return p;
    }

    private Type adviceTypeWithMethod(String adviceAnnotation, Callable method) {
        Type t = new Type();
        t.annotations = List.of(adviceAnnotation);
        t.callableDeclarations = new LinkedHashMap<>();
        t.callableDeclarations.put("handleMethod", method);
        return t;
    }

    // -----------------------------------------------------------------------
    // extractJaxRs — null / empty guards
    // -----------------------------------------------------------------------

    @Test
    void extractJaxRs_nullAnalysis_returnsEmpty() {
        assertTrue(extractor.extractJaxRs(null).isEmpty());
    }

    @Test
    void extractJaxRs_nullSymbolTable_returnsEmpty() {
        AnalysisResult r = new AnalysisResult();
        r.symbolTable = null;
        assertTrue(extractor.extractJaxRs(r).isEmpty());
    }

    @Test
    void extractJaxRs_nullTypeDeclarations_returnsEmpty() {
        JavaCompilationUnit cu = new JavaCompilationUnit();
        cu.filePath = "/src/Foo.java";
        cu.typeDeclarations = null;
        AnalysisResult r = new AnalysisResult();
        r.symbolTable = new LinkedHashMap<>();
        r.symbolTable.put(cu.filePath, cu);
        assertTrue(extractor.extractJaxRs(r).isEmpty());
    }

    // -----------------------------------------------------------------------
    // extractJaxRs — @Provider detection
    // -----------------------------------------------------------------------

    @Test
    void extractJaxRs_withProviderAndExceptionMapper_detected() {
        Type t = typeWithAnnotationsAndIfaces(
                List.of("@Provider"),
                List.of("ExceptionMapper<IllegalArgumentException>"));
        List<ExceptionMapperModel> result = extractor.extractJaxRs(analysis("com.example.MyMapper", t));
        assertEquals(1, result.size());
    }

    @Test
    void extractJaxRs_withoutProvider_notDetected() {
        Type t = typeWithAnnotationsAndIfaces(
                List.of("@SomeOtherAnnotation"),
                List.of("ExceptionMapper<IllegalArgumentException>"));
        assertTrue(extractor.extractJaxRs(analysis("com.example.MyMapper", t)).isEmpty());
    }

    @Test
    void extractJaxRs_nullAnnotations_notDetected() {
        Type t = new Type();
        t.annotations = null;
        t.implementsList = List.of("ExceptionMapper<RuntimeException>");
        assertTrue(extractor.extractJaxRs(analysis("com.example.MyMapper", t)).isEmpty());
    }

    // -----------------------------------------------------------------------
    // extractJaxRs — ExceptionMapper type argument extraction
    // -----------------------------------------------------------------------

    @Test
    void extractJaxRs_exceptionTypeExtractedFromGeneric() {
        Type t = typeWithAnnotationsAndIfaces(
                List.of("@Provider"),
                List.of("ExceptionMapper<NotFoundException>"));
        ExceptionMapperModel m = extractor.extractJaxRs(analysis("com.example.MyMapper", t)).get(0);
        assertEquals("NotFoundException", m.getExceptionType());
    }

    @Test
    void extractJaxRs_fullyQualifiedGenericTypeArg_extractedAsIs() {
        // The raw generic arg is whatever sits between < >, no further trimming in
        // JAX-RS
        Type t = typeWithAnnotationsAndIfaces(
                List.of("@Provider"),
                List.of("ExceptionMapper<javax.ws.rs.NotFoundException>"));
        ExceptionMapperModel m = extractor.extractJaxRs(analysis("com.example.MyMapper", t)).get(0);
        assertEquals("javax.ws.rs.NotFoundException", m.getExceptionType());
    }

    @Test
    void extractJaxRs_noExceptionMapperInIfaces_notDetected() {
        // @Provider alone (e.g. a MessageBodyReader) is not an exception mapper
        Type t = typeWithAnnotationsAndIfaces(
                List.of("@Provider"),
                List.of("Serializable"));
        assertTrue(extractor.extractJaxRs(analysis("com.example.MyMapper", t)).isEmpty());
    }

    @Test
    void extractJaxRs_nullImplementsList_notDetected() {
        // @Provider with no implements list cannot be an ExceptionMapper
        Type t = new Type();
        t.annotations = List.of("@Provider");
        t.implementsList = null;
        assertTrue(extractor.extractJaxRs(analysis("com.example.MyMapper", t)).isEmpty());
    }

    @Test
    void extractJaxRs_exceptionMapperWithoutGeneric_exceptionTypeNull() {
        Type t = typeWithAnnotationsAndIfaces(
                List.of("@Provider"),
                List.of("ExceptionMapper")); // no <T>
        ExceptionMapperModel m = extractor.extractJaxRs(analysis("com.example.MyMapper", t)).get(0);
        assertNull(m.getExceptionType());
    }

    // -----------------------------------------------------------------------
    // extractJaxRs — class name and file name
    // -----------------------------------------------------------------------

    @Test
    void extractJaxRs_qualifiedName_simpleClassNameSet() {
        Type t = typeWithAnnotationsAndIfaces(List.of("@Provider"), List.of("ExceptionMapper<RuntimeException>"));
        ExceptionMapperModel m = extractor.extractJaxRs(analysis("com.example.MyMapper", t)).get(0);
        assertEquals("MyMapper", m.getClassName());
    }

    @Test
    void extractJaxRs_unqualifiedName_classNameUnchanged() {
        Type t = typeWithAnnotationsAndIfaces(List.of("@Provider"), List.of("ExceptionMapper<RuntimeException>"));
        ExceptionMapperModel m = extractor.extractJaxRs(analysis("MyMapper", t)).get(0);
        assertEquals("MyMapper", m.getClassName());
    }

    @Test
    void extractJaxRs_filePath_simpleFileNameSet() {
        Type t = typeWithAnnotationsAndIfaces(List.of("@Provider"), List.of("ExceptionMapper<RuntimeException>"));
        ExceptionMapperModel m = extractor.extractJaxRs(
                analysis("com.example.MyMapper", "/src/com/example/MyMapper.java", t)).get(0);
        assertEquals("MyMapper.java", m.getFile());
    }

    @Test
    void extractJaxRs_nullFilePath_fileIsNull() {
        Type t = typeWithAnnotationsAndIfaces(List.of("@Provider"), List.of("ExceptionMapper<RuntimeException>"));
        ExceptionMapperModel m = extractor.extractJaxRs(analysis("com.example.MyMapper", null, t)).get(0);
        assertNull(m.getFile());
    }

    // -----------------------------------------------------------------------
    // extractSpring — null / empty guards
    // -----------------------------------------------------------------------

    @Test
    void extractSpring_nullAnalysis_returnsEmpty() {
        assertTrue(extractor.extractSpring(null).isEmpty());
    }

    @Test
    void extractSpring_nullSymbolTable_returnsEmpty() {
        AnalysisResult r = new AnalysisResult();
        r.symbolTable = null;
        assertTrue(extractor.extractSpring(r).isEmpty());
    }

    @Test
    void extractSpring_nullTypeDeclarations_returnsEmpty() {
        JavaCompilationUnit cu = new JavaCompilationUnit();
        cu.filePath = "/src/Foo.java";
        cu.typeDeclarations = null;
        AnalysisResult r = new AnalysisResult();
        r.symbolTable = new LinkedHashMap<>();
        r.symbolTable.put(cu.filePath, cu);
        assertTrue(extractor.extractSpring(r).isEmpty());
    }

    // -----------------------------------------------------------------------
    // extractSpring — @ControllerAdvice / @RestControllerAdvice detection
    // -----------------------------------------------------------------------

    @Test
    void extractSpring_controllerAdvice_detected() {
        Callable m = callable(
                List.of("@ExceptionHandler(IllegalArgumentException.class)"),
                param("IllegalArgumentException"));
        Type t = adviceTypeWithMethod("@ControllerAdvice", m);
        assertEquals(1, extractor.extractSpring(analysis("com.example.Handler", t)).size());
    }

    @Test
    void extractSpring_restControllerAdvice_detected() {
        Callable m = callable(
                List.of("@ExceptionHandler(RuntimeException.class)"),
                param("RuntimeException"));
        Type t = adviceTypeWithMethod("@RestControllerAdvice", m);
        assertEquals(1, extractor.extractSpring(analysis("com.example.Handler", t)).size());
    }

    @Test
    void extractSpring_noAdviceAnnotation_notDetected() {
        Callable m = callable(List.of("@ExceptionHandler(RuntimeException.class)"));
        Type t = adviceTypeWithMethod("@Service", m);
        assertTrue(extractor.extractSpring(analysis("com.example.Handler", t)).isEmpty());
    }

    @Test
    void extractSpring_nullAnnotations_notDetected() {
        Callable m = callable(List.of("@ExceptionHandler(RuntimeException.class)"));
        Type t = new Type();
        t.annotations = null;
        t.callableDeclarations = new LinkedHashMap<>();
        t.callableDeclarations.put("handle", m);
        assertTrue(extractor.extractSpring(analysis("com.example.Handler", t)).isEmpty());
    }

    @Test
    void extractSpring_adviceWithNullCallableDeclarations_returnsEmpty() {
        Type t = typeWithAnnotations("@ControllerAdvice");
        t.callableDeclarations = null;
        assertTrue(extractor.extractSpring(analysis("com.example.Handler", t)).isEmpty());
    }

    // -----------------------------------------------------------------------
    // extractSpring — @ExceptionHandler with value/exception attribute
    // -----------------------------------------------------------------------

    @Test
    void extractSpring_exceptionHandlerWithValueSingle_extractsType() {
        Callable m = callable(List.of("@ExceptionHandler(value = IllegalArgumentException.class)"));
        Type t = adviceTypeWithMethod("@ControllerAdvice", m);
        List<ExceptionMapperModel> result = extractor.extractSpring(analysis("com.example.Handler", t));
        assertEquals(1, result.size());
        assertEquals("IllegalArgumentException", result.get(0).getExceptionType());
    }

    @Test
    void extractSpring_exceptionHandlerWithValueArray_eachTypeProducesModel() {
        Callable m = callable(List.of(
                "@ExceptionHandler(value = {IllegalArgumentException.class, IllegalStateException.class})"));
        Type t = adviceTypeWithMethod("@ControllerAdvice", m);
        List<ExceptionMapperModel> result = extractor.extractSpring(analysis("com.example.Handler", t));
        assertEquals(2, result.size());
        assertEquals("IllegalArgumentException", result.get(0).getExceptionType());
        assertEquals("IllegalStateException", result.get(1).getExceptionType());
    }

    @Test
    void extractSpring_exceptionHandlerSimpleSyntax_extractsType() {
        // @ExceptionHandler(FooException.class) — no value= key
        Callable m = callable(List.of("@ExceptionHandler(FooException.class)"));
        Type t = adviceTypeWithMethod("@ControllerAdvice", m);
        List<ExceptionMapperModel> result = extractor.extractSpring(analysis("com.example.Handler", t));
        assertEquals(1, result.size());
        assertEquals("FooException", result.get(0).getExceptionType());
    }

    @Test
    void extractSpring_exceptionHandlerFqnInSimpleSyntax_simpleNameUsed() {
        Callable m = callable(List.of("@ExceptionHandler(com.example.FooException.class)"));
        Type t = adviceTypeWithMethod("@ControllerAdvice", m);
        List<ExceptionMapperModel> result = extractor.extractSpring(analysis("com.example.Handler", t));
        assertEquals(1, result.size());
        assertEquals("FooException", result.get(0).getExceptionType());
    }

    // -----------------------------------------------------------------------
    // extractSpring — fallback to method parameter
    // -----------------------------------------------------------------------

    @Test
    void extractSpring_noHandlerValue_fallsBackToExceptionParam() {
        // @ExceptionHandler with no parseable type, but a parameter of exception type
        Callable m = callable(
                List.of("@ExceptionHandler"),
                param("RuntimeException"));
        Type t = adviceTypeWithMethod("@ControllerAdvice", m);
        List<ExceptionMapperModel> result = extractor.extractSpring(analysis("com.example.Handler", t));
        assertEquals(1, result.size());
        assertEquals("RuntimeException", result.get(0).getExceptionType());
    }

    @Test
    void extractSpring_fallbackPicksFirstExceptionParam() {
        Callable m = callable(
                List.of("@ExceptionHandler"),
                param("String"), // ignored — not an exception type
                param("IllegalStateException"),
                param("RuntimeException")); // not reached — stops at first match
        Type t = adviceTypeWithMethod("@ControllerAdvice", m);
        List<ExceptionMapperModel> result = extractor.extractSpring(analysis("com.example.Handler", t));
        assertEquals(1, result.size());
        assertEquals("IllegalStateException", result.get(0).getExceptionType());
    }

    @Test
    void extractSpring_noHandlerValueNullParams_noModelEmitted() {
        // @ExceptionHandler with no parseable type and no parameters — should produce
        // no models
        Callable m = callable(List.of("@ExceptionHandler"));
        // parameters left null by the callable() helper
        Type t = adviceTypeWithMethod("@ControllerAdvice", m);
        List<ExceptionMapperModel> result = extractor.extractSpring(analysis("com.example.Handler", t));
        assertTrue(result.isEmpty());
    }

    // -----------------------------------------------------------------------
    // extractSpring — class/file metadata
    // -----------------------------------------------------------------------

    @Test
    void extractSpring_qualifiedName_simpleClassNameSet() {
        Callable m = callable(List.of("@ExceptionHandler(RuntimeException.class)"));
        Type t = adviceTypeWithMethod("@ControllerAdvice", m);
        ExceptionMapperModel model = extractor.extractSpring(
                analysis("com.example.GlobalHandler", t)).get(0);
        assertEquals("GlobalHandler", model.getClassName());
    }

    @Test
    void extractSpring_filePath_simpleFileNameSet() {
        Callable m = callable(List.of("@ExceptionHandler(RuntimeException.class)"));
        Type t = adviceTypeWithMethod("@ControllerAdvice", m);
        ExceptionMapperModel model = extractor.extractSpring(
                analysis("com.example.GlobalHandler", "/src/com/example/GlobalHandler.java", t)).get(0);
        assertEquals("GlobalHandler.java", model.getFile());
    }

    @Test
    void extractSpring_nullFilePath_fileIsNull() {
        Callable m = callable(List.of("@ExceptionHandler(RuntimeException.class)"));
        Type t = adviceTypeWithMethod("@ControllerAdvice", m);
        ExceptionMapperModel model = extractor.extractSpring(
                analysis("com.example.GlobalHandler", null, t)).get(0);
        assertNull(model.getFile());
    }

    // -----------------------------------------------------------------------
    // extractSpring — multiple methods / exception types
    // -----------------------------------------------------------------------

    @Test
    void extractSpring_twoHandlerMethods_twoModelsReturned() {
        Type t = typeWithAnnotations("@ControllerAdvice");
        t.callableDeclarations = new LinkedHashMap<>();
        t.callableDeclarations.put("handleArg",
                callable(List.of("@ExceptionHandler(IllegalArgumentException.class)")));
        t.callableDeclarations.put("handleState",
                callable(List.of("@ExceptionHandler(IllegalStateException.class)")));

        List<ExceptionMapperModel> result = extractor.extractSpring(analysis("com.example.Handler", t));
        assertEquals(2, result.size());
    }

    @Test
    void extractSpring_methodWithoutExceptionHandler_ignored() {
        Type t = typeWithAnnotations("@ControllerAdvice");
        t.callableDeclarations = new LinkedHashMap<>();
        t.callableDeclarations.put("handleArg",
                callable(List.of("@ExceptionHandler(IllegalArgumentException.class)")));
        t.callableDeclarations.put("ordinaryMethod",
                callable(List.of("@GetMapping")));

        List<ExceptionMapperModel> result = extractor.extractSpring(analysis("com.example.Handler", t));
        assertEquals(1, result.size());
    }
}
