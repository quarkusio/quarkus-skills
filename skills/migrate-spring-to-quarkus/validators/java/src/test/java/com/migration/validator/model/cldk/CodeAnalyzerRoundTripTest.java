package com.migration.validator.model.cldk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.migration.validator.core.CodeAnalyzerClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration test that validates the fidelity of the cldk model classes
 * by performing a JSON round-trip comparison against raw codeanalyzer.jar
 * output.
 *
 * Uses {@link CodeAnalyzerClient} to invoke the jar and get both:
 * - the raw JSON string (via {@code analyzeProjectRaw})
 * - the deserialized {@link AnalysisResult} (via {@code analyzeProject})
 *
 * Then re-serializes the model and compares against the raw JSON tree.
 * Any fields present in the raw output but lost in the round-trip indicate
 * gaps in the model classes.
 */
public class CodeAnalyzerRoundTripTest {

    // Use the validators/java project itself as the target (user.dir is
    // validators/java when run via Maven)
    private static final Path TARGET_PROJECT = Paths.get(System.getProperty("user.dir"));

    private static CodeAnalyzerClient client;

    @BeforeAll
    static void setUp() {
        client = new CodeAnalyzerClient();
        Assumptions.assumeTrue(Files.isDirectory(TARGET_PROJECT),
                "Target project not found at " + TARGET_PROJECT);
    }

    @Test
    void roundTripAnalysisLevel1() throws Exception {
        assertRoundTripFidelity(1);
    }

    @Test
    void roundTripAnalysisLevel2() throws Exception {
        assertRoundTripFidelity(2);
    }

    private void assertRoundTripFidelity(int analysisLevel) throws Exception {
        // 1. Get raw JSON from codeanalyzer.jar
        String rawJson = client.analyzeProjectRaw(TARGET_PROJECT, analysisLevel);
        assertTrue(rawJson.startsWith("{"), "Expected JSON object output from codeanalyzer.jar but got: "
                + rawJson.substring(0, Math.min(200, rawJson.length())));

        // 2. Deserialize into model and re-serialize
        ObjectMapper mapper = client.getObjectMapper();
        AnalysisResult result = mapper.readValue(rawJson, AnalysisResult.class);

        ObjectMapper writer = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        String roundTrippedJson = writer.writeValueAsString(result);

        // 3. Parse both as trees and compare
        JsonNode rawTree = mapper.readTree(rawJson);
        JsonNode roundTrippedTree = mapper.readTree(roundTrippedJson);

        List<String> differences = new ArrayList<>();
        compareNodes(rawTree, roundTrippedTree, "$", differences);

        if (!differences.isEmpty()) {
            StringBuilder report = new StringBuilder();
            report.append(String.format(
                    "Round-trip comparison failed for analysis-level %d. Found %d difference(s):%n",
                    analysisLevel, differences.size()));
            int shown = Math.min(differences.size(), 50);
            for (int i = 0; i < shown; i++) {
                report.append("  ").append(differences.get(i)).append("\n");
            }
            if (differences.size() > shown) {
                report.append(String.format("  ... and %d more%n", differences.size() - shown));
            }
            fail(report.toString());
        }
    }

    /**
     * Recursively compare two JsonNode trees. Reports paths where:
     * - A key exists in expected but is missing from actual (field lost in
     * round-trip)
     * - Values differ between expected and actual
     */
    private void compareNodes(JsonNode expected, JsonNode actual, String path, List<String> differences) {
        if (expected == null && actual == null)
            return;
        if (expected == null) {
            differences.add(path + ": extra field in round-tripped output (not in raw)");
            return;
        }
        if (actual == null) {
            differences.add(path + ": MISSING from round-tripped output (present in raw)");
            return;
        }

        if (expected.getNodeType() != actual.getNodeType()) {
            differences.add(path + ": type mismatch — raw=" + expected.getNodeType()
                    + ", round-tripped=" + actual.getNodeType());
            return;
        }

        if (expected.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String childPath = path + "." + entry.getKey();
                if (!actual.has(entry.getKey())) {
                    differences.add(childPath + ": MISSING from round-tripped output");
                } else {
                    compareNodes(entry.getValue(), actual.get(entry.getKey()), childPath, differences);
                }
            }
        } else if (expected.isArray()) {
            if (expected.size() != actual.size()) {
                differences.add(path + ": array size mismatch — raw=" + expected.size()
                        + ", round-tripped=" + actual.size());
            } else {
                for (int i = 0; i < expected.size(); i++) {
                    compareNodes(expected.get(i), actual.get(i), path + "[" + i + "]", differences);
                }
            }
        } else {
            if (!expected.equals(actual)) {
                differences.add(path + ": value mismatch — raw=" + expected.asText()
                        + ", round-tripped=" + actual.asText());
            }
        }
    }
}
