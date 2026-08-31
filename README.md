# Quarkus Skills

Agent skills for developing and maintaining Quarkus applications

**NOTE**: This is a work in progress. Use at your own risk.

This repository contains a collection of skills for developing and maintaining Quarkus applications.

## Installation

### Using npx skills add

Install all skills from this repository:

```bash
npx skills add quarkusio/skills
```

List available skills:

```bash
npx skills add quarkusio/skills --list
```

Install specific skills only:

```bash
npx skills add quarkusio/skills --skill quarkus-update
```

Install for specific agents:

```bash
npx skills add quarkusio/skills -a claude-code
```

### Using Claude Code Plugin System

Within Claude Code, you can also install via the plugin marketplace:

```
/plugin marketplace add quarkusio/skills
/plugin install quarkus-appdev@quarkus-skills
```

## Available Skills

### quarkus-update

Check if a Quarkus project's build files are up-to-date by comparing against reference generated projects. Use this skill when you want to:

- Check if your Quarkus project is up-to-date
- Compare your build configuration against the latest Quarkus version
- Upgrade your Quarkus version with guidance on what changes to make

**Triggers:** "check project", "update quarkus", "is my project up to date", "compare build", "quarkus upgrade"

### spring2quarkus

Migrate Spring Boot applications to Quarkus using a layered approach combined with deterministic validator scripts. The skill requires the original codebase and the migrated one be in separate directories in order to make use of the validators. Use this skill when you want to:

- Migrate a Spring Boot application to Quarkus
- Convert Spring annotations (DI, REST, Data, Security) to Quarkus equivalents
- Migrate Spring build files, configuration, frontend (Thymeleaf/JSP), and tests

**Triggers:** "spring2quarkus", "spring to quarkus", "quarkus migration"

## Learn More

- [npx skills documentation](https://github.com/vercel-labs/skills)
- [Claude Code skills documentation](https://code.claude.com/docs/en/plugin-marketplaces)
