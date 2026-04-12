# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

A collection of [boilr](https://github.com/Ilyes512/boilr) project templates. Each top-level folder (`kotlin/`, `clojure/`) is one template. Templates are rendered using Go `text/template` syntax.

## Template structure

Every template follows this layout:

```
<template-name>/
├── project.json     ← variable definitions (stays outside output)
└── template/        ← everything inside is copied and rendered
```

`project.json` rules:
- Scalar values → prompt default
- Array values → multiple-choice prompt
- Use lowercase snake_case keys (e.g. `app_name`, `stack_profile`)

## Go template syntax used in template files and file/directory names

```
{{app_name}}                                         value substitution
{{if eq stack_profile "cli"}}...{{end}}              conditional
{{if or (eq stack_profile "web") (eq stack_profile "web-db")}}...{{end}}
{{range template_type}}- {{.}}{{end}}                loop
{{- ... -}}                                          whitespace trimming
```

Both file contents **and** directory/file names support template syntax. For example, `src/{{tld}}/{{author}}/{{app_name}}/Main.kt` is a valid path inside `template/`.

## Using templates locally

```bash
# Register templates
boilr template save ./kotlin kotlin
boilr template save ./clojure clojure

# Generate a project
boilr template use kotlin ~/Workspace/my-kotlin-app
```

## Kotlin template

**Stack profiles** (selected via `stack_profile` prompt):

| Profile | Extra dependencies |
|---------|-------------------|
| `default` | Log4j (API + Kotlin API + Core), JUnit 5, AssertK, MockK |
| `cli` | `default` + Clikt + Mordant |
| `web` | `default` + Ktor (Netty, content-negotiation, serialization) + Log4j SLF4J |
| `db` | `default` + Exposed + HikariCP + Flyway + PostgreSQL driver |
| `web-db` | `web` + `db` combined |

`Main.kt` adapts to the profile: `web`/`web-db` add `ServerModule`, `db`/`web-db` add `DatabaseModule`. Prefer `stack_profile` for stable bundles; add one-off libraries via manual Gradle edits after generation.

**Build/test commands (run from a generated project, not this repo):**

```bash
gradle build
gradle test
gradle run
```

**Versions (defined in `kotlin/project.json`):** Gradle 8.12.1, Kotlin 2.1.10, JUnit 5.10.0.

## Clojure template

Minimal template — single `default` profile, Leiningen project, Clojure only.

**Build/test commands (run from a generated project):**

```bash
lein test
lein repl
lein uberjar
```

## Adding a new template

1. Create `<name>/project.json` and `<name>/template/` with your files.
2. Use the same common keys (`tld`, `author`, `app_name`, `version`) where applicable.
3. Register locally with `boilr template save ./<name> <name>` and verify with `boilr template use <name> /tmp/test-output`.
