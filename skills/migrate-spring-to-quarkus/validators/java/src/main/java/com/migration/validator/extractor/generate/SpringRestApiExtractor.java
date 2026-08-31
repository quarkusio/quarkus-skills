package com.migration.validator.extractor.generate;

import com.migration.validator.core.PropertyUtils;
import com.migration.validator.model.RestModels.*;
import com.migration.validator.model.cldk.AnalysisResult;
import com.migration.validator.model.cldk.Callable;
import com.migration.validator.model.cldk.Field;
import com.migration.validator.model.cldk.JavaCompilationUnit;
import com.migration.validator.model.cldk.ParameterInCallable;
import com.migration.validator.model.cldk.Type;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts Spring REST APIs (@RestController, @Controller) from an
 * AnalysisResult.
 * Also merges any JAX-RS endpoints found in the same project.
 */
public class SpringRestApiExtractor {

    private static final Map<String, String> MAPPING_TO_HTTP = Map.of(
            "@GetMapping", "GET",
            "@PostMapping", "POST",
            "@PutMapping", "PUT",
            "@DeleteMapping", "DELETE",
            "@PatchMapping", "PATCH");
    private static final List<String> SECURITY_ANNOTATIONS = Arrays.asList("@PreAuthorize", "@Secured",
            "@RolesAllowed");
    private static final List<String> INJECTION_ANNOTATIONS = Arrays.asList("@Inject", "@Autowired");

    private final JaxRsRestApiExtractor jaxRsExtractor = new JaxRsRestApiExtractor();

    // -----------------------------------------------------------------------
    // Public entry point
    // -----------------------------------------------------------------------

    public RestModel extract(AnalysisResult analysis, Path projectRoot,
            Map<String, String> constantsCache) {
        RestModel model = extractSpring(analysis, projectRoot, constantsCache);
        RestModel jaxRsModel = jaxRsExtractor.extract(analysis, constantsCache);
        mergeJaxRsApis(model, jaxRsModel);
        return model;
    }

    // -----------------------------------------------------------------------
    // JAX-RS merge
    // -----------------------------------------------------------------------

    private void mergeJaxRsApis(RestModel target, RestModel jaxRsModel) {
        String jaxRsRoot = jaxRsModel.getRootPath() != null ? jaxRsModel.getRootPath() : "";
        for (RestApi api : jaxRsModel.getApis()) {
            if (!jaxRsRoot.isEmpty()) {
                String path = api.getPath() != null ? api.getPath() : "";
                api.setPath(jaxRsRoot.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", ""));
            }
            target.getApis().add(api);
        }
    }

    // -----------------------------------------------------------------------
    // Spring extraction — iterate compilation units
    // -----------------------------------------------------------------------

    private RestModel extractSpring(AnalysisResult analysis, Path projectRoot,
            Map<String, String> constantsCache) {
        RestModel model = new RestModel();
        model.setRootPath(PropertyUtils.findSpringRestPath(projectRoot).orElse(null));

        if (analysis == null || analysis.symbolTable == null) {
            return model;
        }

        List<RestApi> apis = new ArrayList<>();
        for (JavaCompilationUnit cu : analysis.symbolTable.values()) {
            if (cu.typeDeclarations == null)
                continue;
            for (Map.Entry<String, Type> typeEntry : cu.typeDeclarations.entrySet()) {
                RestApi api = processType(cu, typeEntry.getKey(), typeEntry.getValue(), constantsCache);
                if (api != null)
                    apis.add(api);
            }
        }

        model.setApis(apis);
        return model;
    }

    // -----------------------------------------------------------------------
    // Type-level processing
    // -----------------------------------------------------------------------

    private RestApi processType(JavaCompilationUnit cu, String qname, Type jtype,
            Map<String, String> cache) {
        List<String> annotations = orEmpty(jtype.annotations);

        boolean isController = annotations.stream()
                .anyMatch(a -> {
                    String name = AnnotationUtils.getAnnotationName(a);
                    return "Controller".equals(name) || "RestController".equals(name);
                });
        if (!isController)
            return null;

        // Class-level @RequestMapping
        String mappingAnn = annotations.stream()
                .filter(a -> a.contains("@RequestMapping")).findFirst().orElse(null);
        List<String> basePaths = mappingAnn != null
                ? AnnotationUtils.extractMappingPaths(mappingAnn, "RequestMapping", cache, qname)
                : new ArrayList<>();
        String basePath = basePaths.isEmpty() ? "" : basePaths.get(0);

        List<String> classConsumes = mappingAnn != null
                ? AnnotationUtils.extractAnnotationPropertyValues(mappingAnn, "RequestMapping", "consumes", cache,
                        qname)
                : new ArrayList<>();
        List<String> classProduces = mappingAnn != null
                ? AnnotationUtils.extractAnnotationPropertyValues(mappingAnn, "RequestMapping", "produces", cache,
                        qname)
                : new ArrayList<>();

        RestApi api = new RestApi();
        api.setPath(basePath);
        api.setFile(cu.filePath);
        api.setClassName(simpleName(qname));
        api.setAnnotations(annotations);
        api.setConsumes(classConsumes);
        api.setProduces(classProduces);

        collectFieldInjections(jtype, api);

        if (jtype.callableDeclarations != null) {
            for (Callable callable : jtype.callableDeclarations.values()) {
                processCallable(callable, qname, classConsumes, classProduces, cache, api);
            }
        }

        return api;
    }

    private void collectFieldInjections(Type jtype, RestApi api) {
        Set<String> injected = new TreeSet<>();
        for (Field field : orEmpty(jtype.fieldDeclarations)) {
            if (field.annotations == null)
                continue;
            if (field.annotations.stream().anyMatch(a -> INJECTION_ANNOTATIONS.stream().anyMatch(a::contains))) {
                injected.add(simpleName(field.type));
            }
        }
        api.setInjectedDependencies(new ArrayList<>(injected));
        api.setContextInjections(new ArrayList<>());
    }

    // -----------------------------------------------------------------------
    // Callable-level processing
    // -----------------------------------------------------------------------

    private void processCallable(Callable callable, String qname,
            List<String> classConsumes, List<String> classProduces,
            Map<String, String> cache, RestApi api) {
        List<String> methAnnotations = orEmpty(callable.annotations);

        // Detect HTTP method from @GetMapping / @PostMapping / etc.
        String httpMethod = null;
        String httpAnnotationName = null;
        List<String> subPaths = new ArrayList<>();

        for (Map.Entry<String, String> entry : MAPPING_TO_HTTP.entrySet()) {
            String hmTag = entry.getKey();
            String hmAnn = methAnnotations.stream().filter(a -> a.contains(hmTag)).findFirst().orElse(null);
            if (hmAnn != null) {
                httpAnnotationName = hmTag.substring(1); // strip leading '@'
                httpMethod = entry.getValue();
                subPaths = AnnotationUtils.extractMappingPaths(hmAnn, httpAnnotationName, cache, qname);
                break;
            }
        }

        // Fall back to @RequestMapping with explicit method attribute
        if (httpMethod == null) {
            String rmAnn = methAnnotations.stream()
                    .filter(a -> a.contains("@RequestMapping")).findFirst().orElse(null);
            if (rmAnn != null) {
                httpAnnotationName = "RequestMapping";
                Matcher mm = Pattern.compile("method\\s*=\\s*(?:RequestMethod\\.)?([A-Z]+)").matcher(rmAnn);
                httpMethod = mm.find() ? mm.group(1) : "GET";
                subPaths = AnnotationUtils.extractMappingPaths(rmAnn, "RequestMapping", cache, qname);
            }
        }

        if (httpMethod == null)
            return;

        // Method-level consumes / produces (fall back to class-level if absent)
        String hmAnn = findAnnotation(methAnnotations, httpAnnotationName);
        List<String> methConsumes = hmAnn != null
                ? AnnotationUtils.extractAnnotationPropertyValues(hmAnn, httpAnnotationName, "consumes", cache, qname)
                : new ArrayList<>();
        List<String> methProduces = hmAnn != null
                ? AnnotationUtils.extractAnnotationPropertyValues(hmAnn, httpAnnotationName, "produces", cache, qname)
                : new ArrayList<>();

        Map<String, Object> security = extractSecurity(methAnnotations);

        List<ParameterInCallable> allParams = orEmpty(callable.parameters);
        List<RestParameter> pathParams = buildPathParams(allParams, cache, qname);
        List<RestParameter> queryParams = buildQueryParams(allParams, cache, qname);
        String requestType = allParams.stream()
                .filter(p -> p.annotations != null &&
                        p.annotations.stream().anyMatch(a -> a.contains("@RequestBody")))
                .map(p -> p.type).findFirst().orElse(null);

        List<String> effectiveConsumes = !methConsumes.isEmpty() ? methConsumes : classConsumes;
        List<String> effectiveProduces = !methProduces.isEmpty() ? methProduces : classProduces;

        for (String sp : subPaths) {
            RestOperation op = new RestOperation();
            op.setMethod(httpMethod);
            op.setSubpath(sp);
            op.setRequestType(requestType);
            op.setResponseType(callable.returnType);
            op.setSecurity(security);
            op.setConsumes(effectiveConsumes);
            op.setProduces(effectiveProduces);
            op.setAnnotations(methAnnotations);
            op.setPathParams(pathParams);
            op.setQueryParams(queryParams);
            op.setFormParams(new ArrayList<>());
            api.getOperations().add(op);
        }
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
    // Parameter builders
    // -----------------------------------------------------------------------

    private List<RestParameter> buildPathParams(List<ParameterInCallable> params,
            Map<String, String> cache, String currentClass) {
        List<RestParameter> result = new ArrayList<>();
        for (ParameterInCallable p : params) {
            if (p.annotations == null)
                continue;
            if (p.annotations.stream().noneMatch(a -> a.contains("@PathVariable")))
                continue;
            RestParameter rp = new RestParameter();
            rp.setName(p.name);
            rp.setType(p.type);
            rp.setMapsTo(AnnotationUtils.detectAnnotationPropValue(
                    p.annotations, "@PathVariable", Arrays.asList("name", "value"), cache, currentClass));
            result.add(rp);
        }
        return result;
    }

    private List<RestParameter> buildQueryParams(List<ParameterInCallable> params,
            Map<String, String> cache, String currentClass) {
        List<RestParameter> result = new ArrayList<>();
        for (ParameterInCallable p : params) {
            if (p.annotations == null)
                continue;
            if (p.annotations.stream().noneMatch(a -> a.contains("@RequestParam")))
                continue;
            RestParameter rp = new RestParameter();
            rp.setName(p.name);
            rp.setType(p.type);
            rp.setMapsTo(AnnotationUtils.detectAnnotationPropValue(
                    p.annotations, "@RequestParam", Arrays.asList("name", "value"), cache, currentClass));
            rp.setDefaultValue(p.annotations.stream()
                    .filter(a -> a.contains("@RequestParam"))
                    .map(a -> AnnotationUtils.extractAnnotationField(a, "defaultValue", cache, currentClass))
                    .filter(v -> v != null)
                    .findFirst().orElse(null));
            result.add(rp);
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Small utilities
    // -----------------------------------------------------------------------

    private static String findAnnotation(List<String> annotations, String name) {
        if (name == null)
            return null;
        return annotations.stream().filter(a -> a.contains(name)).findFirst().orElse(null);
    }

    private static String simpleName(String fqn) {
        if (fqn == null)
            return "";
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : new ArrayList<>();
    }
}
