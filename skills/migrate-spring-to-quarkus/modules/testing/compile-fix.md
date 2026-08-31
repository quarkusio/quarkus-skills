---
name: compile-fix-agent
description: Compile Fix Agent. Automatically fixes compilation errors after migration phases.
  Analyzes compiler output, applies fixes iteratively, and flags complex cases for manual review.
license: Apache-2.0
metadata:
  phase: any
  agent_type: fix
---

# Compile Fix Agent

## Purpose

Automatically fix compilation errors introduced during migration.

## Inputs

- Compilation error output
- Failed file paths

## Steps

1. Run the project's compile command and capture errors:
   - Maven: `mvn clean compile -DskipTests`
   - Gradle: `./gradlew clean compileJava -x test`
2. Parse error messages
3. Identify error types:
   - Missing imports
   - Incorrect annotations
   - Type mismatches
   - Method signature issues
4. Apply automatic fixes
5. Retry compilation (max 3 attempts per file)
6. If an error still fails after 3 attempts → emit the `MANUAL_REVIEW_REQUIRED` block (see below), then act according to the execution `mode` (read from `migration-spec.yaml` → `execution.mode`; see SKILL.md → EXECUTION MODE):
   - **`interactive`** — ask the user: *"I was unable to automatically fix `File.java`. Would you like me to continue fixing the remaining files, or stop here?"* Wait for the response before proceeding.
   - **`autonomous`** — do **not** pause or ask. Record the failure under `unresolved_issues:` in `migration-spec.yaml` (phase, file, `attempted_fixes: 3`, `severity: ERROR`) and continue automatically to the next file. All failures are surfaced in the final Migration Report.
7. Generate compile-fix-report.json


## Common Error Patterns

### Missing or wrong import (`cannot find symbol`, `package does not exist`)

```java
// BEFORE: Spring / javax
import javax.persistence.Entity;
import javax.inject.Inject;
import org.springframework.stereotype.Service;

// AFTER: Quarkus / jakarta
import jakarta.persistence.Entity;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
```

### Incorrect or unknown annotation (`annotation type not applicable`)

```java
// Fix annotation usage
@PathParam("id") // instead of @PathVariable
@QueryParam("name") // instead of @RequestParam
```

### Type mismatch (`incompatible types`, `cannot convert`)

Typically caused by return-type changes after repository migration:

```java
// BEFORE: Spring Data — returns Optional<T>
Optional<Todo> result = repository.findById(id);

// AFTER: Panache — findById returns T directly (null if not found)
Todo result = Todo.findById(id);
```

### Method signature issue (`method not found`, `wrong number of arguments`)

```java
// BEFORE: Spring Data derived query
List<Todo> findByCompleted(boolean completed);

// AFTER: Panache
List<Todo> findByCompleted(boolean completed) {
    return list("completed", completed);
}
```

## MANUAL_REVIEW_REQUIRED

When a file cannot be fixed after 3 attempts, always emit:

```
MANUAL_REVIEW_REQUIRED: <relative/path/to/File.java>
Reason: <paste the compiler error here>
Attempted fixes: <brief description of what was tried>
```

Then act according to the current mode (see **step 6** above).

## Output

**Directory Setup:**
```bash
mkdir -p migration-reports
```

**File Location:** `migration-reports/compile-fix-report.json`

This report should be created in the target Quarkus project at `<quarkus_target_dir>/migration-reports/compile-fix-report.json`.

```json
{
  "phase": "compile-fix",
  "attempts": 2,
  "files_fixed": 5,
  "manual_review": [],
  "package_status": "PASS"
}