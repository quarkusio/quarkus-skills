package com.migration.validator.extractor.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.migration.validator.core.CodeAnalyzerClient;
import com.migration.validator.core.FrameworkUtils;
import com.migration.validator.core.FrameworkUtils.Framework;
import com.migration.validator.model.PersistenceModels.RepoMetadataModel;
import com.migration.validator.model.cldk.AnalysisResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Orchestrates the full repo metadata extraction pipeline:
 * 1. Detect framework (Spring / Quarkus / Jakarta EE)
 * 2. Run CodeAnalyzer to get AnalysisResult
 * 3. Build ConstantsCache
 * 4. Run all extractors appropriate for the detected framework
 * 5. Assemble and return RepoMetadataModel
 */
public class RepoMetadataGenerator {

    private final CodeAnalyzerClient codeAnalyzerClient;

    public RepoMetadataGenerator() {
        this.codeAnalyzerClient = new CodeAnalyzerClient();
    }

    public RepoMetadataGenerator(CodeAnalyzerClient client) {
        this.codeAnalyzerClient = client;
    }

    /**
     * Run analysis and build the complete RepoMetadataModel.
     */
    public RepoMetadataModel generate(Path projectRoot, int analysisLevel)
            throws IOException, InterruptedException {
        // 1. Detect framework
        Framework framework = FrameworkUtils.detectFramework(projectRoot);
        System.out.println("[INFO] Detected framework: " + framework);

        // 2. Analyze project
        AnalysisResult analysis = codeAnalyzerClient.analyzeProject(projectRoot, analysisLevel);
        System.out.println("[INFO] Analysis complete");

        // 3. Build constants cache
        ConstantsCache constantsCache = ConstantsCache.build(analysis);
        Map<String, String> cache = constantsCache.asMap();

        // 4. Extractors always run
        EntityExtractor entityExtractor = new EntityExtractor();
        ServletExtractor servletExtractor = new ServletExtractor();
        ExceptionMapperExtractor exceptionMapperExtractor = new ExceptionMapperExtractor();
        DataSourceExtractor dataSourceExtractor = new DataSourceExtractor();
        EntityManagerExtractor entityManagerExtractor = new EntityManagerExtractor();
        ProjectConfigExtractor projectConfigExtractor = new ProjectConfigExtractor();
        RepositoryExtractor repoExtractor = new RepositoryExtractor();

        RepoMetadataModel model = new RepoMetadataModel();
        model.setEntities(entityExtractor.extract(analysis, cache));
        model.setServlets(servletExtractor.extract(analysis, cache));
        model.setDatasourceUsages(dataSourceExtractor.extract(analysis));
        model.setEntityManagerUsages(entityManagerExtractor.extract(analysis));
        model.setProjectConfig(projectConfigExtractor.extract(analysis, projectRoot));

        // 5. Framework-specific extractors
        if (framework == Framework.SPRING_BOOT) {
            model.setRepositories(repoExtractor.extractSpring(analysis));
            model.setRest(new SpringRestApiExtractor().extract(analysis, projectRoot, cache));
            model.setExceptionMappers(exceptionMapperExtractor.extractSpring(analysis));
        } else if (framework == Framework.QUARKUS) {
            model.setRepositories(repoExtractor.extractQuarkus(analysis));
            model.setRest(new QuarkusRestApiExtractor().extract(analysis, projectRoot, cache));
            model.setExceptionMappers(exceptionMapperExtractor.extractJaxRs(analysis));
        } else {
            // Jakarta EE
            model.setRepositories(repoExtractor.extractJakarta(analysis));
            model.setRest(new JakartaRestApiExtractor().extract(analysis, projectRoot, cache));
            model.setExceptionMappers(exceptionMapperExtractor.extractJaxRs(analysis));
        }

        return model;
    }

    /**
     * Generate and serialize to a YAML file.
     * The output format matches Python yaml.safe_dump(asdict(model),
     * sort_keys=False).
     */
    public void generateYaml(Path projectRoot, Path outputFile, int analysisLevel)
            throws IOException, InterruptedException {
        RepoMetadataModel model = generate(projectRoot, analysisLevel);

        // Use Jackson YAML with MINIMIZE_QUOTES to match Python yaml.safe_dump output
        YAMLFactory yamlFactory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .disable(YAMLGenerator.Feature.USE_NATIVE_OBJECT_ID)
                .disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID)
                .build();
        ObjectMapper yamlMapper = new ObjectMapper(yamlFactory);
        yamlMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Path parent = outputFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        yamlMapper.writeValue(outputFile.toFile(), model);
        System.out.println("[INFO] YAML generated: " + outputFile);
    }
}
