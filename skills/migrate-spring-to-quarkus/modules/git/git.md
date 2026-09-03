# Module: Git / Branch Management

**Optional module.** Set up an isolated migration branch before making changes, and commit + open a draft PR after the migration is verified. Requires user confirmation at every step.

## Prerequisites

Verify the target project is a git repository:

```bash
git -C <project-path> rev-parse --is-inside-work-tree
```

If this fails, **skip this module entirely** — inform the user that git management is not available because the project is not a git repository.

## What to do

### Pre-migration (before executing modules)

- [ ] Verify the project is a git repository
- [ ] Ensure agent session files are excluded from version control
- [ ] Determine the next run number from existing branches
- [ ] Propose branch name to the user and wait for confirmation
- [ ] Create the migration branch

### Post-migration (after verification)

- [ ] Write `migration-report.md` at the repo root
- [ ] Show the user a summary of changes and ask for confirmation before committing
- [ ] Ask the user for confirmation before pushing and creating the draft PR

## Exclude agent session files

Before any commit, ensure that agent session directories are listed in the project's `.gitignore`. These directories may contain sensitive data (tokens, credentials) logged during tool execution.

Append the following entries to `.gitignore` if they are not already present:

```
# AI agent session/local files — may contain tokens and secrets
.claude/
.cursor/
.codex/
.opencode/
.copilot/
.cline/
.continue/
.windsurf/
.junie/
.pi/
.roo/
.augment/
.aider*
CLAUDE.local.md
```

This prevents session logs from being committed and pushed to the remote repository.

## Create the migration branch

Determine the next run number from existing branches:

```bash
git branch -a --list '*migration/run-*' | sort -t- -k3 -n | tail -1
```

Propose the branch name to the user:

> I'll create branch `migration/run-XX` from `main`. OK, or do you prefer a different name?

Wait for the user to confirm or provide a custom name. Then create the branch:

```bash
git checkout main
git checkout -b <confirmed-branch-name>
```

Where `XX` is the next sequential number (zero-padded to two digits). If no prior branches exist, start with `migration/run-01`.

## Commit

### Pre-commit: check for secrets

Before staging files, scan the working directory for accidentally exposed secrets. Search for patterns like:

- Hardcoded tokens or API keys (e.g., `ghp_`, `ghs_`, `sk-`, `Bearer`, `AKIA`)
- Password or credential values in plain text
- Private keys (`-----BEGIN.*PRIVATE KEY-----`)
- `.env` files or agent session logs that slipped past `.gitignore`

```bash
grep -rn --include='*.java' --include='*.properties' --include='*.yml' --include='*.md' --include='*.json' \
  -E '(ghp_|ghs_|sk-|AKIA|Bearer [A-Za-z0-9]|password\s*=\s*[^\$]|BEGIN.*PRIVATE KEY)' .
```

If any matches are found, flag them to the user before proceeding. Do **not** commit files containing secrets.

### Stage and commit

After migration and verification are complete, show the user a summary of staged changes and ask for confirmation:

> Migration complete. Ready to commit all changes (including `migration-report.md`) with message:
>
> ```
> Migrate Spring Boot to Quarkus
>
> Migrated by Claude using skill spring-to-quarkus
> ```
>
> Proceed?

Only commit after the user confirms.

## Push and create draft PR

Ask the user for confirmation before pushing:

> Ready to push `<branch-name>` to `origin` and create a draft PR. Proceed?

```bash
git push origin <branch-name>
gh pr create --draft \
  --title "<branch-name>: Spring Boot → Quarkus migration" \
  --body "$(cat migration-report.md)"
```

The draft PR is a permanent record — never merge it. `main` always keeps the original Spring Boot code. Use labels to categorize runs (e.g., `strategy:native`, `strategy:spring-compat`).