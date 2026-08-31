---
name: web-views-migration-agent
description: Phase 8B Web Views Migration Agent. Orchestrates view technology migration (JSP/JSF/Thymeleaf/FreeMarker to Qute or MyFaces).
  Delegates to specialized frontend agents and validates with UIValidator.
license: Apache-2.0
metadata:
  phase: 8b
  agent_type: orchestrator
---

# Phase 8B — Web Views Migration Agent

## Purpose

Orchestrate the migration of Spring web view technologies (JSF, JSP, Thymeleaf, FreeMarker) to Quarkus-compatible view technologies by delegating to specialized frontend agents.

## ⚠️ CRITICAL: Output File Location

**YOU MUST save the migration report to this exact location:**

```
<quarkus_target_dir>/migration-reports/phase-08b-web-views-migration.json
```

**Before creating the report:**
1. Ensure the `migration-reports/` directory exists (create it if needed)
2. Save the report to the exact path above
3. Do NOT save to the root directory
4. Do NOT use any other filename

## Inputs

- migration-spec.yaml (`migration_strategy.view_layer` controls strategy override)
- Source view files (JSP, JSF XHTML, Thymeleaf templates, FreeMarker templates)
- Source managed beans

## Transformation Rules

Apply rules from transformation_rules.md for view layer migration.

## Detection and Strategy Selection

### Step 1 — Read the resolved view strategy from migration-spec.yaml

Read `migration_strategy.view_layer` and `execution.mode` from `migration-spec.yaml`:

| Value | Meaning |
|---|---|
| `qute` | Migrate all views to Qute — skip file-count heuristic |
| `myfaces` | Maintain JSF with Quarkus MyFaces — skip file-count heuristic |
| `auto` or `null` | Fall back to file-count heuristic (Step 2) |

`view_layer` was resolved by the planning agent — user-chosen in **interactive** mode, agent-selected in
**autonomous** mode. **This module never prompts the user in either mode**: an `auto`/`null` value is
resolved by the file-count heuristic below, not by asking. In autonomous mode, record the resolved
technology + reason in the spec's `decisions:` section so the final report shows what was chosen.

### Step 2 — Count view files (only when view_layer is `auto` or `null`)

1. **Count JSP files**: `find src/main/webapp -name "*.jsp" | wc -l`
2. **Count JSF files**: `find src/main/webapp -name "*.xhtml" | wc -l`
3. **Count Thymeleaf files**: `find src/main/resources/templates -name "*.html" | wc -l`
4. **Count FreeMarker files**: `find src/main/resources/templates -name "*.ftl" | wc -l`

### Step 3 — Resolve final strategy

Apply in priority order — **user preference always wins over file count**:

**JSP:**
- `view_layer: qute` OR `auto`/`null` → **Migrate to Qute**
- Delegate to [jsp-qute.md](jsp-qute.md)

**JSF:**
- `view_layer: qute` → **Migrate to Qute** regardless of file count
  - Delegate to [jsf-qute.md](jsf-qute.md)
- `view_layer: myfaces` → **Maintain JSF with Quarkus MyFaces** regardless of file count
  - Delegate to [jsf-quarkus-myfaces.md](jsf-quarkus-myfaces.md)
- `view_layer: auto` or `null`:
  - **If view files >= 5**: Maintain JSF using Quarkus MyFaces extension
    - Delegate to [jsf-quarkus-myfaces.md](jsf-quarkus-myfaces.md)
  - **If view files < 5**: Migrate to Qute templates
    - Delegate to [jsf-qute.md](jsf-qute.md)

**Thymeleaf:**
- `view_layer: qute` OR `auto`/`null` → **Migrate to Qute** (no Quarkus Thymeleaf extension)
- Delegate to [thymeleaf-qute.md](thymeleaf-qute.md)

**FreeMarker:**
- `view_layer: qute` OR `auto`/`null` → **Migrate to Qute**
- Delegate to [freemarker-qute.md](freemarker-qute.md)

## Steps

1. **Read `migration_strategy.view_layer`** from migration-spec.yaml
2. **Count view files** (only when view_layer is `auto` or `null`)
3. **Delegate to specialized frontend agent** based on resolved strategy:
   - **JSP → Qute** (user: `qute`/`auto`/`null`): Read and execute [jsp-qute.md](jsp-qute.md)
   - **JSF → Qute** (user: `qute`, or `auto`/`null` with < 5 files): Read and execute [jsf-qute.md](jsf-qute.md)
   - **JSF → MyFaces** (user: `myfaces`, or `auto`/`null` with >= 5 files): Read and execute [jsf-quarkus-myfaces.md](jsf-quarkus-myfaces.md)
   - **Thymeleaf → Qute** (user: `qute`/`auto`/`null`): Read and execute [thymeleaf-qute.md](thymeleaf-qute.md)
   - **FreeMarker → Qute** (user: `qute`/`auto`/`null`): Read and execute [freemarker-qute.md](freemarker-qute.md)
4. **Frontend agent performs actual file transformations**:
   - Reads source view files with actual content
   - Applies syntax transformations per the frontend guide
   - Writes transformed content to target files
   - Migrates managed beans to CDI (if applicable)
   - Updates controllers to work with chosen view technology
   - Migrates static assets to `META-INF/resources/`
5. **Update application.properties** with view configuration (if needed)
6. **Run compilation**: `mvn clean package -DskipTests` to ensure compilation is successful
7. **Run UI migration validator**:
```bash
# Build validator if needed. The path is relative to where the skill is located
cd validators/java
mvn clean package -DskipTests -q

# Run validator
java -jar target/migration-validator-1.0.0.jar validate ui \
  <spring_source_dir> \
  <quarkus_target_dir> \
  <migration_type> \
  <quarkus_target_dir>/migration-spec.yaml
```
   Where `<migration_type>` is one of:
   - `jsp-qute` - JSP to Qute migration
   - `thymeleaf-qute` - Thymeleaf to Qute migration
   - `freemarker-qute` - FreeMarker to Qute migration
   - `jsf-qute` - JSF to Qute migration
   - `jsf-myfaces` - JSF maintained with Quarkus MyFaces
8. Optional: **Fix & Revalidate until pass** status (PASS/FAIL)
9. **Generate phase-08b-web-views-migration.json**

## Validation Gate

After completing all transformations, **MANDATORY VALIDATION** must be performed:

### 1. Run UI Migration Validator

Execute the UI migration validator to verify the migration:

```bash
# Build validator if needed. The path is relative to where the skill is located
cd validators/java
mvn clean package -DskipTests -q

# Run validator
java -jar target/migration-validator-1.0.0.jar validate ui \
  <spring_source_dir> \
  <quarkus_target_dir> \
  <migration_type> \
  <quarkus_target_dir>/migration-spec.yaml
```

**Migration Types:**
- `jsp-qute` - JSP to Qute migration
- `thymeleaf-qute` - Thymeleaf to Qute migration
- `freemarker-qute` - FreeMarker to Qute migration
- `jsf-qute` - JSF to Qute migration
- `jsf-myfaces` - JSF maintained with Quarkus MyFaces

### 2. Gate Decision

Check the validation report status:
- ✅ **PASS** (`status: PASS`, `errors: 0`) → Proceed to Phase 9
- ❌ **FAIL** (`status: FAIL`, `errors > 0`) → **STOP** and fix issues

The validator exits with code 0 on PASS, 1 on FAIL.

### 3. Blocking Criteria

The following issues will block progression to Phase 9 (ERROR severity):
- **Missing view files**: Source files not migrated to target
- **Missing Quarkus view dependencies**: Required extensions not added
  - JSP → Qute: `quarkus-rest-qute`
  - JSF → MyFaces: MyFaces or PrimeFaces extension
  - JSF → Qute: `quarkus-rest-qute`
  - Thymeleaf → Qute: `quarkus-rest-qute`
  - FreeMarker → Qute: `quarkus-rest-qute`
- **Managed beans not migrated**: JSF/Spring beans not converted to CDI
- **Compilation failures**: Target project does not compile
- **Missing configuration**: Required properties not set
- **Incorrect template syntax**: Qute templates with syntax errors
- **Static resources not migrated**: CSS/JS/images not moved to `META-INF/resources/`
- **Spring-specific code remaining**: CSRF tokens, Spring form tags, etc.

### 4. Non-Blocking Warnings

These can be addressed later but should be documented (WARNING severity):
- View styling differences (CSS/layout)
- JavaScript compatibility issues
- Advanced JSF components not fully supported
- Template syntax differences requiring manual review
- Performance optimization opportunities
- Code style inconsistencies

### 5. Error Resolution Process

If validation fails:
1. Review the validation report for specific errors
2. Fix errors based on category:
   - **Dependency errors**: Add missing Quarkus extensions
   - **Template errors**: Fix syntax or complete transformations
   - **Bean errors**: Complete CDI migration
   - **Configuration errors**: Add missing properties
3. Re-run compilation: `mvn clean package -DskipTests`
4. Re-run validator with same command
5. Repeat until validation passes


**⚠️ CRITICAL: Do not proceed to Phase 9 until validation gate passes!**

The validation gate ensures:
- All view files are properly migrated
- Dependencies are correctly configured
- Managed beans follow CDI patterns (if applicable)
- Templates use correct syntax
- Static resources are accessible
- The application compiles successfully

## Output

**Directory Setup:**
```bash
mkdir -p migration-reports
```

**File Location:** `migration-reports/phase-08b-web-views-migration.json`

This report should be created in the target Quarkus project at `<quarkus_target_dir>/migration-reports/phase-08b-web-views-migration.json`.

Example:

```json
{
  "view_technology": {
    "source_technology": "JSF|JSP|Thymeleaf|FreeMarker",
    "target_technology": "JSF|Qute",
    "strategy": "maintain|migrate",
    "file_count": 12,
    "files_migrated": [
      {
        "source": "src/main/webapp/orders/list.jsp",
        "target": "src/main/resources/templates/OrderResource/list.html",
        "status": "DONE"
      }
    ],
    "managed_beans_migrated": 5,
    "managed_beans": [
      {
        "source": "com.example.bean.OrderBean",
        "target": "com.example.bean.OrderBean",
        "scope_before": "@ViewScoped",
        "scope_after": "@jakarta.faces.view.ViewScoped",
        "status": "DONE"
      }
    ]
  }
}
```

## Transformation Ledger

Update migration-spec.yaml transformations.web-views-migration:

```yaml
transformations:
  web-views-migration:
    - source: src/main/webapp/orders/list.jsp
      target: src/main/resources/templates/OrderResource/list.html
      technology: "JSP → Qute"
      status: DONE
      notes: "Syntax converted, controller updated"
    - source: src/main/webapp/orders/list.xhtml
      target: src/main/resources/META-INF/resources/orders/list.xhtml
      technology: "JSF → JSF (maintained with MyFaces)"
      status: DONE
      notes: "Managed bean migrated to CDI"
```

## Error Handling

On errors:
1. Capture error details
2. Attempt automatic fix (missing imports, syntax issues)
3. If unresolved after 2 attempts, mark for manual review
4. Continue with remaining files
5. Report all issues in phase-08b-web-views-migration.json

## Notes

- All framework-specific migration instructions are in the specialized frontend agents
- This orchestrator only handles strategy selection, delegation, validation, and reporting
- Frontend agents contain detailed syntax conversion tables, code examples, and troubleshooting guides
