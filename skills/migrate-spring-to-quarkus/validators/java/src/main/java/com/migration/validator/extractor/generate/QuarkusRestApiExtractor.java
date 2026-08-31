package com.migration.validator.extractor.generate;

import com.migration.validator.core.PropertyUtils;
import com.migration.validator.extractor.ServerConfigExtractor;
import com.migration.validator.model.RestModels.*;
import com.migration.validator.model.cldk.AnalysisResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Quarkus-specific JAX-RS extractor.
 * Extends JAX-RS logic and prefixes the root path from application.properties.
 */
public class QuarkusRestApiExtractor {

    private final JaxRsRestApiExtractor jaxRsExtractor = new JaxRsRestApiExtractor();

    public RestModel extract(AnalysisResult analysis, Path projectRoot,
            Map<String, String> constantsCache) {
        RestModel model = jaxRsExtractor.extract(analysis, constantsCache);

        String rootPath = model.getRootPath();
        String configRootPath = PropertyUtils.findQuarkusRestPath(projectRoot).orElse(null);

        if (configRootPath != null) {
            configRootPath = configRootPath.stripTrailing();
            while (configRootPath.endsWith("/"))
                configRootPath = configRootPath.substring(0, configRootPath.length() - 1);
        }

        if (rootPath != null && configRootPath != null) {
            String rp = rootPath.startsWith("/") ? rootPath : "/" + rootPath;
            model.setRootPath(configRootPath + rp);
        } else {
            model.setRootPath(rootPath != null ? rootPath : configRootPath);
        }

        return model;
    }
}
