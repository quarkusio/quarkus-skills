package com.migration.validator;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.migration.validator.extractor.generate.SpringRestApiExtractor;
import com.migration.validator.model.RestModels.*;
import com.migration.validator.model.cldk.AnalysisResult;
import com.migration.validator.core.CodeAnalyzerClient;
import com.migration.validator.core.ValidationReport;
import com.migration.validator.core.YamlUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RestValidator - Validates REST API migration from Spring to Quarkus (Phase 8)
 * Instance-based validator with constructor injection for better testability
 * and design.
 *
 * Validates:
 * 1. API count matches
 * 2. All Spring endpoints migrated to Quarkus
 * 3. HTTP methods match
 * 4. Path parameters match
 * 5. Query parameters match
 * 6. Request/response types match
 * 7. Media types (consumes/produces) match
 * 8. Maven compile succeeds
 */
public class RestValidator {

    private static final String PHASE = "phase_8_rest_migration";

    // Media type mappings from Spring constants to standard MIME types
    private static final Map<String, String> MEDIA_TYPE_MAPPINGS = new HashMap<>();
    static {
        MEDIA_TYPE_MAPPINGS.put("APPLICATION_JSON_VALUE", "application/json");
        MEDIA_TYPE_MAPPINGS.put("APPLICATION_XML_VALUE", "application/xml");
        MEDIA_TYPE_MAPPINGS.put("TEXT_PLAIN_VALUE", "text/plain");
        MEDIA_TYPE_MAPPINGS.put("TEXT_HTML_VALUE", "text/html");
        MEDIA_TYPE_MAPPINGS.put("APPLICATION_FORM_URLENCODED_VALUE", "application/x-www-form-urlencoded");
        MEDIA_TYPE_MAPPINGS.put("MULTIPART_FORM_DATA_VALUE", "multipart/form-data");
        MEDIA_TYPE_MAPPINGS.put("APPLICATION_OCTET_STREAM_VALUE", "application/octet-stream");
    }

    private final Path springMetadataPath;
    private final Path quarkusMetadataPath;
    private final Path projectRoot;
    private final Path specPath;
    private final boolean compatMode;

    /**
     * Constructor with dependency injection (full migration mode).
     *
     * @param springMetadata  Path to Spring project code-metadata.yaml
     * @param quarkusMetadata Path to Quarkus project code-metadata.yaml
     * @param projectRoot     Absolute path to the target Quarkus project root
     * @param specPath        Absolute path to migration-spec.yaml
     */
    public RestValidator(Path springMetadata, Path quarkusMetadata,
            Path projectRoot, Path specPath) {
        this(springMetadata, quarkusMetadata, projectRoot, specPath, false);
    }

    /**
     * Constructor with dependency injection.
     *
     * @param springMetadata  Path to Spring project code-metadata.yaml
     * @param quarkusMetadata Path to Quarkus project code-metadata.yaml
     * @param projectRoot     Absolute path to the target Quarkus project root
     * @param specPath        Absolute path to migration-spec.yaml
     * @param compatMode      When true, uses SpringRestApiExtractor for the target
     *                        project
     *                        (controllers kept as Spring MVC via
     *                        quarkus-spring-web)
     */
    public RestValidator(Path springMetadata, Path quarkusMetadata,
            Path projectRoot, Path specPath, boolean compatMode) {
        this.springMetadataPath = springMetadata.toAbsolutePath();
        this.quarkusMetadataPath = quarkusMetadata.toAbsolutePath();
        this.projectRoot = projectRoot.toAbsolutePath();
        this.specPath = specPath.toAbsolutePath();
        this.compatMode = compatMode;
    }

    /**
     * Run validation and return exit code.
     *
     * @param verbose Enable verbose output with detailed logging
     * @return 0 for success, 1 for failure
     */
    public int validate(boolean verbose) {
        try {
            // Load existing spec
            Map<String, Object> spec = YamlUtils.loadYaml(specPath);

            ValidationReport report = runValidation();

            // Save report to spec
            saveToSpec(spec, report);

            // Print summary
            report.printSummary(PHASE);

            // Return exit code
            return report.hasFailures() ? 1 : 0;

        } catch (Exception e) {
            System.err.println("Validation failed: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    /**
     * Internal validation method
     */
    private ValidationReport runValidation() throws IOException {
        ValidationReport report = new ValidationReport();

        // Load Spring-side metadata (always from the YAML file)
        CodeMetadata springMetadata = loadMetadata(springMetadataPath);
        RestModel springRest = springMetadata.getRest();

        // In compat mode the Quarkus target still uses Spring MVC annotations,
        // so we re-extract the target REST model using SpringRestApiExtractor
        // rather than reading the pre-generated Quarkus metadata YAML.
        RestModel quarkusRest;
        if (compatMode) {
            quarkusRest = extractSpringRestFromTarget();
        } else {
            CodeMetadata quarkusMetadata = loadMetadata(quarkusMetadataPath);
            quarkusRest = quarkusMetadata.getRest();
        }

        if (springRest == null || quarkusRest == null) {
            report.fail("rest_metadata_exists",
                    "REST metadata missing in Spring or Quarkus project. " +
                            "Ensure metadata generation completed successfully.");
            return report;
        }

        // Run validation checks — endpoint comparison logic is identical in both modes
        validateApiCount(springRest, quarkusRest, report);
        validateEndpointMigration(springRest, quarkusRest, report);
        if (compatMode) {
            validateCompatModeRequirements(report);
        }
        validateMavenCompile(report);

        return report;
    }

    /**
     * Re-extract the REST model from the target project using
     * SpringRestApiExtractor.
     * Used in compat mode where the target still has Spring MVC annotations.
     */
    private RestModel extractSpringRestFromTarget() throws IOException {
        try {
            CodeAnalyzerClient client = new CodeAnalyzerClient();
            AnalysisResult analysis = client.analyzeProject(projectRoot, 1);
            SpringRestApiExtractor extractor = new SpringRestApiExtractor();
            return extractor.extract(analysis, projectRoot, Collections.emptyMap());
        } catch (Exception e) {
            // Fall back to the pre-generated metadata YAML if live extraction fails
            System.err.println("[WARN] SpringRestApiExtractor failed (" + e.getMessage() +
                    "), falling back to quarkus metadata YAML");
            CodeMetadata quarkusMetadata = loadMetadata(quarkusMetadataPath);
            return quarkusMetadata.getRest();
        }
    }

    /**
     * Compat-mode specific checks (runs only when {@code --compat-mode} is set).
     * <ol>
     * <li>FAIL — {@code quarkus-spring-web} missing from pom.xml</li>
     * <li>FAIL — plain {@code @Controller} classes present (not bridged)</li>
     * <li>FAIL — {@code RestTemplate} / {@code RestClient} usage (not bridged)</li>
     * <li>WARN — {@code @ControllerAdvice} (partially supported)</li>
     * <li>WARN — {@code HandlerInterceptor} / {@code WebMvcConfigurer} (not
     * bridged)</li>
     * </ol>
     */
    private void validateCompatModeRequirements(ValidationReport report) {
        checkSpringWebExtension(report);
        SourceScanResult scan = scanSourceFiles();
        reportSourceFindings(report, scan);
    }

    /** Check 1 — quarkus-spring-web declared in pom.xml. */
    private void checkSpringWebExtension(ValidationReport report) {
        Path pomXml = projectRoot.resolve("pom.xml");
        try {
            String pom = new String(Files.readAllBytes(pomXml));
            if (pom.contains("quarkus-spring-web")) {
                report.pass("compat_spring_web_extension",
                        "quarkus-spring-web extension is present in pom.xml");
            } else {
                report.fail("compat_spring_web_extension",
                        "quarkus-spring-web extension is missing from pom.xml. " +
                                "Add: <artifactId>quarkus-spring-web</artifactId>");
            }
        } catch (IOException e) {
            report.fail("compat_spring_web_extension",
                    "Could not read pom.xml: " + e.getMessage());
        }
    }

    /**
     * Walk src/main/java once and bucket each file into the relevant finding lists.
     * Returns an empty result (no failures, no warnings) if the source directory
     * does not exist.
     */
    private SourceScanResult scanSourceFiles() {
        SourceScanResult result = new SourceScanResult();
        Path srcMain = projectRoot.resolve("src/main/java");
        if (!Files.exists(srcMain))
            return result;

        try {
            for (Path p : (Iterable<Path>) Files.walk(srcMain).filter(p -> p.toString().endsWith(".java"))::iterator) {
                String content = new String(Files.readAllBytes(p));
                String rel = projectRoot.relativize(p).toString();

                if (hasPlainController(content))
                    result.plainControllers.add(rel);
                if (content.contains("RestTemplate") || content.contains("RestClient"))
                    result.restClients.add(rel);
                if (content.contains("@ControllerAdvice"))
                    result.controllerAdvice.add(rel);
                if (content.contains("HandlerInterceptor") || content.contains("WebMvcConfigurer"))
                    result.interceptors.add(rel);
            }
        } catch (IOException e) {
            result.scanError = e.getMessage();
        }
        return result;
    }

    /** Report the findings from the source scan into the validation report. */
    private void reportSourceFindings(ValidationReport report, SourceScanResult scan) {
        if (scan.scanError != null) {
            report.warn("compat_source_scan", "Could not fully scan source files: " + scan.scanError);
        }

        // Check 2 — plain @Controller
        if (scan.plainControllers.isEmpty()) {
            report.pass("compat_no_plain_controller",
                    "No plain @Controller classes found (quarkus-spring-web only bridges @RestController)");
        } else {
            report.fail("compat_no_plain_controller",
                    "Plain @Controller classes found — not bridged by quarkus-spring-web (will silently not work at runtime):\n  - "
                            +
                            String.join("\n  - ", scan.plainControllers) +
                            "\nEither annotate with @RestController (if JSON API) or migrate to @Path + TemplateInstance (if template).");
        }

        // Check 3 — RestTemplate / RestClient
        if (scan.restClients.isEmpty()) {
            report.pass("compat_no_rest_client", "No RestTemplate / RestClient usage found");
        } else {
            report.fail("compat_no_rest_client",
                    "RestTemplate / RestClient usage found — not bridged by quarkus-spring-web (ClassNotFoundException at runtime):\n  - "
                            +
                            String.join("\n  - ", scan.restClients) +
                            "\nMigrate to JAX-RS Client or MicroProfile REST Client.");
        }

        // Warning 4 — @ControllerAdvice
        if (!scan.controllerAdvice.isEmpty()) {
            report.warn("compat_controller_advice",
                    "@ControllerAdvice usage found — only partially supported by quarkus-spring-web. " +
                            "Verify runtime behaviour or migrate to JAX-RS @Provider ExceptionMapper:\n  - " +
                            String.join("\n  - ", scan.controllerAdvice));
        }

        // Warning 5 — HandlerInterceptor / WebMvcConfigurer
        if (!scan.interceptors.isEmpty()) {
            report.warn("compat_interceptor",
                    "HandlerInterceptor / WebMvcConfigurer usage found — not bridged by quarkus-spring-web. " +
                            "Migrate to JAX-RS ContainerRequestFilter or Quarkus HttpServerOptions:\n  - " +
                            String.join("\n  - ", scan.interceptors));
        }
    }

    /** Holds per-category file lists gathered during a single source-tree walk. */
    private static class SourceScanResult {
        final List<String> plainControllers = new ArrayList<>();
        final List<String> restClients = new ArrayList<>();
        final List<String> controllerAdvice = new ArrayList<>();
        final List<String> interceptors = new ArrayList<>();
        String scanError = null;
    }

    /**
     * Returns true when a file contains a plain {@code @Controller} annotation
     * but not {@code @RestController} (which is a superset annotation).
     */
    private boolean hasPlainController(String content) {
        if (!content.contains("@Controller"))
            return false;
        return content.replace("@RestController", "").contains("@Controller");
    }

    /**
     * Load REST metadata from YAML file
     */
    private CodeMetadata loadMetadata(Path path) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(path.toFile(), CodeMetadata.class);
    }

    /**
     * Validate API count matches
     */
    private void validateApiCount(RestModel spring, RestModel quarkus, ValidationReport report) {
        int springCount = spring.getApis().size();
        int quarkusCount = quarkus.getApis().size();

        if (springCount == quarkusCount) {
            report.pass("api_count",
                    String.format("API count matches: %d REST controllers", springCount));
        } else {
            report.fail("api_count",
                    String.format("API count mismatch: Spring=%d, Quarkus=%d. Review migrated REST controllers.",
                            springCount, quarkusCount));
        }
    }

    /**
     * Validate all Spring endpoints migrated to Quarkus
     */
    private void validateEndpointMigration(RestModel spring, RestModel quarkus, ValidationReport report) {
        // Build endpoint maps for comparison
        Map<String, RestOperation> springEndpoints = buildEndpointMap(spring);
        Map<String, RestOperation> quarkusEndpoints = buildEndpointMap(quarkus);

        // Check each Spring endpoint has a Quarkus equivalent
        int totalEndpoints = springEndpoints.size();
        int migratedEndpoints = 0;
        List<String> missingEndpoints = new ArrayList<>();
        List<String> mismatchedEndpoints = new ArrayList<>();

        for (Map.Entry<String, RestOperation> entry : springEndpoints.entrySet()) {
            String endpointKey = entry.getKey();
            RestOperation springOp = entry.getValue();

            // Try to find matching Quarkus endpoint
            RestOperation quarkusOp = findMatchingEndpoint(endpointKey, springOp, quarkusEndpoints);

            if (quarkusOp == null) {
                missingEndpoints.add(endpointKey);
            } else {
                migratedEndpoints++;

                // Validate endpoint details
                List<String> mismatches = compareOperations(endpointKey, springOp, quarkusOp);
                if (!mismatches.isEmpty()) {
                    mismatchedEndpoints.addAll(mismatches);
                }
            }
        }

        // Report endpoint migration status
        if (missingEndpoints.isEmpty()) {
            report.pass("endpoints_migrated",
                    String.format("All %d endpoints migrated successfully", totalEndpoints));
        } else {
            report.fail("endpoints_migrated",
                    String.format("%d/%d endpoints migrated. Missing:\n  - %s\nMigrate missing endpoints.",
                            migratedEndpoints, totalEndpoints, String.join("\n  - ", missingEndpoints)));
        }

        // Report endpoint detail mismatches
        if (!mismatchedEndpoints.isEmpty()) {
            report.fail("endpoint_details",
                    String.format("Endpoint mismatches found:\n  - %s\nReview and fix endpoint mismatches.",
                            String.join("\n  - ", mismatchedEndpoints)));
        } else if (missingEndpoints.isEmpty()) {
            report.pass("endpoint_details",
                    "All endpoint details (parameters, types, media types) match");
        }
    }

    /**
     * Build map of endpoints for easy lookup
     * Key format: "METHOD /normalized/path"
     */
    private Map<String, RestOperation> buildEndpointMap(RestModel rest) {
        Map<String, RestOperation> endpoints = new HashMap<>();

        for (RestApi api : rest.getApis()) {
            String basePath = normalizePath(api.getPath());

            for (RestOperation op : api.getOperations()) {
                String fullPath = combinePaths(basePath, op.getSubpath());
                String key = op.getMethod() + " " + fullPath;
                endpoints.put(key, op);
            }
        }

        return endpoints;
    }

    /**
     * Find matching endpoint in Quarkus, handling path variations
     */
    private RestOperation findMatchingEndpoint(String springKey, RestOperation springOp,
            Map<String, RestOperation> quarkusEndpoints) {
        // Try exact match first
        if (quarkusEndpoints.containsKey(springKey)) {
            return quarkusEndpoints.get(springKey);
        }

        // Try with path parameter variations
        String method = springKey.split(" ")[0];
        String path = springKey.substring(method.length() + 1);

        for (Map.Entry<String, RestOperation> entry : quarkusEndpoints.entrySet()) {
            String quarkusKey = entry.getKey();
            if (quarkusKey.startsWith(method + " ")) {
                String quarkusPath = quarkusKey.substring(method.length() + 1);
                if (pathsMatch(path, quarkusPath)) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    /**
     * Check if two paths match, considering path parameters
     */
    private boolean pathsMatch(String path1, String path2) {
        // Normalize both paths
        path1 = normalizePath(path1);
        path2 = normalizePath(path2);

        // Split into segments
        String[] segments1 = path1.split("/");
        String[] segments2 = path2.split("/");

        if (segments1.length != segments2.length) {
            return false;
        }

        // Compare segments, treating {param} as wildcards
        for (int i = 0; i < segments1.length; i++) {
            String seg1 = segments1[i];
            String seg2 = segments2[i];

            // If either is a path parameter, consider it a match
            if (isPathParam(seg1) || isPathParam(seg2)) {
                continue;
            }

            // Otherwise must match exactly
            if (!seg1.equals(seg2)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if segment is a path parameter
     */
    private boolean isPathParam(String segment) {
        return segment.startsWith("{") && segment.endsWith("}");
    }

    /**
     * Compare two operations and return list of mismatches
     */
    private List<String> compareOperations(String endpointKey, RestOperation spring, RestOperation quarkus) {
        List<String> mismatches = new ArrayList<>();

        // Compare path parameters
        if (!parametersMatch(spring.getPathParams(), quarkus.getPathParams())) {
            mismatches.add(endpointKey + ": Path parameters mismatch");
        }

        // Compare query parameters
        if (!parametersMatch(spring.getQueryParams(), quarkus.getQueryParams())) {
            mismatches.add(endpointKey + ": Query parameters mismatch");
        }

        // Compare request type
        if (!typesMatch(spring.getRequestType(), quarkus.getRequestType())) {
            mismatches.add(endpointKey + ": Request type mismatch");
        }

        // Compare response type
        if (!typesMatch(spring.getResponseType(), quarkus.getResponseType())) {
            mismatches.add(endpointKey + ": Response type mismatch");
        }

        // Compare consumes
        if (!mediaTypesMatch(spring.getConsumes(), quarkus.getConsumes())) {
            mismatches.add(endpointKey + ": Consumes media types mismatch");
        }

        // Compare produces
        if (!mediaTypesMatch(spring.getProduces(), quarkus.getProduces())) {
            mismatches.add(endpointKey + ": Produces media types mismatch");
        }

        return mismatches;
    }

    /**
     * Check if parameter lists match
     */
    private boolean parametersMatch(List<RestParameter> params1, List<RestParameter> params2) {
        if (params1.size() != params2.size()) {
            return false;
        }

        // Build name->type maps
        Map<String, String> map1 = params1.stream()
                .collect(Collectors.toMap(RestParameter::getName, RestParameter::getType));
        Map<String, String> map2 = params2.stream()
                .collect(Collectors.toMap(RestParameter::getName, RestParameter::getType));

        // Check all parameters match
        for (Map.Entry<String, String> entry : map1.entrySet()) {
            String name = entry.getKey();
            String type1 = entry.getValue();
            String type2 = map2.get(name);

            if (type2 == null || !typesMatch(type1, type2)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if two types match, handling async wrappers
     */
    private boolean typesMatch(String type1, String type2) {
        if (type1 == null && type2 == null)
            return true;
        if (type1 == null || type2 == null)
            return false;

        // Normalize types
        type1 = normalizeType(type1);
        type2 = normalizeType(type2);

        return type1.equals(type2);
    }

    /**
     * Normalize type name, removing async wrappers
     */
    private String normalizeType(String type) {
        if (type == null)
            return "";

        // Remove common async wrappers
        type = type.replaceAll("^(Uni|Multi|CompletionStage|CompletableFuture)<(.+)>$", "$2");
        type = type.replaceAll("^ResponseEntity<(.+)>$", "$1");
        type = type.replaceAll("^Response<(.+)>$", "$1");

        // Remove package names, keep only simple class name
        if (type.contains(".")) {
            type = type.substring(type.lastIndexOf('.') + 1);
        }

        return type.trim();
    }

    /**
     * Check if media type lists match
     */
    private boolean mediaTypesMatch(List<String> types1, List<String> types2) {
        // Handle null lists
        if (types1 == null)
            types1 = new ArrayList<>();
        if (types2 == null)
            types2 = new ArrayList<>();

        // Normalize both lists
        Set<String> set1 = types1.stream()
                .map(this::normalizeMediaType)
                .collect(Collectors.toSet());
        Set<String> set2 = types2.stream()
                .map(this::normalizeMediaType)
                .collect(Collectors.toSet());

        return set1.equals(set2);
    }

    /**
     * Normalize media type, handling Spring constants
     */
    private String normalizeMediaType(String mediaType) {
        if (mediaType == null)
            return "";

        // Check if it's a Spring constant
        String normalized = MEDIA_TYPE_MAPPINGS.get(mediaType);
        if (normalized != null) {
            return normalized;
        }

        // Remove MediaType. prefix if present
        if (mediaType.startsWith("MediaType.")) {
            mediaType = mediaType.substring("MediaType.".length());
            normalized = MEDIA_TYPE_MAPPINGS.get(mediaType);
            if (normalized != null) {
                return normalized;
            }
        }

        return mediaType.toLowerCase().trim();
    }

    /**
     * Normalize path by removing leading/trailing slashes and extra slashes
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        // Remove leading/trailing slashes
        path = path.replaceAll("^/+", "").replaceAll("/+$", "");

        // Replace multiple slashes with single slash
        path = path.replaceAll("/+", "/");

        return path;
    }

    /**
     * Combine base path and subpath
     */
    private String combinePaths(String basePath, String subPath) {
        basePath = normalizePath(basePath);
        subPath = normalizePath(subPath);

        if (basePath.isEmpty())
            return "/" + subPath;
        if (subPath.isEmpty())
            return "/" + basePath;

        return "/" + basePath + "/" + subPath;
    }

    /**
     * Validate Maven compile succeeds
     */
    private void validateMavenCompile(ValidationReport report) {
        try {
            ProcessBuilder pb = new ProcessBuilder("mvn", "clean", "compile", "-q");
            pb.directory(this.projectRoot.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                report.pass("maven_compile", "Maven compile successful");
            } else {
                report.fail("maven_compile", "Maven compile failed. Fix compilation errors.");
            }

        } catch (Exception e) {
            report.fail("maven_compile",
                    "Maven compile check failed: " + e.getMessage() +
                            ". Ensure Maven is installed and project is valid.");
        }
    }

    @SuppressWarnings("unchecked")
    private void saveToSpec(Map<String, Object> spec, ValidationReport report) {
        try {
            // Ensure intermediate.history exists
            Map<String, Object> intermediate = (Map<String, Object>) spec.computeIfAbsent("intermediate",
                    k -> new HashMap<>());
            List<Map<String, Object>> history = (List<Map<String, Object>>) intermediate.computeIfAbsent("history",
                    k -> new ArrayList<>());

            // Add this validation run to history
            history.add(report.toMap(PHASE));

            // Save updated spec
            YamlUtils.saveYaml(this.specPath, spec);

        } catch (IOException e) {
            System.err.println("[WARNING] Could not save to spec: " + e.getMessage());
        }
    }
}
