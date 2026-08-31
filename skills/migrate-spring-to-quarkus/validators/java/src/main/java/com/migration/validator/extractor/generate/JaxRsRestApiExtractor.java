package com.migration.validator.extractor.generate;

import com.migration.validator.model.RestModels.*;
import com.migration.validator.model.cldk.AnalysisResult;
import com.migration.validator.model.cldk.Callable;
import com.migration.validator.model.cldk.Field;
import com.migration.validator.model.cldk.JavaCompilationUnit;
import com.migration.validator.model.cldk.ParameterInCallable;
import com.migration.validator.model.cldk.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Extracts JAX-RS REST APIs from an AnalysisResult.
 */
public class JaxRsRestApiExtractor {

    private static final List<String> HTTP_METHODS = Arrays.asList("@GET", "@POST", "@PUT", "@DELETE", "@PATCH",
            "@HEAD", "@OPTIONS");
    private static final List<String> SECURITY_ANNOTATIONS = Arrays.asList("@RolesAllowed", "@PermitAll", "@DenyAll");
    private static final List<String> INJECTION_ANNOTATIONS = Arrays.asList("@Inject", "@EJB", "@Resource");
    private static final List<String> JAX_RS_BINDING_ANNOTATIONS = Arrays.asList("QueryParam", "PathParam",
            "HeaderParam", "CookieParam",
            "FormParam", "MatrixParam", "BeanParam", "Context");

    public RestModel extract(AnalysisResult analysis, Map<String, String> constantsCache) {
        if (analysis == null || analysis.symbolTable == null) {
            return emptyModel();
        }

        List<RestApi> apis = new ArrayList<>();
        String rootPath = null;

        for (JavaCompilationUnit cu : analysis.symbolTable.values()) {
            if (cu.typeDeclarations == null)
                continue;

            for (Map.Entry<String, Type> typeEntry : cu.typeDeclarations.entrySet()) {
                String qname = typeEntry.getKey();
                Type jtype = typeEntry.getValue();
                List<String> annotations = orEmpty(jtype.annotations);

                if (isApplicationSubclass(jtype)) {
                    String appPath = AnnotationUtils.detectAnnotationValue(
                            annotations, "@ApplicationPath", constantsCache, qname);
                    if (appPath != null && rootPath == null)
                        rootPath = appPath;
                }

                RestApi api = processType(cu, qname, jtype, annotations, constantsCache);
                if (api != null)
                    apis.add(api);
            }
        }

        RestModel model = new RestModel();
        model.setRootPath(rootPath);
        model.setApis(apis);
        return model;
    }

    // -----------------------------------------------------------------------
    // Type-level processing
    // -----------------------------------------------------------------------

    private RestApi processType(JavaCompilationUnit cu, String qname, Type jtype,
            List<String> annotations, Map<String, String> cache) {
        String basePath = AnnotationUtils.detectAnnotationValue(annotations, "@Path", cache, qname);
        if (basePath == null)
            return null;

        RestApi api = new RestApi();
        api.setPath(basePath);
        api.setFile(cu.filePath);
        api.setClassName(simpleName(qname));
        api.setAnnotations(annotations);
        api.setConsumes(AnnotationUtils.extractConsumes(annotations));
        api.setProduces(AnnotationUtils.extractProduces(annotations));

        collectFieldInjections(jtype, api);

        if (jtype.callableDeclarations != null) {
            for (Callable callable : jtype.callableDeclarations.values()) {
                RestOperation op = processCallable(callable, cache, qname);
                if (op != null)
                    api.getOperations().add(op);
            }
        }

        return api;
    }

    private void collectFieldInjections(Type jtype, RestApi api) {
        Set<String> injected = new TreeSet<>();
        Set<String> contexts = new TreeSet<>();

        for (Field field : orEmpty(jtype.fieldDeclarations)) {
            if (field.annotations == null)
                continue;
            String typeName = simpleName(field.type);
            for (String ann : field.annotations) {
                if (INJECTION_ANNOTATIONS.stream().anyMatch(ann::contains))
                    injected.add(typeName);
                if (ann.contains("@Context"))
                    contexts.add(typeName);
            }
        }

        api.setInjectedDependencies(new ArrayList<>(injected));
        api.setContextInjections(new ArrayList<>(contexts));
    }

    // -----------------------------------------------------------------------
    // Callable-level processing
    // -----------------------------------------------------------------------

    private RestOperation processCallable(Callable callable,
            Map<String, String> cache, String qname) {
        List<String> methAnnotations = orEmpty(callable.annotations);

        String httpMethod = HTTP_METHODS.stream()
                .filter(hm -> methAnnotations.stream().anyMatch(a -> a.contains(hm)))
                .map(hm -> hm.replace("@", ""))
                .findFirst().orElse(null);
        if (httpMethod == null)
            return null;

        List<ParameterInCallable> params = orEmpty(callable.parameters);
        String requestType = params.stream()
                .filter(p -> p.annotations == null || p.annotations.stream()
                        .noneMatch(a -> JAX_RS_BINDING_ANNOTATIONS.stream().anyMatch(a::contains)))
                .map(p -> p.type)
                .findFirst().orElse(null);

        RestOperation op = new RestOperation();
        op.setMethod(httpMethod);
        op.setSubpath(orSlash(AnnotationUtils.detectAnnotationValue(methAnnotations, "@Path", cache, qname)));
        op.setRequestType(requestType);
        op.setResponseType(callable.returnType);
        op.setSecurity(extractSecurity(methAnnotations));
        op.setConsumes(AnnotationUtils.extractConsumes(methAnnotations));
        op.setProduces(AnnotationUtils.extractProduces(methAnnotations));
        op.setAnnotations(methAnnotations);
        op.setPathParams(buildParams(params, "@PathParam", "@DefaultValue", cache, qname));
        op.setQueryParams(buildParams(params, "@QueryParam", "@DefaultValue", cache, qname));
        op.setFormParams(buildParams(params, "@FormParam", "@DefaultValue", cache, qname));
        return op;
    }

    private Map<String, Object> extractSecurity(List<String> annotations) {
        for (String sec : SECURITY_ANNOTATIONS) {
            String match = annotations.stream().filter(a -> a.contains(sec)).findFirst().orElse(null);
            if (match != null) {
                Map<String, Object> security = new LinkedHashMap<>();
                security.put("annotation", sec);
                security.put("roles_allowed", AnnotationUtils.extractAnnotationField(match, "value"));
                return security;
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Parameter building
    // -----------------------------------------------------------------------

    private List<RestParameter> buildParams(List<ParameterInCallable> params,
            String bindAnnotation, String defaultAnnotation,
            Map<String, String> constantsCache, String currentClass) {
        List<RestParameter> result = new ArrayList<>();
        for (ParameterInCallable p : params) {
            if (p.annotations == null)
                continue;
            if (p.annotations.stream().noneMatch(a -> a.contains(bindAnnotation)))
                continue;
            RestParameter rp = new RestParameter();
            rp.setName(p.name);
            rp.setType(p.type);
            rp.setMapsTo(
                    AnnotationUtils.detectAnnotationValue(p.annotations, bindAnnotation, constantsCache, currentClass));
            rp.setDefaultValue(AnnotationUtils.detectAnnotationValue(p.annotations, defaultAnnotation, constantsCache,
                    currentClass));
            result.add(rp);
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Small utilities
    // -----------------------------------------------------------------------

    private static boolean isApplicationSubclass(Type jtype) {
        return jtype.extendsList != null
                && jtype.extendsList.stream().anyMatch(e -> e.contains("Application"));
    }

    private static String simpleName(String fqn) {
        if (fqn == null)
            return "";
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private static String orSlash(String value) {
        return value != null ? value : "/";
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : new ArrayList<>();
    }

    private static RestModel emptyModel() {
        RestModel model = new RestModel();
        model.setApis(new ArrayList<>());
        return model;
    }
}
