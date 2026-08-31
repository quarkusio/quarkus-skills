package com.migration.validator.extractor.generate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static helpers for parsing annotation strings.
 */
public class AnnotationUtils {

    private static final Pattern PRODUCES_PATTERN = Pattern.compile("@Produces\\s*\\((.*?)\\)", Pattern.DOTALL);
    private static final Pattern CONSUMES_PATTERN = Pattern.compile("@Consumes\\s*\\((.*?)\\)", Pattern.DOTALL);
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern CONSTANT_REF = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_.]*\\b)");
    private static final Pattern VALUE_EXTRACT = Pattern.compile("value\\s*=\\s*(\\{.*?\\}|\".*?\"|[^,]+)",
            Pattern.DOTALL);

    /**
     * Extract a named field value from an annotation string.
     * E.g. extractAnnotationField("@Column(name = \"foo\")", "name", null, null) ->
     * "foo"
     */
    public static String extractAnnotationField(
            String annotation, String key,
            Map<String, String> constantsCache, String currentClass) {
        if (annotation == null)
            return null;

        Pattern pattern = Pattern.compile(
                key + "\\s*=\\s*\"?([^\"]+?)\"?(,|\\))", Pattern.DOTALL);
        Matcher m = pattern.matcher(annotation);
        if (!m.find())
            return null;

        String value = m.group(1).trim();

        // Already quoted string literal — already stripped by regex
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }

        // Try to resolve as constant reference
        if (constantsCache != null && !value.startsWith("\"")) {
            String resolved = resolveConstantReference(value, constantsCache, currentClass);
            if (resolved != null)
                return resolved;
        }
        return value;
    }

    public static String extractAnnotationField(String annotation, String key) {
        return extractAnnotationField(annotation, key, null, null);
    }

    /**
     * Extract the first positional (non-named) argument of an annotation.
     * E.g. @GetMapping("/path") -> "/path"
     */
    public static String extractFirstAnnotationArg(String annotation) {
        if (annotation == null)
            return null;

        // String literal as first arg: @Annotation("value", ...)
        Pattern stringFirst = Pattern.compile("\\(\\s*\"([^\"]+)\"(?:\\s*,|\\s*\\))");
        Matcher m = stringFirst.matcher(annotation);
        if (m.find())
            return m.group(1);

        // Constant as first arg (no equals sign after it)
        Pattern constFirst = Pattern.compile("\\(\\s*([a-zA-Z_][a-zA-Z0-9_.]*)[^=]*(?:,|\\))");
        m = constFirst.matcher(annotation);
        if (m.find()) {
            String candidate = m.group(1).trim();
            // Make sure it doesn't look like a named param
            if (!candidate.contains("=")) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Resolve a constant reference (e.g. "MY_CONST" or "MyClass.MY_CONST") from
     * constants cache.
     */
    public static String resolveConstantReference(
            String reference, Map<String, String> constantsCache, String currentClass) {
        if (constantsCache == null || reference == null)
            return null;

        // Qualified reference: "ClassName.CONSTANT"
        if (reference.contains(".")) {
            String direct = constantsCache.get(reference);
            if (direct != null)
                return direct;

            // Try last two parts
            String[] parts = reference.split("\\.");
            if (parts.length >= 2) {
                String shortKey = parts[parts.length - 2] + "." + parts[parts.length - 1];
                for (Map.Entry<String, String> entry : constantsCache.entrySet()) {
                    if (entry.getKey().endsWith("." + shortKey)) {
                        return entry.getValue();
                    }
                }
            }
        } else {
            // Unqualified reference
            if (currentClass != null) {
                String simpleClass = currentClass.contains(".")
                        ? currentClass.substring(currentClass.lastIndexOf('.') + 1)
                        : currentClass;
                String key = simpleClass + "." + reference;
                String val = constantsCache.get(key);
                if (val != null)
                    return val;

                val = constantsCache.get(currentClass + "." + reference);
                if (val != null)
                    return val;
            }
            // Search all constants for a suffix match
            for (Map.Entry<String, String> entry : constantsCache.entrySet()) {
                if (entry.getKey().endsWith("." + reference)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Parse cascade values from a relationship annotation.
     */
    public static List<String> parseCascade(String annotation) {
        String cascade = extractAnnotationField(annotation, "cascade");
        if (cascade == null)
            return null;
        cascade = cascade.replace("{", "").replace("}", "");
        List<String> result = new ArrayList<>();
        for (String c : cascade.split(",")) {
            String trimmed = c.trim();
            int dot = trimmed.lastIndexOf('.');
            result.add(dot >= 0 ? trimmed.substring(dot + 1) : trimmed);
        }
        return result;
    }

    /**
     * Extract media types from @Produces or @Consumes annotation body.
     */
    public static List<String> parseMediaTypes(String annotationBody) {
        if (annotationBody == null)
            return new ArrayList<>();
        // Extract the value= part if present
        Matcher vm = VALUE_EXTRACT.matcher(annotationBody);
        String content = vm.find() ? vm.group(1) : annotationBody;
        content = content.trim();
        if (content.startsWith("{") && content.endsWith("}")) {
            content = content.substring(1, content.length() - 1);
        }

        List<String> values = new ArrayList<>();
        Matcher slm = STRING_LITERAL.matcher(content);
        while (slm.find()) {
            values.add(slm.group(1));
        }
        // Remove string literals from content to find constant refs
        String noStrings = STRING_LITERAL.matcher(content).replaceAll("");
        Matcher cm = CONSTANT_REF.matcher(noStrings);
        while (cm.find()) {
            String c = cm.group(1).trim();
            if (!c.isEmpty())
                values.add(c);
        }
        return values;
    }

    /**
     * Extract @Produces media types from a list of annotation strings.
     */
    public static List<String> extractProduces(List<String> annotations) {
        if (annotations == null)
            return new ArrayList<>();
        for (String ann : annotations) {
            if (ann.contains("@Produces")) {
                Matcher m = PRODUCES_PATTERN.matcher(ann);
                if (m.find())
                    return parseMediaTypes(m.group(1));
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }

    /**
     * Extract @Consumes media types from a list of annotation strings.
     */
    public static List<String> extractConsumes(List<String> annotations) {
        if (annotations == null)
            return new ArrayList<>();
        for (String ann : annotations) {
            if (ann.contains("@Consumes")) {
                Matcher m = CONSUMES_PATTERN.matcher(ann);
                if (m.find())
                    return parseMediaTypes(m.group(1));
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }

    /**
     * Get simple annotation name from a full annotation string.
     * E.g. "@javax.ws.rs.Path(\"/foo\")" -> "Path"
     */
    public static String getAnnotationName(String annotationStr) {
        if (annotationStr == null)
            return null;
        Pattern p = Pattern.compile("@(?:[\\w.]+\\.)?([\\w]+)");
        Matcher m = p.matcher(annotationStr);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Extract values from a named property of a specific annotation.
     * Returns list of string literals and constant references.
     */
    public static List<String> extractAnnotationPropertyValues(
            String annotation, String annotationName, String propertyName,
            Map<String, String> constantsCache, String currentClass) {
        if (annotation == null)
            return new ArrayList<>();
        Pattern bodyPat = Pattern.compile(
                "@" + Pattern.quote(annotationName) + "\\s*\\((.*?)\\)", Pattern.DOTALL);
        Matcher bm = bodyPat.matcher(annotation);
        if (!bm.find())
            return new ArrayList<>();
        String body = bm.group(1);

        Pattern propPat = Pattern.compile(
                propertyName + "\\s*=\\s*(\\{.*?\\}|\".*?\"|[^,]+)", Pattern.DOTALL);
        Matcher pm = propPat.matcher(body);
        if (!pm.find())
            return new ArrayList<>();

        String content = pm.group(1).trim();
        if (content.startsWith("{") && content.endsWith("}")) {
            content = content.substring(1, content.length() - 1);
        }

        List<String> values = new ArrayList<>();
        Matcher slm = STRING_LITERAL.matcher(content);
        while (slm.find())
            values.add(slm.group(1));
        String noStrings = STRING_LITERAL.matcher(content).replaceAll("");
        Matcher cm = CONSTANT_REF.matcher(noStrings);
        while (cm.find()) {
            String c = cm.group(1).trim();
            if (!c.isEmpty()) {
                String resolved = resolveConstantReference(c, constantsCache, currentClass);
                values.add(resolved != null ? resolved : c);
            }
        }
        return values;
    }

    /**
     * Extract mapping paths from a Spring @*Mapping annotation.
     * Tries "value" property, then "path" property, then first positional arg.
     */
    public static List<String> extractMappingPaths(
            String annotation, String annotationName,
            Map<String, String> constantsCache, String currentClass) {
        List<String> values = extractAnnotationPropertyValues(
                annotation, annotationName, "value", constantsCache, currentClass);
        if (!values.isEmpty())
            return values;
        values = extractAnnotationPropertyValues(
                annotation, annotationName, "path", constantsCache, currentClass);
        if (!values.isEmpty())
            return values;
        String first = extractFirstAnnotationArg(annotation);
        if (first == null)
            return Arrays.asList("/");
        // Attempt to resolve a positional constant reference via the cache
        if (constantsCache != null) {
            String resolved = resolveConstantReference(first, constantsCache, currentClass);
            if (resolved != null)
                return Arrays.asList(resolved);
        }
        return Arrays.asList(first);
    }

    /**
     * Detect the value from a named annotation in a list of annotations.
     * Uses extract_annotation_field("value") or first positional arg.
     */
    public static String detectAnnotationValue(
            List<String> annotations, String targetAnnotation,
            Map<String, String> constantsCache, String currentClass) {
        if (annotations == null)
            return null;
        for (String ann : annotations) {
            if (ann.contains(targetAnnotation)) {
                String val = extractAnnotationField(ann, "value", constantsCache, currentClass);
                if (val != null)
                    return val;
                // Positional arg: resolve as a constant reference if the cache is available
                String positional = extractFirstAnnotationArg(ann);
                if (positional != null && constantsCache != null) {
                    String resolved = resolveConstantReference(positional, constantsCache, currentClass);
                    if (resolved != null)
                        return resolved;
                }
                return positional;
            }
        }
        return null;
    }

    /**
     * Detect a property value from a named annotation in a list of annotations.
     * Tries multiple property name alternatives.
     */
    public static String detectAnnotationPropValue(
            List<String> annotations, String targetAnnotation,
            List<String> propertyNameAlternatives,
            Map<String, String> constantsCache, String currentClass) {
        if (annotations == null)
            return null;
        for (String ann : annotations) {
            if (ann.contains(targetAnnotation)) {
                for (String prop : propertyNameAlternatives) {
                    String val = extractAnnotationField(ann, prop, constantsCache, currentClass);
                    if (val != null)
                        return val;
                }
                return extractFirstAnnotationArg(ann);
            }
        }
        return null;
    }

    /**
     * Extract URL patterns from a @WebServlet annotation.
     */
    public static List<String> extractWebServletUrlPatterns(
            String annotation, Map<String, String> constantsCache, String currentClass) {
        if (annotation == null)
            return new ArrayList<>();
        Pattern servletPat = Pattern.compile("@WebServlet\\s*\\((.*?)\\)", Pattern.DOTALL);
        Matcher sm = servletPat.matcher(annotation);
        if (!sm.find())
            return new ArrayList<>();
        String body = sm.group(1);

        List<String> patterns = new ArrayList<>();

        // Case: no named parameter
        if (!body.matches("(?s).*\\b(urlPatterns|value)\\s*=.*")) {
            // String literals
            Matcher slm = STRING_LITERAL.matcher(body);
            while (slm.find())
                patterns.add(slm.group(1));
            if (!patterns.isEmpty())
                return patterns;
            // Constant ref
            Matcher cm = Pattern.compile("\\b([A-Z_][A-Z0-9_]*(?:\\.[A-Z_][A-Z0-9_]*)?)\\b")
                    .matcher(body);
            if (cm.find()) {
                String ref = cm.group(1);
                String resolved = resolveConstantReference(ref, constantsCache, currentClass);
                patterns.add(resolved != null ? resolved : ref);
            }
            return patterns;
        }

        // Array form: urlPatterns = {...}
        Pattern arrayPat = Pattern.compile(
                "\\b(?:urlPatterns|value)\\s*=\\s*\\{(.*?)\\}", Pattern.DOTALL);
        Matcher am = arrayPat.matcher(body);
        if (am.find()) {
            String content = am.group(1);
            Matcher slm = STRING_LITERAL.matcher(content);
            while (slm.find())
                patterns.add(slm.group(1));
            String noStrings = STRING_LITERAL.matcher(content).replaceAll("");
            Matcher cm = Pattern.compile("\\b([A-Z_][A-Z0-9_]*(?:\\.[A-Z_][A-Z0-9_]*)?)\\b")
                    .matcher(noStrings);
            while (cm.find()) {
                String ref = cm.group(1);
                String resolved = resolveConstantReference(ref, constantsCache, currentClass);
                patterns.add(resolved != null ? resolved : ref);
            }
            return patterns;
        }

        // Single string form: urlPatterns = "/path"
        Pattern singleStr = Pattern.compile("\\b(?:urlPatterns|value)\\s*=\\s*\"([^\"]+)\"");
        Matcher ssm = singleStr.matcher(body);
        if (ssm.find()) {
            patterns.add(ssm.group(1));
            return patterns;
        }

        // Single constant form: urlPatterns = CONSTANT
        Pattern singleConst = Pattern.compile(
                "\\b(?:urlPatterns|value)\\s*=\\s*([A-Z_][A-Z0-9_]*(?:\\.[A-Z_][A-Z0-9_]*)?)\\b");
        Matcher scm = singleConst.matcher(body);
        if (scm.find()) {
            String ref = scm.group(1);
            String resolved = resolveConstantReference(ref, constantsCache, currentClass);
            patterns.add(resolved != null ? resolved : ref);
        }
        return patterns;
    }

    /**
     * Normalize a generic type string: e.g. "List<Order>" -> ["List", "Order"]
     * Returns [null, typeStr] if not generic.
     */
    public static String[] normalizeType(String typeStr) {
        if (typeStr != null && typeStr.contains("<") && typeStr.contains(">")) {
            int lt = typeStr.indexOf('<');
            int gt = typeStr.indexOf('>');
            String collection = typeStr.substring(0, lt).contains(".")
                    ? typeStr.substring(0, lt).substring(typeStr.substring(0, lt).lastIndexOf('.') + 1)
                    : typeStr.substring(0, lt);
            String inner = typeStr.substring(lt + 1, gt);
            return new String[] { collection, inner };
        }
        return new String[] { null, typeStr };
    }

    /**
     * Check whether any annotation in the list has @Entity annotation name.
     * Mirrors Python is_entity_annotation() which only matches @Entity,
     * not @Embeddable.
     */
    public static boolean isEntityAnnotation(String ann) {
        String name = getAnnotationName(ann);
        return "Entity".equals(name);
    }

    /**
     * Classify a JPQL/SQL query into SELECT/UPDATE/DELETE/INSERT/UNKNOWN.
     */
    public static String classifyQuery(String query) {
        if (query == null)
            return "UNKNOWN";
        String q = query.strip().toUpperCase();
        if (q.startsWith("SELECT"))
            return "SELECT";
        if (q.startsWith("UPDATE"))
            return "UPDATE";
        if (q.startsWith("DELETE"))
            return "DELETE";
        if (q.startsWith("INSERT"))
            return "INSERT";
        return "UNKNOWN";
    }

    /**
     * Extract named + positional query parameters from JPQL: :param and ?1.
     */
    public static List<String> extractQueryParameters(String query) {
        List<String> params = new ArrayList<>();
        if (query == null)
            return params;
        Matcher nm = Pattern.compile(":(\\w+)").matcher(query);
        while (nm.find())
            params.add(":" + nm.group(1));
        Matcher pm = Pattern.compile("\\?(\\d+)").matcher(query);
        while (pm.find())
            params.add("?" + pm.group(1));
        return params;
    }

    /**
     * Extract @NamedQuery entries from a class annotation string.
     */
    public static List<Map<String, Object>> extractNamedQueries(String annotation) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (annotation == null)
            return results;
        Pattern namedQuery = Pattern.compile("@NamedQuery\\s*\\((.*?)\\)", Pattern.DOTALL);
        Pattern namedNative = Pattern.compile("@NamedNativeQuery\\s*\\((.*?)\\)", Pattern.DOTALL);
        for (Pattern p : Arrays.asList(namedQuery, namedNative)) {
            Matcher m = p.matcher(annotation);
            while (m.find()) {
                String body = m.group(1);
                Matcher nameMatcher = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"").matcher(body);
                Matcher queryMatcher = Pattern.compile("query\\s*=\\s*\"([^\"]+)\"").matcher(body);
                if (!nameMatcher.find() || !queryMatcher.find())
                    continue;
                String name = nameMatcher.group(1);
                String query = queryMatcher.group(1);
                String type = classifyQuery(query);
                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("name", name);
                entry.put("query", query);
                entry.put("type", type);
                entry.put("is_modifying", type.equals("UPDATE") || type.equals("DELETE") || type.equals("INSERT"));
                entry.put("parameters", extractQueryParameters(query));
                results.add(entry);
            }
        }
        return results;
    }

    /**
     * Extract index annotations from a @Table annotation.
     */
    public static List<Map<String, String>> extractIndexes(String annotation) {
        List<Map<String, String>> indexes = new ArrayList<>();
        if (annotation == null)
            return indexes;
        Matcher m = Pattern.compile("@Index\\((.*?)\\)").matcher(annotation);
        while (m.find()) {
            String body = m.group(1);
            Map<String, String> idx = new java.util.LinkedHashMap<>();
            idx.put("name", extractAnnotationField(body, "name"));
            idx.put("columns", extractAnnotationField(body, "columnList"));
            indexes.add(idx);
        }
        return indexes;
    }

    /**
     * Detect soft-delete flag from field names or @Where annotation.
     */
    public static boolean detectSoftDelete(
            List<com.migration.validator.model.cldk.Field> fields,
            List<String> classAnnotations) {
        for (com.migration.validator.model.cldk.Field f : fields) {
            if ("boolean".equalsIgnoreCase(f.type) || "Boolean".equals(f.type)) {
                String name = f.variables != null && !f.variables.isEmpty()
                        ? f.variables.get(0).toLowerCase()
                        : "";
                if (name.equals("deleted") || name.equals("active") || name.equals("enabled")) {
                    return true;
                }
            }
        }
        if (classAnnotations != null) {
            for (String ann : classAnnotations) {
                if (ann.contains("@Where"))
                    return true;
            }
        }
        return false;
    }
}
