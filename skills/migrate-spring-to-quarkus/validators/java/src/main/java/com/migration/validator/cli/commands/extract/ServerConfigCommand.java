package com.migration.validator.cli.commands.extract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.migration.validator.extractor.ServerConfigExtractor;
import com.migration.validator.model.ServerConfigModels.ServerConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * CLI command to extract server configuration metadata.
 * 
 * Extracts minimal database configuration and context root from Jakarta EE
 * server-specific configuration files:
 * - JDBC URL (to detect database type and verify Quarkus properties)
 * - Driver class (to confirm correct JDBC driver dependency)
 * - Database type (postgresql, mysql, db2, etc.)
 * - Context root (application deployment path)
 * 
 * Supports extraction from:
 * - WebSphere/Liberty: server.xml, resources.xml
 * - JBoss/WildFly: standalone.xml, domain.xml, *-ds.xml, jboss-web.xml
 * - Tomcat: context.xml, server.xml
 * - WebLogic: config.xml, jdbc/*.xml, weblogic.xml
 * 
 * Usage:
 * java -jar migration-validator-1.0.0.jar extract server-config <project_root>
 * <output_file>
 * 
 * Example:
 * java -jar migration-validator-1.0.0.jar extract server-config \
 * /path/to/project \
 * server-config.json
 */
@Command(name = "server-config", description = "Extract server configuration (datasources, context root)", mixinStandardHelpOptions = true)
public class ServerConfigCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory")
    private String projectRoot;

    @Parameters(index = "1", description = "Output JSON file path")
    private String outputFile;

    @Option(names = { "-v", "--verbose" }, description = "Enable verbose output with detailed logging")
    private boolean verbose;

    @Override
    public Integer call() throws Exception {
        try {
            System.out.println("=" + "=".repeat(79));
            System.out.println("SERVER CONFIGURATION EXTRACTOR");
            System.out.println("=" + "=".repeat(79));
            System.out.println("Project Root: " + projectRoot);
            System.out.println("Output File: " + outputFile + "\n");

            // Extract configuration
            ServerConfigExtractor extractor = new ServerConfigExtractor(projectRoot);
            ServerConfig config = extractor.extractAll();

            // Write to JSON file
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(new File(outputFile), config);

            System.out.println("\n📄 Configuration exported to: " + outputFile);
            System.out.println("   Found " + config.getDatasources().size() + " datasource(s)");
            System.out.println("   Detected databases: " + config.getDetectedDatabases());
            if (config.getContextRoot() != null) {
                System.out.println("   Context root: " + config.getContextRoot());
            }
            System.out.println("\n" + "=".repeat(80));

            return 0;

        } catch (Exception e) {
            System.err.println("❌ Error extracting server configuration: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}
