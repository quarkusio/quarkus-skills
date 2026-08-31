---
name: validation-agent
description: Phase 11 Validation Agent. Validates the migrated Quarkus application through compilation, packaging, and smoke tests.
  Runs all validators and ensures application starts successfully.
license: Apache-2.0
metadata:
  phase: 11
  agent_type: validation
---

# Phase 11 — Validation Agent

## Purpose

Validate that the migrated Quarkus application compiles, packages, and runs correctly.

> **Execution mode:** This agent has **no interactive approval gate**. "Wait for the application to start"
> below is a runtime readiness wait (waiting on the JVM), not a pause for user input, and runs identically
> in `interactive` and `autonomous` modes. When validation FAILS, the orchestrator applies its mode-aware
> gate policy (SKILL.md → Error Resolution Process): interactive pauses and asks; autonomous attempts
> compile-fix, then records any unresolved failure under `unresolved_issues:` and continues.

## ⚠️ CRITICAL: Output File Location

**YOU MUST save the validation report to this exact location:**

```
<quarkus_target_dir>/migration-reports/phase-11-validation.json
```

**Before creating the report:**
1. Ensure the `migration-reports/` directory exists (create it if needed)
2. Save the report to the exact path above
3. Do NOT save to the root directory
4. Do NOT use any other filename

## Steps

### 1. Compilation Validation
Run `mvn clean compile` to verify code compiles without errors.

### 2. Package Validation
Run `mvn clean package -DskipTests` to ensure the application packages successfully.

### 3. Development Mode Deployment Validation (REQUIRED)
**This step is MANDATORY and must be performed:**

```bash
cd <quarkus_target_dir>
mvn quarkus:dev -Ddebug=false
```

**Wait for the application to start completely.** Look for these indicators:
- "Quarkus X.X.X started in X.XXXs"
- "Listening on: http://localhost:8080"
- No ERROR or WARN messages in startup logs

**Capture startup logs** (first 100 lines after "Quarkus started"):
```bash
# The logs will show if there are any runtime errors
```

**Check for common issues:**
- ❌ Bean injection failures
- ❌ Database connection errors
- ❌ Missing configuration properties
- ❌ Class loading errors
- ❌ Port binding failures

**If any errors occur during startup, the validation FAILS.**

### 4. Health Check Validation
While `quarkus:dev` is running, verify health endpoint:
```bash
curl http://localhost:8080/q/health
```

Expected response: `{"status":"UP",...}`

### 5. Smoke Tests (if available)
Run any smoke tests or basic endpoint checks.

### 6. Stop Development Mode
After validation, stop the `quarkus:dev` process (Ctrl+C).

### 7. Verify Transformation Ledger Coverage
Check that all components were migrated.

### 8. Generate validation-report.json
Document all validation results.

## Validation Checks

### Build Validation (REQUIRED)
- ✅ Compilation succeeds (`mvn clean compile`)
- ✅ No compilation errors
- ✅ Package creation succeeds (`mvn clean package -DskipTests`)

### Development Mode Deployment Validation (REQUIRED)
**This is the most critical validation step:**
- ✅ Application starts in dev mode (`mvn quarkus:dev`)
- ✅ No ERROR messages in startup logs
- ✅ No WARN messages about missing beans or configuration
- ✅ Application reaches "started" state
- ✅ Port 8080 is listening
- ✅ No runtime exceptions during startup

**Common Startup Errors to Check For:**
1. **Bean Injection Errors**: "Unsatisfied dependency", "No bean found"
2. **Database Errors**: "Unable to create connection", "Unknown database"
3. **Configuration Errors**: "Required property not set", "Invalid configuration"
4. **Class Loading Errors**: "ClassNotFoundException", "NoClassDefFoundError"
5. **Port Conflicts**: "Address already in use"

### Runtime Validation (REQUIRED)
- ✅ Health endpoint responds (`/q/health`)
- ✅ Application runs without errors for at least 30 seconds
- ✅ Database connection works (if applicable)
- ✅ Messaging channels active (if applicable)

### Optional Validation
- REST endpoints accessible (if smoke tests available)
- Basic functionality tests (if available)

### Coverage Validation
- All controllers migrated
- All services migrated
- All repositories migrated
- All entities migrated
- Configuration complete

## Output

**Directory Setup:**
```bash
mkdir -p migration-reports
```

**File Location:** `migration-reports/phase-11-validation.json`

This report should be created in the target Quarkus project at `<quarkus_target_dir>/migration-reports/phase-11-validation.json`.

```json
{
  "phase": "validation",
  "status": "PASS",
  "timestamp": "2024-01-15T10:30:00Z",
  "build_validation": {
    "compilation": "PASS",
    "packaging": "PASS",
    "errors": []
  },
  "deployment_validation": {
    "quarkus_dev_mode": "PASS",
    "startup_time_seconds": 3.456,
    "startup_errors": [],
    "startup_warnings": [],
    "port_listening": true,
    "health_check": "PASS"
  },
  "runtime_validation": {
    "status": "PASS",
    "uptime_seconds": 45,
    "database_connection": "PASS",
    "errors": []
  },
  "coverage": {
    "controllers": "100%",
    "services": "100%",
    "repositories": "100%",
    "entities": "100%"
  },
  "issues": [],
  "recommendations": []
}
```

Then update `migration-metadata/migration-context.json`:
- Add `"11-validation"` to `completedPhases`
- Set `phaseReports["11-validation"]` to `"migration-reports/phase-11-validation.json"`