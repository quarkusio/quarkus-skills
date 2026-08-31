package com.migration.validator.extractor.generate;

import com.migration.validator.extractor.ServerConfigExtractor;
import com.migration.validator.model.RestModels.*;
import com.migration.validator.model.ServerConfigModels.ServerConfig;
import com.migration.validator.model.cldk.AnalysisResult;

import java.nio.file.Path;
import java.util.Map;

/**
 * Jakarta EE JAX-RS extractor.
 * Extends JAX-RS logic and merges context root from server configuration.
 */
public class JakartaRestApiExtractor {

    private final JaxRsRestApiExtractor jaxRsExtractor = new JaxRsRestApiExtractor();

    public RestModel extract(AnalysisResult analysis, Path projectRoot,
            Map<String, String> constantsCache) {
        RestModel model = jaxRsExtractor.extract(analysis, constantsCache);

        String rootPath = model.getRootPath();
        try {
            ServerConfigExtractor serverConfigExtractor = new ServerConfigExtractor(projectRoot.toString());
            ServerConfig serverConfig = serverConfigExtractor.extractAll();
            String configRootPath = serverConfig.getContextRoot();
            if (configRootPath != null) {
                configRootPath = configRootPath.strip();
                while (configRootPath.endsWith("/"))
                    configRootPath = configRootPath.substring(0, configRootPath.length() - 1);
            }
            if (rootPath != null && configRootPath != null) {
                String rp = rootPath.startsWith("/") ? rootPath : "/" + rootPath;
                model.setRootPath(configRootPath + rp);
            } else {
                model.setRootPath(rootPath != null ? rootPath : configRootPath);
            }
        } catch (Exception e) {
            // If server config extraction fails, keep the JAX-RS root path as-is
        }

        return model;
    }
}
