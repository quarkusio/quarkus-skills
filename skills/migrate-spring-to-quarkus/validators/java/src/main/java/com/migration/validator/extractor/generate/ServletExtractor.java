package com.migration.validator.extractor.generate;

import com.migration.validator.model.RestModels.ServletModel;
import com.migration.validator.model.cldk.AnalysisResult;
import com.migration.validator.model.cldk.JavaCompilationUnit;
import com.migration.validator.model.cldk.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Extracts @WebServlet classes from an AnalysisResult.
 */
public class ServletExtractor {

    // Maps each HttpServlet do-method name to its HTTP verb.
    private static final Map<String, String> DO_METHOD_TO_HTTP = Map.of(
            "doGet", "GET",
            "doPost", "POST",
            "doPut", "PUT",
            "doDelete", "DELETE");

    public List<ServletModel> extract(AnalysisResult analysis, Map<String, String> constantsCache) {
        if (analysis == null || analysis.symbolTable == null)
            return Collections.emptyList();

        List<ServletModel> servlets = new ArrayList<>();
        for (JavaCompilationUnit cu : analysis.symbolTable.values()) {
            if (cu.typeDeclarations == null)
                continue;
            for (Map.Entry<String, Type> typeEntry : cu.typeDeclarations.entrySet()) {
                ServletModel model = processType(typeEntry.getKey(), typeEntry.getValue(), cu, constantsCache);
                if (model != null)
                    servlets.add(model);
            }
        }
        return servlets;
    }

    private ServletModel processType(
            String qname, Type jtype, JavaCompilationUnit cu,
            Map<String, String> constantsCache) {

        List<String> annotations = Objects.requireNonNullElse(jtype.annotations, List.of());

        String servletAnn = annotations.stream()
                .filter(a -> a.contains("@WebServlet"))
                .findFirst()
                .orElse(null);
        if (servletAnn == null)
            return null;

        ServletModel servlet = new ServletModel();
        servlet.setClassName(simpleClassName(qname));
        servlet.setFile(cu.filePath);
        servlet.setUrlPatterns(AnnotationUtils.extractWebServletUrlPatterns(servletAnn, constantsCache, qname));
        servlet.setHttpMethods(detectHttpMethods(jtype));
        servlet.setAnnotations(annotations);
        return servlet;
    }

    private List<String> detectHttpMethods(Type jtype) {
        if (jtype.callableDeclarations == null)
            return Collections.emptyList();

        List<String> methods = new ArrayList<>();
        for (String sig : jtype.callableDeclarations.keySet()) {
            String verb = DO_METHOD_TO_HTTP.get(simpleMethodName(sig));
            if (verb != null)
                methods.add(verb);
        }
        return methods;
    }

    /** "com.example.MyClass" -> "MyClass"; "MyClass" -> "MyClass" */
    private static String simpleClassName(String qname) {
        int dot = qname.lastIndexOf('.');
        return dot >= 0 ? qname.substring(dot + 1) : qname;
    }

    /** "com.example.MyClass.doGet(HttpServletRequest, ...)" -> "doGet" */
    private static String simpleMethodName(String sig) {
        String name = sig.contains("(") ? sig.substring(0, sig.indexOf('(')).trim() : sig;
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
