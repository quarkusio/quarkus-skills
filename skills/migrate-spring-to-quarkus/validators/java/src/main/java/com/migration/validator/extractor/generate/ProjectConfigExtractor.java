package com.migration.validator.extractor.generate;

import com.migration.validator.core.PropertyUtils;
import com.migration.validator.extractor.ServerConfigExtractor;
import com.migration.validator.model.PersistenceModels.*;
import com.migration.validator.model.ServerConfigModels.DataSourceConfig;
import com.migration.validator.model.ServerConfigModels.ServerConfig;
import com.migration.validator.model.cldk.AnalysisResult;
import com.migration.validator.model.cldk.Import;
import com.migration.validator.model.cldk.JavaCompilationUnit;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Orchestrates extraction of the full ProjectConfigModel:
 * - persistence.xml parsing
 * - application.properties / .yml extraction
 * - pom.xml dependencies
 * - javax.persistence import detection
 * - server configuration datasources
 */
public class ProjectConfigExtractor {

    private static final Set<String> EXCLUDED_DIRS = new HashSet<>(java.util.Arrays.asList(
            "target", "build", "test", "tests", ".git", ".svn", "node_modules", "dist", "out"));

    public ProjectConfigModel extract(AnalysisResult analysis, Path projectRoot) {
        // 1. Try persistence.xml
        PersistenceConfigModel persistenceXmlConfig = extractPersistenceXml(projectRoot);

        // 2. Try application.properties / .yml
        PersistenceConfigModel appPropsConfig = PropertyUtils.extractApplicationProperties(projectRoot);

        // 3. Prefer app properties if they have actual properties; else use
        // persistence.xml
        PersistenceConfigModel persistenceConfig = (appPropsConfig != null && !appPropsConfig.getProperties().isEmpty())
                ? appPropsConfig
                : persistenceXmlConfig;

        // 4. Dependencies from pom.xml
        List<DependencyInfoModel> dependencies = extractDependenciesFromPom(projectRoot);

        // 5. javax.persistence import detection
        List<String> javaxPersistenceFiles = detectJavaxPersistenceImports(analysis);

        // 6. Server configuration
        List<ServerDatasourceModel> serverDatasources = new ArrayList<>();
        try {
            ServerConfigExtractor serverExt = new ServerConfigExtractor(projectRoot.toString());
            ServerConfig serverConfig = serverExt.extractAll();

            // Convert ServerConfigModels.DataSourceConfig -> ServerDatasourceModel
            for (DataSourceConfig ds : serverConfig.getDatasources()) {
                serverDatasources
                        .add(new ServerDatasourceModel(ds.getJdbcUrl(), ds.getDriverClass(), ds.getDriverType()));
            }

            // Enhance persistence config with server datasource info if not already present
            if (persistenceConfig != null && !serverConfig.getDatasources().isEmpty()
                    && persistenceConfig.getDatasourceUrl() == null) {
                DataSourceConfig first = serverConfig.getDatasources().get(0);
                persistenceConfig.setDatasourceUrl(first.getJdbcUrl());
                persistenceConfig.setDatasourceDriver(first.getDriverClass());
                System.out.println("[INFO] Using server datasource config from server.xml");
            }
        } catch (Exception e) {
            System.err.println("[WARN] Failed to extract server config: " + e.getMessage());
        }

        ProjectConfigModel config = new ProjectConfigModel();
        config.setDependencies(dependencies);
        config.setPersistenceConfig(persistenceConfig);
        config.setJavaxPersistenceImportFiles(javaxPersistenceFiles);
        config.setServerDatasources(serverDatasources);
        return config;
    }

    // -----------------------------------------------------------------------
    // persistence.xml parsing
    // -----------------------------------------------------------------------

    private PersistenceConfigModel extractPersistenceXml(Path projectRoot) {
        Path xmlPath = findPersistenceXml(projectRoot);
        if (xmlPath == null) {
            PersistenceConfigModel empty = new PersistenceConfigModel();
            empty.setPersistenceXmlExists(false);
            empty.setProperties(new ArrayList<>());
            return empty;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlPath.toFile());
            Element root = doc.getDocumentElement();

            List<PersistenceProperty> properties = new ArrayList<>();
            String datasourceUrl = null, datasourceDriver = null, datasourceUsername = null;
            String hibernateDialect = null, hibernateDdlAuto = null;
            Boolean hibernateShowSql = null;
            String jtaDataSource = null, transactionType = null;

            // Both javax and jakarta namespaces
            for (String ns : new String[] {
                    "http://xmlns.jcp.org/xml/ns/persistence",
                    "https://jakarta.ee/xml/ns/persistence",
                    "" }) {
                NodeList pus = ns.isEmpty()
                        ? root.getElementsByTagName("persistence-unit")
                        : root.getElementsByTagNameNS(ns, "persistence-unit");
                for (int i = 0; i < pus.getLength(); i++) {
                    Element pu = (Element) pus.item(i);
                    if (transactionType == null)
                        transactionType = pu.getAttribute("transaction-type");

                    NodeList jtaNodes = ns.isEmpty()
                            ? pu.getElementsByTagName("jta-data-source")
                            : pu.getElementsByTagNameNS(ns, "jta-data-source");
                    if (jtaNodes.getLength() > 0 && jtaDataSource == null) {
                        jtaDataSource = jtaNodes.item(0).getTextContent().strip();
                    }
                }

                NodeList props = ns.isEmpty()
                        ? root.getElementsByTagName("property")
                        : root.getElementsByTagNameNS(ns, "property");
                for (int i = 0; i < props.getLength(); i++) {
                    Element prop = (Element) props.item(i);
                    String name = prop.getAttribute("name");
                    String value = prop.getAttribute("value");
                    if (name.isEmpty() || value.isEmpty())
                        continue;

                    PersistenceProperty pp = new PersistenceProperty();
                    pp.setKey(name);
                    pp.setValue(value);
                    properties.add(pp);

                    if (name.contains("jdbc.url"))
                        datasourceUrl = value;
                    else if (name.contains("jdbc.driver"))
                        datasourceDriver = value;
                    else if (name.contains("jdbc.user") || name.contains("jdbc.username"))
                        datasourceUsername = value;
                    else if (name.contains("hibernate.dialect"))
                        hibernateDialect = value;
                    else if (name.contains("hbm2ddl.auto") || name.contains("schema-generation.database.action"))
                        hibernateDdlAuto = value;
                    else if (name.contains("show_sql"))
                        hibernateShowSql = "true".equalsIgnoreCase(value);
                }
            }

            PersistenceConfigModel cfg = new PersistenceConfigModel();
            cfg.setPersistenceXmlExists(true);
            cfg.setProperties(properties);
            cfg.setDatasourceUrl(datasourceUrl);
            cfg.setDatasourceDriver(datasourceDriver);
            cfg.setDatasourceUsername(datasourceUsername);
            cfg.setHibernateDialect(hibernateDialect);
            cfg.setHibernateDdlAuto(hibernateDdlAuto);
            cfg.setHibernateShowSql(hibernateShowSql);
            cfg.setJtaDataSource(jtaDataSource);
            cfg.setTransactionType(transactionType);
            return cfg;

        } catch (Exception e) {
            System.err.println("[WARN] Failed to parse persistence.xml: " + e.getMessage());
            PersistenceConfigModel cfg = new PersistenceConfigModel();
            cfg.setPersistenceXmlExists(true);
            cfg.setProperties(new ArrayList<>());
            return cfg;
        }
    }

    private Path findPersistenceXml(Path projectRoot) {
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("persistence.xml"))
                    .filter(p -> {
                        for (Path part : p) {
                            if (EXCLUDED_DIRS.contains(part.toString()))
                                return false;
                        }
                        return true;
                    })
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // pom.xml dependency extraction
    // -----------------------------------------------------------------------

    private List<DependencyInfoModel> extractDependenciesFromPom(Path projectRoot) {
        List<DependencyInfoModel> deps = new ArrayList<>();
        Path pomPath = projectRoot.resolve("pom.xml");
        if (!Files.exists(pomPath))
            return deps;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomPath.toFile());
            Element root = doc.getDocumentElement();

            // Only the top-level <dependencies> section (not inside profiles)
            NodeList depsSections = root.getChildNodes();
            Element depsElement = null;
            for (int i = 0; i < depsSections.getLength(); i++) {
                if (depsSections.item(i) instanceof Element) {
                    Element el = (Element) depsSections.item(i);
                    if (el.getTagName().endsWith("dependencies")) {
                        depsElement = el;
                        break;
                    }
                }
            }
            if (depsElement == null)
                return deps;

            Set<String> seen = new HashSet<>();
            NodeList depNodes = depsElement.getChildNodes();
            for (int i = 0; i < depNodes.getLength(); i++) {
                if (!(depNodes.item(i) instanceof Element))
                    continue;
                Element dep = (Element) depNodes.item(i);
                if (!dep.getTagName().endsWith("dependency"))
                    continue;

                String groupId = null, artifactId = null, version = null;
                NodeList children = dep.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    if (!(children.item(j) instanceof Element))
                        continue;
                    Element child = (Element) children.item(j);
                    String tag = child.getTagName();
                    if (tag.endsWith("groupId"))
                        groupId = child.getTextContent().trim();
                    else if (tag.endsWith("artifactId"))
                        artifactId = child.getTextContent().trim();
                    else if (tag.endsWith("version"))
                        version = child.getTextContent().trim();
                }
                if (groupId != null && artifactId != null) {
                    String key = groupId + ":" + artifactId;
                    if (!seen.contains(key)) {
                        seen.add(key);
                        deps.add(new DependencyInfoModel(groupId, artifactId, version));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[WARN] Failed to parse pom.xml: " + e.getMessage());
        }
        return deps;
    }

    // -----------------------------------------------------------------------
    // javax.persistence import detection
    // -----------------------------------------------------------------------

    private List<String> detectJavaxPersistenceImports(AnalysisResult analysis) {
        List<String> files = new ArrayList<>();
        if (analysis == null || analysis.symbolTable == null)
            return files;
        for (Map.Entry<String, JavaCompilationUnit> entry : analysis.symbolTable.entrySet()) {
            JavaCompilationUnit cu = entry.getValue();
            if (cu.imports == null)
                continue;
            boolean hasJavax = cu.imports.stream()
                    .anyMatch(imp -> imp.path != null && imp.path.startsWith("javax.persistence."));
            if (hasJavax && cu.filePath != null) {
                files.add(cu.filePath);
            }
        }
        return files;
    }
}
