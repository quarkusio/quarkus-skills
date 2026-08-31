package com.migration.validator.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.migration.validator.model.ServerConfigModels.DataSourceConfig;
import com.migration.validator.model.ServerConfigModels.ServerConfig;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * Extract minimal database configuration and context root from Jakarta EE
 * server-specific configuration files.
 * 
 * This extractor extracts only the essential information needed for validation:
 * - JDBC URL (to detect database type and verify Quarkus properties)
 * - Driver class (to confirm correct JDBC driver dependency)
 * - Database type (postgresql, mysql, db2, etc.)
 * - Context root (application deployment path)
 * 
 * Supports extraction from:
 * - WebSphere/Liberty: server.xml, resources.xml (context root from
 * webApplication/application elements)
 * - JBoss/WildFly: standalone.xml, domain.xml, *-ds.xml, jboss-web.xml (context
 * root)
 * - Tomcat: context.xml, server.xml (context root from Context path attribute)
 * - WebLogic: config.xml, jdbc/*.xml, weblogic.xml (context root)
 * 
 */
public class ServerConfigExtractor {

    // Database type detection patterns
    private static final Map<String, List<String>> DB_TYPE_PATTERNS = new HashMap<>();
    static {
        DB_TYPE_PATTERNS.put("postgresql", Arrays.asList("postgresql", "postgres"));
        DB_TYPE_PATTERNS.put("mysql", Collections.singletonList("mysql"));
        DB_TYPE_PATTERNS.put("mariadb", Collections.singletonList("mariadb"));
        DB_TYPE_PATTERNS.put("oracle", Arrays.asList("oracle", "ojdbc"));
        DB_TYPE_PATTERNS.put("db2", Arrays.asList("db2", "com.ibm.db2"));
        DB_TYPE_PATTERNS.put("sqlserver", Arrays.asList("sqlserver", "mssql", "microsoft.sqlserver"));
        DB_TYPE_PATTERNS.put("h2", Arrays.asList("h2database", "h2"));
        DB_TYPE_PATTERNS.put("derby", Arrays.asList("derby", "javadb"));
        DB_TYPE_PATTERNS.put("sybase", Collections.singletonList("sybase"));
    }

    // JDBC driver class to database type mapping
    private static final Map<String, String> DRIVER_CLASS_MAPPING = new HashMap<>();
    static {
        DRIVER_CLASS_MAPPING.put("org.postgresql.Driver", "postgresql");
        DRIVER_CLASS_MAPPING.put("com.mysql.jdbc.Driver", "mysql");
        DRIVER_CLASS_MAPPING.put("com.mysql.cj.jdbc.Driver", "mysql");
        DRIVER_CLASS_MAPPING.put("org.mariadb.jdbc.Driver", "mariadb");
        DRIVER_CLASS_MAPPING.put("oracle.jdbc.driver.OracleDriver", "oracle");
        DRIVER_CLASS_MAPPING.put("oracle.jdbc.OracleDriver", "oracle");
        DRIVER_CLASS_MAPPING.put("com.ibm.db2.jcc.DB2Driver", "db2");
        DRIVER_CLASS_MAPPING.put("com.microsoft.sqlserver.jdbc.SQLServerDriver", "sqlserver");
        DRIVER_CLASS_MAPPING.put("org.h2.Driver", "h2");
        DRIVER_CLASS_MAPPING.put("org.apache.derby.jdbc.EmbeddedDriver", "derby");
        DRIVER_CLASS_MAPPING.put("org.apache.derby.jdbc.ClientDriver", "derby");
        DRIVER_CLASS_MAPPING.put("com.sybase.jdbc4.jdbc.SybDriver", "sybase");
    }

    private static final Set<String> EXCLUDED_DIRS = new HashSet<>(Arrays.asList(
            "target", "build", "test", "tests", ".git", ".svn",
            "node_modules", "dist", "out", ".idea", ".vscode"));

    private final Path projectRoot;
    private final List<DataSourceConfig> datasources = new ArrayList<>();
    private String contextRoot = null;

    public ServerConfigExtractor(String projectRoot) {
        this.projectRoot = Paths.get(projectRoot);
    }

    /**
     * Extract minimal database configuration needed for validation
     */
    public ServerConfig extractAll() {
        extractLibertyConfig();
        extractWebSphereConfig();
        extractJBossConfig();
        extractTomcatConfig();
        extractWebLogicConfig();

        return new ServerConfig(datasources, getDetectedDatabaseTypes(), contextRoot);
    }

    /**
     * Extract datasource config and context root from Liberty/WebSphere Liberty
     * server.xml
     */
    private void extractLibertyConfig() {
        try {
            List<Path> serverXmlPaths = findFiles("server.xml");

            for (Path serverXml : serverXmlPaths) {
                if (isExcludedPath(serverXml)) {
                    continue;
                }

                try {
                    Document doc = parseXml(serverXml.toFile());
                    Element root = doc.getDocumentElement();

                    // Extract context root from application or webApplication elements
                    if (contextRoot == null) {
                        // Check for webApplication element
                        NodeList webApps = root.getElementsByTagName("webApplication");
                        for (int i = 0; i < webApps.getLength(); i++) {
                            Element webApp = (Element) webApps.item(i);
                            String ctxRoot = webApp.getAttribute("contextRoot");
                            if (ctxRoot != null && !ctxRoot.isEmpty()) {
                                contextRoot = ctxRoot;
                                break;
                            }
                        }

                        // Check for application element
                        if (contextRoot == null) {
                            NodeList apps = root.getElementsByTagName("application");
                            for (int i = 0; i < apps.getLength(); i++) {
                                Element app = (Element) apps.item(i);
                                String ctxRoot = app.getAttribute("context-root");
                                if (ctxRoot != null && !ctxRoot.isEmpty()) {
                                    contextRoot = ctxRoot;
                                    break;
                                }
                            }
                        }
                    }

                    // Liberty datasource format
                    NodeList dataSourceNodes = root.getElementsByTagName("dataSource");
                    for (int i = 0; i < dataSourceNodes.getLength(); i++) {
                        Element ds = (Element) dataSourceNodes.item(i);

                        // Extract properties - try different property element types
                        Element props = getFirstChildElement(ds, "properties");
                        if (props == null)
                            props = getFirstChildElement(ds, "properties.db2.jcc");
                        if (props == null)
                            props = getFirstChildElement(ds, "properties.postgresql");
                        if (props == null)
                            props = getFirstChildElement(ds, "properties.oracle");
                        if (props == null)
                            props = getFirstChildElement(ds, "properties.mysql");
                        if (props == null)
                            props = getFirstChildElement(ds, "properties.microsoft.sqlserver");

                        String jdbcUrl = null;
                        String driverClass = null;

                        if (props != null) {
                            jdbcUrl = props.getAttribute("url");
                            if (jdbcUrl == null || jdbcUrl.isEmpty()) {
                                jdbcUrl = props.getAttribute("URL");
                            }
                            driverClass = props.getAttribute("driverClass");
                        }

                        // Try to get from jdbcDriver element
                        Element jdbcDriver = getFirstChildElement(ds, "jdbcDriver");
                        if (jdbcDriver != null && (driverClass == null || driverClass.isEmpty())) {
                            driverClass = jdbcDriver.getAttribute("javax.sql.DataSource");
                        }

                        String driverType = detectDatabaseType(jdbcUrl, driverClass);
                        datasources.add(new DataSourceConfig(jdbcUrl, driverClass, driverType));
                    }

                } catch (Exception e) {
                    System.err.println(
                            "[WARN] Failed to parse Liberty server.xml at " + serverXml + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[WARN] Error finding Liberty server.xml files: " + e.getMessage());
        }
    }

    /**
     * Extract datasource config from WebSphere traditional resources.xml
     */
    private void extractWebSphereConfig() {
        try {
            List<Path> resourcesXmlPaths = findFiles("resources.xml");

            for (Path resourcesXml : resourcesXmlPaths) {
                if (isExcludedPath(resourcesXml)) {
                    continue;
                }

                try {
                    Document doc = parseXml(resourcesXml.toFile());
                    Element root = doc.getDocumentElement();

                    // WebSphere traditional format with namespace
                    NodeList dataSourceNodes = root.getElementsByTagNameNS(
                            "http://www.ibm.com/xmlns/prod/was/resources", "DataSource");

                    for (int i = 0; i < dataSourceNodes.getLength(); i++) {
                        Element ds = (Element) dataSourceNodes.item(i);

                        // Extract properties
                        NodeList propsList = ds.getElementsByTagNameNS(
                                "http://www.ibm.com/xmlns/prod/was/resources", "properties");

                        String jdbcUrl = null;
                        String driverClass = null;

                        if (propsList.getLength() > 0) {
                            Element props = (Element) propsList.item(0);
                            jdbcUrl = props.getAttribute("URL");
                            if (jdbcUrl == null || jdbcUrl.isEmpty()) {
                                jdbcUrl = props.getAttribute("url");
                            }
                            driverClass = props.getAttribute("driverClass");
                        }

                        String driverType = detectDatabaseType(jdbcUrl, driverClass);
                        datasources.add(new DataSourceConfig(jdbcUrl, driverClass, driverType));
                    }

                } catch (Exception e) {
                    System.err.println("[WARN] Failed to parse WebSphere resources.xml at " + resourcesXml + ": "
                            + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[WARN] Error finding WebSphere resources.xml files: " + e.getMessage());
        }
    }

    /**
     * Extract datasource config and context root from JBoss/WildFly standalone.xml
     * or *-ds.xml
     */
    private void extractJBossConfig() {
        try {
            List<Path> configFiles = new ArrayList<>();
            configFiles.addAll(findFiles("standalone.xml"));
            configFiles.addAll(findFiles("standalone-full.xml"));
            configFiles.addAll(findFiles("domain.xml"));
            configFiles.addAll(findFilesWithPattern("*-ds.xml"));

            // Also check jboss-web.xml for context root
            List<Path> jbossWebFiles = findFiles("jboss-web.xml");
            for (Path jbossWeb : jbossWebFiles) {
                if (isExcludedPath(jbossWeb)) {
                    continue;
                }

                try {
                    Document doc = parseXml(jbossWeb.toFile());
                    Element root = doc.getDocumentElement();

                    // Extract context root
                    NodeList ctxRootNodes = root.getElementsByTagName("context-root");
                    if (ctxRootNodes.getLength() > 0 && contextRoot == null) {
                        String text = ctxRootNodes.item(0).getTextContent();
                        if (text != null && !text.trim().isEmpty()) {
                            contextRoot = text.trim();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[WARN] Failed to parse jboss-web.xml at " + jbossWeb + ": " + e.getMessage());
                }
            }

            for (Path configFile : configFiles) {
                if (isExcludedPath(configFile)) {
                    continue;
                }

                try {
                    Document doc = parseXml(configFile.toFile());
                    Element root = doc.getDocumentElement();

                    // JBoss/WildFly datasource format - try with and without namespace
                    String[] namespaces = {
                            "urn:jboss:domain:datasources:1.0",
                            "urn:jboss:domain:datasources:2.0",
                            "urn:jboss:domain:datasources:3.0",
                            "urn:jboss:domain:datasources:4.0",
                            "urn:jboss:domain:datasources:5.0",
                            ""
                    };

                    for (String ns : namespaces) {
                        NodeList dataSourceNodes;
                        if (ns.isEmpty()) {
                            dataSourceNodes = root.getElementsByTagName("datasource");
                        } else {
                            dataSourceNodes = root.getElementsByTagNameNS(ns, "datasource");
                        }

                        for (int i = 0; i < dataSourceNodes.getLength(); i++) {
                            Element ds = (Element) dataSourceNodes.item(i);

                            // Extract connection URL
                            String jdbcUrl = null;
                            NodeList connUrlNodes;
                            if (ns.isEmpty()) {
                                connUrlNodes = ds.getElementsByTagName("connection-url");
                            } else {
                                connUrlNodes = ds.getElementsByTagNameNS(ns, "connection-url");
                            }
                            if (connUrlNodes.getLength() > 0) {
                                jdbcUrl = connUrlNodes.item(0).getTextContent();
                            }

                            // Extract driver class
                            String driverClass = null;
                            NodeList driverNodes;
                            if (ns.isEmpty()) {
                                driverNodes = ds.getElementsByTagName("driver-class");
                            } else {
                                driverNodes = ds.getElementsByTagNameNS(ns, "driver-class");
                            }
                            if (driverNodes.getLength() > 0) {
                                driverClass = driverNodes.item(0).getTextContent();
                            }

                            String driverType = detectDatabaseType(jdbcUrl, driverClass);
                            datasources.add(new DataSourceConfig(jdbcUrl, driverClass, driverType));
                        }
                    }

                } catch (Exception e) {
                    System.err.println("[WARN] Failed to parse JBoss config at " + configFile + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[WARN] Error finding JBoss config files: " + e.getMessage());
        }
    }

    /**
     * Extract datasource config and context root from Tomcat context.xml or
     * server.xml
     */
    private void extractTomcatConfig() {
        try {
            List<Path> configFiles = new ArrayList<>();
            configFiles.addAll(findFiles("context.xml"));
            configFiles.addAll(findFiles("server.xml"));

            for (Path configFile : configFiles) {
                if (isExcludedPath(configFile)) {
                    continue;
                }

                try {
                    Document doc = parseXml(configFile.toFile());
                    Element root = doc.getDocumentElement();

                    // Extract context root from Context element
                    if (contextRoot == null && "Context".equals(root.getTagName())) {
                        String path = root.getAttribute("path");
                        if (path != null && !path.isEmpty()) {
                            contextRoot = path;
                        }
                    }

                    // Tomcat Resource format
                    NodeList resourceNodes = root.getElementsByTagName("Resource");
                    for (int i = 0; i < resourceNodes.getLength(); i++) {
                        Element resource = (Element) resourceNodes.item(i);

                        String resType = resource.getAttribute("type");
                        // Only process DataSource resources
                        if (resType == null || !resType.contains("DataSource")) {
                            continue;
                        }

                        String jdbcUrl = resource.getAttribute("url");
                        String driverClass = resource.getAttribute("driverClassName");

                        String driverType = detectDatabaseType(jdbcUrl, driverClass);
                        datasources.add(new DataSourceConfig(jdbcUrl, driverClass, driverType));
                    }

                } catch (Exception e) {
                    System.err.println("[WARN] Failed to parse Tomcat config at " + configFile + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[WARN] Error finding Tomcat config files: " + e.getMessage());
        }
    }

    /**
     * Extract datasource config and context root from WebLogic config.xml or
     * jdbc/*.xml
     */
    private void extractWebLogicConfig() {
        try {
            List<Path> configFiles = new ArrayList<>();
            configFiles.addAll(findFiles("config.xml"));
            // Find JDBC XML files in jdbc directories
            configFiles.addAll(findFilesInDirectory("jdbc", ".xml"));

            // Check weblogic.xml for context root
            List<Path> weblogicXmlFiles = findFiles("weblogic.xml");
            for (Path weblogicXml : weblogicXmlFiles) {
                if (isExcludedPath(weblogicXml)) {
                    continue;
                }

                try {
                    Document doc = parseXml(weblogicXml.toFile());
                    Element root = doc.getDocumentElement();

                    // Extract context root
                    NodeList ctxRootNodes = root.getElementsByTagName("context-root");
                    if (ctxRootNodes.getLength() > 0 && contextRoot == null) {
                        String text = ctxRootNodes.item(0).getTextContent();
                        if (text != null && !text.trim().isEmpty()) {
                            contextRoot = text.trim();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[WARN] Failed to parse weblogic.xml at " + weblogicXml + ": " + e.getMessage());
                }
            }

            for (Path configFile : configFiles) {
                if (isExcludedPath(configFile)) {
                    continue;
                }

                try {
                    Document doc = parseXml(configFile.toFile());
                    Element root = doc.getDocumentElement();

                    // WebLogic JDBC DataSource format
                    NodeList dataSourceNodes = root.getElementsByTagName("jdbc-data-source");
                    for (int i = 0; i < dataSourceNodes.getLength(); i++) {
                        Element ds = (Element) dataSourceNodes.item(i);

                        // Extract connection properties
                        String jdbcUrl = null;
                        NodeList urlNodes = ds.getElementsByTagName("url");
                        if (urlNodes.getLength() > 0) {
                            jdbcUrl = urlNodes.item(0).getTextContent();
                        }

                        String driverClass = null;
                        NodeList driverNodes = ds.getElementsByTagName("driver-name");
                        if (driverNodes.getLength() > 0) {
                            driverClass = driverNodes.item(0).getTextContent();
                        }

                        String driverType = detectDatabaseType(jdbcUrl, driverClass);
                        datasources.add(new DataSourceConfig(jdbcUrl, driverClass, driverType));
                    }

                } catch (Exception e) {
                    System.err
                            .println("[WARN] Failed to parse WebLogic config at " + configFile + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[WARN] Error finding WebLogic config files: " + e.getMessage());
        }
    }

    /**
     * Detect database type from JDBC URL or driver class
     */
    private String detectDatabaseType(String jdbcUrl, String driverClass) {
        // Try driver class first (more reliable)
        if (driverClass != null && !driverClass.isEmpty()) {
            String dbType = DRIVER_CLASS_MAPPING.get(driverClass);
            if (dbType != null) {
                return dbType;
            }
        }

        // Fall back to URL pattern matching
        if (jdbcUrl != null && !jdbcUrl.isEmpty()) {
            String urlLower = jdbcUrl.toLowerCase();
            for (Map.Entry<String, List<String>> entry : DB_TYPE_PATTERNS.entrySet()) {
                for (String pattern : entry.getValue()) {
                    if (urlLower.contains(pattern)) {
                        return entry.getKey();
                    }
                }
            }
        }

        return null;
    }

    /**
     * Get set of all detected database types
     */
    private Set<String> getDetectedDatabaseTypes() {
        Set<String> types = new HashSet<>();
        for (DataSourceConfig ds : datasources) {
            if (ds.getDriverType() != null) {
                types.add(ds.getDriverType());
            }
        }
        return types;
    }

    /**
     * Check if path should be excluded from processing
     */
    private boolean isExcludedPath(Path path) {
        for (Path part : path) {
            if (EXCLUDED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find all files with given name recursively
     */
    private List<Path> findFiles(String fileName) throws Exception {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .forEach(result::add);
        }
        return result;
    }

    /**
     * Find all files matching pattern recursively
     */
    private List<Path> findFilesWithPattern(String pattern) throws Exception {
        List<Path> result = new ArrayList<>();
        String regex = pattern.replace("*", ".*");
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches(regex))
                    .forEach(result::add);
        }
        return result;
    }

    /**
     * Find all files with given extension in directories with given name
     */
    private List<Path> findFilesInDirectory(String dirName, String extension) throws Exception {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().contains(File.separator + dirName + File.separator))
                    .filter(p -> p.getFileName().toString().endsWith(extension))
                    .forEach(result::add);
        }
        return result;
    }

    /**
     * Parse XML file
     */
    private Document parseXml(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(file);
    }

    /**
     * Get first child element with given tag name
     */
    private Element getFirstChildElement(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            Node node = nodes.item(0);
            if (node instanceof Element) {
                return (Element) node;
            }
        }
        return null;
    }
}
