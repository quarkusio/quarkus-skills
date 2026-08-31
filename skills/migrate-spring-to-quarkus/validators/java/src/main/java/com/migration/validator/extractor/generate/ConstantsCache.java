package com.migration.validator.extractor.generate;

import com.migration.validator.model.cldk.AnalysisResult;
import com.migration.validator.model.cldk.Field;
import com.migration.validator.model.cldk.JavaCompilationUnit;
import com.migration.validator.model.cldk.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pre-builds a map of "ClassName.CONSTANT_NAME" -> string value by scanning
 * all static final String fields across the AnalysisResult symbol table.
 */
public class ConstantsCache {

    private final Map<String, String> cache;

    private ConstantsCache(Map<String, String> cache) {
        this.cache = cache;
    }

    /** Build the constants cache from a complete AnalysisResult. */
    public static ConstantsCache build(AnalysisResult analysis) {
        Map<String, String> result = new HashMap<>();
        if (analysis == null || analysis.symbolTable == null) {
            return new ConstantsCache(result);
        }
        for (Map.Entry<String, JavaCompilationUnit> entry : analysis.symbolTable.entrySet()) {
            String filePath = entry.getKey();
            JavaCompilationUnit cu = entry.getValue();
            if (cu.typeDeclarations == null)
                continue;

            for (Map.Entry<String, Type> typeEntry : cu.typeDeclarations.entrySet()) {
                String typeName = typeEntry.getKey();
                Type type = typeEntry.getValue();
                if (type.fieldDeclarations == null)
                    continue;

                // Collect static final String fields (non-private)
                for (Field field : type.fieldDeclarations) {
                    if (!isStaticFinalString(field))
                        continue;
                    if (isPrivate(field))
                        continue;
                    if (field.variables == null)
                        continue;

                    // Read the actual file to extract the string literal value
                    for (String varName : field.variables) {
                        String value = extractStringValue(filePath, field, varName);
                        // Key: "SimpleClassName.CONSTANT" or "FQN.CONSTANT"
                        String simpleClass = typeName.contains(".")
                                ? typeName.substring(typeName.lastIndexOf('.') + 1)
                                : typeName;
                        result.put(simpleClass + "." + varName, value);
                        // Also store FQN version
                        if (!simpleClass.equals(typeName)) {
                            result.put(typeName + "." + varName, value);
                        }
                    }
                }
            }
        }
        return new ConstantsCache(result);
    }

    /** Return the backing map for use by annotation resolvers. */
    public Map<String, String> asMap() {
        return cache;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static boolean isStaticFinalString(Field field) {
        if (field.type == null)
            return false;
        if (!field.type.endsWith("String"))
            return false;
        List<String> mods = field.modifiers;
        if (mods == null)
            return false;
        return mods.contains("static") && mods.contains("final");
    }

    private static boolean isPrivate(Field field) {
        return field.modifiers != null && field.modifiers.contains("private");
    }

    private static String extractStringValue(String filePath, Field field, String varName) {
        if (field.startLine == null || field.endLine == null)
            return null;
        try {
            Path p = Paths.get(filePath);
            if (!Files.exists(p))
                return null;
            List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            int start = Math.max(0, field.startLine - 1);
            int end = Math.min(lines.size(), field.endLine);
            StringBuilder decl = new StringBuilder();
            for (int i = start; i < end; i++) {
                decl.append(lines.get(i).trim()).append(" ");
            }
            Pattern pat = Pattern.compile("\\b" + Pattern.quote(varName) + "\\s*=\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
            Matcher m = pat.matcher(decl);
            if (m.find())
                return m.group(1);
        } catch (IOException | IndexOutOfBoundsException e) {
            // ignore
        }
        return null;
    }
}
