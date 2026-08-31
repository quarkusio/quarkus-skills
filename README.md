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

### migrate-spring-to-quarkus

Migrate Spring Boot applications to Quarkus using a modular, gate-driven approach. Supports both Spring compatibility extensions and full Quarkus migration paths. Use this skill when you want to:

- Migrate a Spring Boot application to Quarkus
- Convert Spring annotations (DI, REST, Data, Security) to Quarkus equivalents
- Migrate Spring build files, configuration, frontend (Thymeleaf/JSP), and tests

**Triggers:** "spring to quarkus", "quarkus migration", "replace spring", "migrate pom.xml"

### quarkus-ddd

Scaffolds DDD tactical patterns using Hexagonal Architecture in Quarkus. Use when you want to:
* Generate aggregates, value objects, commands, and domain events
* Create REST endpoints, repositories, and infrastructure adapters
* Structure a full bounded context with Ports and Adapters

**Usage:** Ask Claude Code: "Create an orders bounded context with an Order aggregate"

## Learn More

- [npx skills documentation](https://github.com/vercel-labs/skills)
- [Claude Code skills documentation](https://code.claude.com/docs/en/plugin-marketplaces)
