package com.migration.validator.extractor.generate;

import com.migration.validator.model.RestModels.ExceptionMapperModel;
import com.migration.validator.model.cldk.AnalysisResult;
import com.migration.validator.model.cldk.Callable;
import com.migration.validator.model.cldk.JavaCompilationUnit;
import com.migration.validator.model.cldk.ParameterInCallable;
import com.migration.validator.model.cldk.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts exception mapper classes from an AnalysisResult.
 *
 * Handles both:
 * - JAX-RS: @Provider + implements ExceptionMapper<T>
 * - Spring: @ControllerAdvice/@RestControllerAdvice + @ExceptionHandler methods
 */
public class ExceptionMapperExtractor {

    private static final Pattern EXCEPTION_HANDLER_TYPES = Pattern.compile(
            "(?:value|exception)\\s*=\\s*(?:\\{([^}]+)\\}|([^,)]+))");
    private static final Pattern SINGLE_CLASS_PAT = Pattern.compile(
            "@ExceptionHandler\\s*\\(\\s*([A-Za-z0-9_.]+)\\.class\\s*\\)");

    /** Extract JAX-RS ExceptionMapper providers. */
    public List<ExceptionMapperModel> extractJaxRs(AnalysisResult analysis) {
        List<ExceptionMapperModel> mappers = new ArrayList<>();
        if (analysis == null || analysis.symbolTable == null)
            return mappers;

        for (JavaCompilationUnit cu : analysis.symbolTable.values()) {
            if (cu.typeDeclarations == null)
                continue;

            for (Map.Entry<String, Type> typeEntry : cu.typeDeclarations.entrySet()) {
                Type jtype = typeEntry.getValue();

                if (!isProvider(jtype))
                    continue;

                // Must implement ExceptionMapper<T> — other @Provider types (filters, readers,
                // …) are not mappers
                String mapperIface = nullSafe(jtype.implementsList).stream()
                        .filter(i -> i.contains("ExceptionMapper"))
                        .findFirst().orElse(null);
                if (mapperIface == null)
                    continue;

                mappers.add(buildModel(
                        simpleName(typeEntry.getKey()),
                        fileName(cu.filePath),
                        typeArgument(mapperIface)));
            }
        }
        return mappers;
    }

    /** Extract Spring @ControllerAdvice/@ExceptionHandler handlers. */
    public List<ExceptionMapperModel> extractSpring(AnalysisResult analysis) {
        List<ExceptionMapperModel> handlers = new ArrayList<>();
        if (analysis == null || analysis.symbolTable == null)
            return handlers;

        for (JavaCompilationUnit cu : analysis.symbolTable.values()) {
            if (cu.typeDeclarations == null)
                continue;

            for (Map.Entry<String, Type> typeEntry : cu.typeDeclarations.entrySet()) {
                Type jtype = typeEntry.getValue();

                if (!isControllerAdvice(jtype) || jtype.callableDeclarations == null)
                    continue;

                String simpleClass = simpleName(typeEntry.getKey());
                String simpleFile = fileName(cu.filePath);

                for (Callable callable : jtype.callableDeclarations.values()) {
                    String exHandlerAnn = nullSafe(callable.annotations).stream()
                            .filter(a -> a.contains("@ExceptionHandler"))
                            .findFirst().orElse(null);
                    if (exHandlerAnn == null)
                        continue;

                    for (String exType : extractExceptionTypes(exHandlerAnn, callable)) {
                        handlers.add(buildModel(simpleClass, simpleFile, exType));
                    }
                }
            }
        }
        return handlers;
    }

    // -------------------------------------------------------------------------
    // Type predicates
    // -------------------------------------------------------------------------

    private static boolean isProvider(Type jtype) {
        return nullSafe(jtype.annotations).stream().anyMatch(a -> a.contains("@Provider"));
    }

    private static boolean isControllerAdvice(Type jtype) {
        return nullSafe(jtype.annotations).stream()
                .anyMatch(a -> a.contains("@ControllerAdvice") || a.contains("@RestControllerAdvice"));
    }

    // -------------------------------------------------------------------------
    // Exception type parsing
    // -------------------------------------------------------------------------

    private List<String> extractExceptionTypes(String exHandlerAnn, Callable callable) {
        List<String> types = new ArrayList<>();

        // Try value/exception = {Type1.class, Type2.class} or value = Type.class
        Matcher m = EXCEPTION_HANDLER_TYPES.matcher(exHandlerAnn);
        if (m.find()) {
            String raw = m.group(1) != null ? m.group(1) : m.group(2);
            for (String t : raw.split(",")) {
                types.add(simpleName(t.trim().replace(".class", "")));
            }
        } else {
            // Single class: @ExceptionHandler(FooException.class)
            Matcher sm = SINGLE_CLASS_PAT.matcher(exHandlerAnn);
            if (sm.find()) {
                types.add(simpleName(sm.group(1)));
            }
        }

        // Fallback: infer from method parameters
        if (types.isEmpty() && callable.parameters != null) {
            for (ParameterInCallable p : callable.parameters) {
                String t = p.type;
                if (isExceptionType(t)) {
                    types.add(simpleName(t));
                    break;
                }
            }
        }
        return types;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static boolean isExceptionType(String type) {
        if (type == null) {
            return false;
        }
        return type.contains("Exception") || type.contains("Error") || type.contains("Throwable");
    }

    private static ExceptionMapperModel buildModel(String className, String file, String exceptionType) {
        ExceptionMapperModel m = new ExceptionMapperModel();
        m.setClassName(className);
        m.setFile(file);
        m.setExceptionType(exceptionType);
        return m;
    }

    /**
     * Returns the last dot-delimited segment of a fully-qualified name, or the
     * value unchanged if no dot.
     */
    private static String simpleName(String fqn) {
        if (fqn == null)
            return null;
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    /**
     * Returns the filename portion of a path (last '/'-delimited segment), or null
     * if path is null.
     */
    private static String fileName(String path) {
        if (path == null)
            return null;
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    /** Returns the list if non-null, otherwise an empty immutable list. */
    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }

    /**
     * Extracts the first generic type argument from a string like
     * {@code ExceptionMapper<FooException>}, or null.
     */
    private static String typeArgument(String generic) {
        int lt = generic.indexOf('<');
        int gt = generic.indexOf('>');
        return (lt >= 0 && gt > lt) ? generic.substring(lt + 1, gt) : null;
    }
}
