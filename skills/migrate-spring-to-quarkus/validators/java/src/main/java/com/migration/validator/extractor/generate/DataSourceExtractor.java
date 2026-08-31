package com.migration.validator.extractor.generate;

import com.migration.validator.model.PersistenceModels.DataSourceUsageModel;
import com.migration.validator.model.cldk.AnalysisResult;
import com.migration.validator.model.cldk.Field;
import com.migration.validator.model.cldk.JavaCompilationUnit;
import com.migration.validator.model.cldk.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Extracts DataSource field injections from all classes in the AnalysisResult.
 */
public class DataSourceExtractor {

    public List<DataSourceUsageModel> extract(AnalysisResult analysis) {
        if (analysis == null || analysis.symbolTable == null)
            return Collections.emptyList();

        List<DataSourceUsageModel> usages = new ArrayList<>();
        for (JavaCompilationUnit cu : analysis.symbolTable.values()) {
            if (cu.typeDeclarations == null)
                continue;
            for (Map.Entry<String, Type> entry : cu.typeDeclarations.entrySet()) {
                usages.addAll(extractFromType(cu, entry.getKey(), entry.getValue()));
            }
        }
        return usages;
    }

    private List<DataSourceUsageModel> extractFromType(JavaCompilationUnit cu, String qname, Type jtype) {
        List<Field> fields = jtype.fieldDeclarations != null
                ? jtype.fieldDeclarations
                : Collections.emptyList();

        List<DataSourceUsageModel> usages = new ArrayList<>();
        for (Field field : fields) {
            if (field.type == null || !isDataSourceType(field.type))
                continue;

            List<String> anns = field.annotations != null
                    ? field.annotations
                    : Collections.emptyList();

            boolean usesResource = anns.stream().anyMatch(a -> a.contains("@Resource"));
            boolean usesInject = anns.stream().anyMatch(a -> a.contains("@Inject"));
            boolean usesAutowired = anns.stream().anyMatch(a -> a.contains("@Autowired"));

            DataSourceUsageModel usage = new DataSourceUsageModel();
            usage.setOriginalFile(cu.filePath);
            usage.setPackageName(packageFrom(qname));
            usage.setClassName(classFrom(qname));
            usage.setFieldName(firstVariable(field));
            usage.setUsesResource(usesResource);
            usage.setUsesInject(usesInject);
            usage.setUsesAutowired(usesAutowired);
            usage.setJndiName(usesResource ? resolveJndiName(anns) : null);
            usages.add(usage);
        }
        return usages;
    }

    private static boolean isDataSourceType(String fieldType) {
        return fieldType.contains("DataSource") &&
                (fieldType.contains("javax.sql") ||
                        fieldType.contains("jakarta.sql") ||
                        fieldType.endsWith("DataSource"));
    }

    private static String resolveJndiName(List<String> annotations) {
        return annotations.stream()
                .filter(a -> a.contains("@Resource"))
                .findFirst()
                .map(a -> {
                    String n = AnnotationUtils.extractAnnotationField(a, "name");
                    return n != null ? n : AnnotationUtils.extractAnnotationField(a, "lookup");
                })
                .orElse(null);
    }

    private static String firstVariable(Field field) {
        return (field.variables != null && !field.variables.isEmpty())
                ? field.variables.get(0)
                : "unknown";
    }

    private static String packageFrom(String qname) {
        return qname.contains(".") ? qname.substring(0, qname.lastIndexOf('.')) : "";
    }

    private static String classFrom(String qname) {
        return qname.contains(".") ? qname.substring(qname.lastIndexOf('.') + 1) : qname;
    }
}
