package com.migration.validator.core;

import java.time.Instant;
import java.util.*;

/**
 * Collects validation results and generates reports.
 * Tracks passed/failed checks and provides summary output.
 */
public class ValidationReport {
    private final List<Evidence> evidenceList = new ArrayList<>();
    private int passed = 0;
    private int failed = 0;
    private int warned = 0;

    /**
     * Record a passed validation check.
     * 
     * @param rule     Rule name/identifier
     * @param evidence Evidence or description of what was checked
     */
    public void pass(String rule, String evidence) {
        evidenceList.add(new Evidence(rule, true, evidence));
        passed++;
    }

    /**
     * Record a failed validation check.
     * 
     * @param rule     Rule name/identifier
     * @param evidence Evidence or description of the failure
     */
    public void fail(String rule, String evidence) {
        evidenceList.add(new Evidence(rule, false, evidence));
        failed++;
    }

    /**
     * Record a non-blocking warning.
     *
     * @param rule     Rule name/identifier
     * @param evidence Evidence or description of the warning
     */
    public void warn(String rule, String evidence) {
        evidenceList.add(new Evidence(rule, false, evidence, true));
        warned++;
    }

    /**
     * Check if any validation checks failed.
     * 
     * @return true if there are failures
     */
    public boolean hasFailures() {
        return failed > 0;
    }

    /**
     * Get overall validation status.
     * 
     * @return "success" if no failures, "partial" if some passed, "failed" if
     *         mostly failed
     */
    public String getStatus() {
        if (failed == 0) {
            return "success";
        } else if (passed >= failed) {
            return "partial";
        } else {
            return "failed";
        }
    }

    /**
     * Get number of passed checks.
     */
    public int getPassed() {
        return passed;
    }

    /**
     * Get number of failed checks.
     */
    public int getFailed() {
        return failed;
    }

    /**
     * Get total number of checks.
     */
    public int getTotal() {
        return passed + failed;
    }

    /**
     * Get number of warnings.
     */
    public int getWarned() {
        return warned;
    }

    /**
     * Get all evidence records.
     */
    public List<Evidence> getEvidenceList() {
        return Collections.unmodifiableList(evidenceList);
    }

    /**
     * Print formatted summary to console.
     * 
     * @param phase Phase name for the report header
     */
    public void printSummary(String phase) {
        String separator = "=".repeat(70);

        System.out.println(separator);
        System.out.println("Verification Summary — " + phase);
        System.out.println(separator);
        System.out.println("Status  : " + getStatus().toUpperCase());
        System.out.println("Rules   : " + getTotal() + " total  |  " +
                passed + " passed  |  " + failed + " failed  |  " + warned + " warnings\n");

        for (Evidence e : evidenceList) {
            String mark = e.passed ? "✓" : (e.warning ? "⚠" : "✗");
            System.out.println("  " + mark + " " + e.rule);

            // Indent multi-line evidence
            for (String line : e.evidence.split("\n")) {
                System.out.println("      " + line);
            }
        }

        System.out.println(separator);
    }

    /**
     * Convert report to Map for YAML serialization.
     * 
     * @param phase Phase name
     * @return Map representation of the report
     */
    public Map<String, Object> toMap(String phase) {
        Map<String, Object> result = new HashMap<>();
        result.put("phase", phase);
        result.put("run_at", Instant.now().toString());
        result.put("status", getStatus());
        result.put("rules_total", getTotal());
        result.put("rules_passed", passed);
        result.put("rules_failed", failed);
        result.put("rules_warned", warned);

        List<Map<String, Object>> evidenceMapList = new ArrayList<>();
        for (Evidence e : evidenceList) {
            Map<String, Object> evidenceMap = new HashMap<>();
            evidenceMap.put("rule", e.rule);
            evidenceMap.put("passed", e.passed);
            if (e.warning)
                evidenceMap.put("warning", true);
            evidenceMap.put("evidence", e.evidence);
            evidenceMapList.add(evidenceMap);
        }
        result.put("verification_evidence", evidenceMapList);

        return result;
    }

    /**
     * Evidence record for a single validation check.
     */
    public static class Evidence {
        public final String rule;
        public final boolean passed;
        public final boolean warning;
        public final String evidence;

        /** Backward-compatible constructor (non-warning). */
        public Evidence(String rule, boolean passed, String evidence) {
            this(rule, passed, evidence, false);
        }

        public Evidence(String rule, boolean passed, String evidence, boolean warning) {
            this.rule = rule;
            this.passed = passed;
            this.warning = warning;
            this.evidence = evidence;
        }

        public String getRule() {
            return rule;
        }

        public boolean isPassed() {
            return passed;
        }

        public boolean isWarning() {
            return warning;
        }

        public String getEvidence() {
            return evidence;
        }
    }
}
