# Module: Check JDK Version

Verify that the installed JDK meets the version requirement using the user's prompt VERSION before proceeding with the migration.

## Preconditions

This phase has no preconditions — it must **always** run as the very first step.

## Instructions

- **DO NOT** skip this phase.
- Capture the VERSION parameter from the prompt.
- [ ] Check the JDK version.
- [ ] If the version is **>= VERSION**, mark this phase as passed and proceed to the next phase
- [ ] If the version is **< VERSION** or `java` is not found — this is a genuine toolchain blocker that
      halts the run in **both** modes (the migration cannot proceed without a suitable JDK):
    - **interactive** — **Warn the user**: "JDK VERSION or later is required for this migration. Currently installed: <detected version or 'none'>. Please install JDK VERSION and ensure it is on your PATH before retrying." Then **stop the migration** — do not proceed to any subsequent phase.
    - **autonomous** — do not prompt. Emit a structured blocking failure the orchestrator records under `unresolved_issues:` (severity ERROR, phase `jdk-check`, message as above), then **stop the migration**. This is the one autonomous case that halts rather than documenting-and-continuing, because no later phase can run without a JDK.