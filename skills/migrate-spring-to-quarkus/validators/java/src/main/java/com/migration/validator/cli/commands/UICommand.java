package com.migration.validator.cli.commands;

import com.migration.validator.UIValidator;
import com.migration.validator.ui.UIMigrationType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Command for validating Phase 8b: UI Migration.
 * 
 * Usage:
 * java -jar migration-validator-1.0.0.jar validate ui \
 * <source_dir> <target_dir> <migration_type> <migration-spec.yaml> [--verbose]
 * 
 * Migration types: jsp-qute, thymeleaf-qute, freemarker-qute, jsf-qute,
 * jsf-myfaces
 */
@Command(name = "ui", description = "Validate Phase 8b: UI Migration - Verifies view technology migration (JSP/JSF/Thymeleaf/FreeMarker → Qute or MyFaces)", mixinStandardHelpOptions = true)
public class UICommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Spring project source directory")
    private Path sourceDir;

    @Parameters(index = "1", description = "Quarkus project target directory")
    private Path targetDir;

    @Parameters(index = "2", description = "Migration type: ${COMPLETION-CANDIDATES}")
    private UIMigrationType migrationType;

    @Parameters(index = "3", description = "Path to migration-spec.yaml file")
    private Path specPath;

    @Option(names = { "-v", "--verbose" }, description = "Enable verbose output with detailed logging")
    private boolean verbose;

    @Override
    public Integer call() throws Exception {
        UIValidator validator = new UIValidator(sourceDir, targetDir, migrationType.getValue(), specPath);
        return validator.validate(verbose);
    }
}
