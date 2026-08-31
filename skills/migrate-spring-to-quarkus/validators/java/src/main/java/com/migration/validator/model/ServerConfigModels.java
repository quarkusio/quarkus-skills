package com.migration.validator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Model classes for server configuration extraction.
 * These models represent database configuration extracted from Jakarta EE
 * server-specific files.
 */
public class ServerConfigModels {

    /**
     * Minimal datasource configuration for validation purposes
     */
    public static class DataSourceConfig {
        @JsonProperty("jdbc_url")
        private String jdbcUrl;

        @JsonProperty("driver_class")
        private String driverClass;

        @JsonProperty("driver_type")
        private String driverType; // e.g., "postgresql", "mysql", "db2"

        public DataSourceConfig() {
        }

        public DataSourceConfig(String jdbcUrl, String driverClass, String driverType) {
            this.jdbcUrl = jdbcUrl;
            this.driverClass = driverClass;
            this.driverType = driverType;
        }

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public String getDriverClass() {
            return driverClass;
        }

        public void setDriverClass(String driverClass) {
            this.driverClass = driverClass;
        }

        public String getDriverType() {
            return driverType;
        }

        public void setDriverType(String driverType) {
            this.driverType = driverType;
        }
    }

    /**
     * Server configuration containing datasources and context root
     */
    public static class ServerConfig {
        @JsonProperty("datasources")
        private List<DataSourceConfig> datasources = new ArrayList<>();

        @JsonProperty("detected_databases")
        private Set<String> detectedDatabases = new HashSet<>();

        @JsonProperty("context_root")
        private String contextRoot;

        public ServerConfig() {
        }

        public ServerConfig(List<DataSourceConfig> datasources, Set<String> detectedDatabases, String contextRoot) {
            this.datasources = datasources != null ? datasources : new ArrayList<>();
            this.detectedDatabases = detectedDatabases != null ? detectedDatabases : new HashSet<>();
            this.contextRoot = contextRoot;
        }

        public List<DataSourceConfig> getDatasources() {
            return datasources;
        }

        public void setDatasources(List<DataSourceConfig> datasources) {
            this.datasources = datasources;
        }

        public Set<String> getDetectedDatabases() {
            return detectedDatabases;
        }

        public void setDetectedDatabases(Set<String> detectedDatabases) {
            this.detectedDatabases = detectedDatabases;
        }

        public String getContextRoot() {
            return contextRoot;
        }

        public void setContextRoot(String contextRoot) {
            this.contextRoot = contextRoot;
        }
    }
}
