package com.migration.validator.cli.commands.extract;

import com.migration.validator.extractor.generate.RepoMetadataGenerator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * CLI command: extract metadata
 *
 * Extracts repo_metadata.yaml from a Java project by running CodeAnalyzer
 * and all metadata extractors.
 *
 * Usage:
 * java -jar migration-validator.jar extract metadata <projectRoot> [options]
 */
@Command(name = "metadata", description = "Extract repo_metadata.yaml from a Java project", mixinStandardHelpOptions = true)
public class MetadataCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Path to the root of the Java project to analyze")
    private Path projectRoot;

    @Option(names = { "-o",
            "--output" }, description = "Output YAML file path (default: repo_metadata.yaml)", defaultValue = "repo_metadata.yaml")
    private Path outputFile;

    @Option(names = {
            "--analysis-level" }, description = "CodeAnalyzer analysis level: 1 or 2 (default: 1)", defaultValue = "1")
    private int analysisLevel;

    @Override
    public Integer call() {
        try {
            Path resolvedProject = projectRoot.toAbsolutePath().normalize();
            Path resolvedOutput = outputFile.isAbsolute()
                    ? outputFile
                    : Paths.get(System.getProperty("user.dir")).resolve(outputFile).normalize();

            RepoMetadataGenerator generator = new RepoMetadataGenerator();
            generator.generateYaml(resolvedProject, resolvedOutput, analysisLevel);
            return 0;
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to generate repo metadata: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }
    }
}
