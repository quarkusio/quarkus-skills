package com.migration.validator.extractor.generate;

import com.migration.validator.model.PersistenceModels.EntityManagerUsageModel;
import com.migration.validator.model.cldk.AnalysisResult;
import com.migration.validator.model.cldk.Field;
import com.migration.validator.model.cldk.JavaCompilationUnit;
import com.migration.validator.model.cldk.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Extracts EntityManager field injections from all classes in the
 * AnalysisResult.
 */
public class EntityManagerExtractor {

    public List<EntityManagerUsageModel> extract(AnalysisResult analysis) {
        if (analysis == null || analysis.symbolTable == null)
            return Collections.emptyList();

        List<EntityManagerUsageModel> usages = new ArrayList<>();
        for (JavaCompilationUnit cu : analysis.symbolTable.values()) {
            if (cu.typeDeclarations == null)
                continue;
            for (Map.Entry<String, Type> entry : cu.typeDeclarations.entrySet()) {
                usages.addAll(extractUsages(cu, entry.getKey(), entry.getValue()));
            }
        }
        return usages;
    }

    private List<EntityManagerUsageModel> extractUsages(
            JavaCompilationUnit cu, String qname, Type type) {

        List<Field> fields = type.fieldDeclarations != null
                ? type.fieldDeclarations
                : Collections.emptyList();

        List<EntityManagerUsageModel> usages = new ArrayList<>();
        for (Field field : fields) {
            if (!isEntityManagerField(field.type))
                continue;

            List<String> annotations = field.annotations != null
                    ? field.annotations
                    : Collections.emptyList();
            boolean usesPersistenceContext = annotations.stream()
                    .anyMatch(a -> a.contains("@PersistenceContext"));
            boolean usesInject = annotations.stream()
                    .anyMatch(a -> a.contains("@Inject"));
            String unitName = usesPersistenceContext ? findUnitName(annotations) : null;
            String fieldName = (field.variables != null && !field.variables.isEmpty())
                    ? field.variables.get(0)
                    : "unknown";

            usages.add(buildUsage(cu.filePath, qname, fieldName,
                    usesPersistenceContext, usesInject, unitName));
        }
        return usages;
    }

    // A field is an EntityManager when its type is the bare simple name
    // OR is fully-qualified under javax/jakarta.persistence.
    private boolean isEntityManagerField(String fieldType) {
        if (fieldType == null)
            return false;
        return fieldType.endsWith("EntityManager") &&
                (fieldType.equals("EntityManager") ||
                        fieldType.contains("javax.persistence") ||
                        fieldType.contains("jakarta.persistence"));
    }

    private String findUnitName(List<String> annotations) {
        return annotations.stream()
                .filter(a -> a.contains("@PersistenceContext"))
                .map(a -> AnnotationUtils.extractAnnotationField(a, "unitName"))
                .filter(v -> v != null)
                .findFirst()
                .orElse(null);
    }

    private EntityManagerUsageModel buildUsage(
            String filePath, String qname, String fieldName,
            boolean usesPersistenceContext, boolean usesInject, String unitName) {

        int dot = qname.lastIndexOf('.');
        EntityManagerUsageModel usage = new EntityManagerUsageModel();
        usage.setOriginalFile(filePath);
        usage.setPackageName(dot >= 0 ? qname.substring(0, dot) : "");
        usage.setClassName(dot >= 0 ? qname.substring(dot + 1) : qname);
        usage.setFieldName(fieldName);
        usage.setUsesPersistenceContext(usesPersistenceContext);
        usage.setUsesInject(usesInject);
        usage.setUnitName(unitName);
        return usage;
    }
}
