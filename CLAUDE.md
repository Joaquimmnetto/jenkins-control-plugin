# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A JetBrains IDE plugin that integrates Jenkins CI servers into IntelliJ-based IDEs. Users can browse
jobs, trigger builds, monitor build status, and view logs without leaving the IDE.

## Build Commands

```bash
./gradlew buildPlugin          # Build the plugin ZIP (output: build/distributions/)
./gradlew test                 # Run all unit tests
./gradlew check                # Run all checks (tests + verifications)
./gradlew runIdea              # Launch IntelliJ with the plugin loaded for manual testing
./gradlew verifyPlugin         # Validate plugin structure and API compatibility
./gradlew jacocoTestReport     # Generate test coverage report

# Run a single test class
./gradlew test --tests org.codinjutsu.tools.jenkins.logic.JenkinsJsonParserTest

# Run tests matching a pattern
./gradlew test --tests "org.codinjutsu.tools.jenkins.logic.*"
```

Tests run headless (`-Djava.awt.headless=true`). The project targets Java 17 and IntelliJ Platform
2025.1 Community Edition.

## Architecture

The plugin follows a layered architecture:

```
view/       → Swing UI: tool windows, tree, dialogs, parameter renderers, actions
logic/      → Business logic: HTTP communication, JSON parsing, background tasks
model/      → Data model: Job, Build, View, JobParameter, BuildStatus
settings/   → Persistent configuration (project-level and application-level)
security/   → Authentication and HTTP client abstraction
util/       → Helpers: date, string, IO, GUI
exception/  → Custom exception hierarchy
```

### Request Flow

User action → `view/action/*` → `RequestManager` → `SecurityClient` (HTTP to Jenkins REST API)
→ `JenkinsJsonParser` → model objects → `BrowserPanel` tree refresh

### Key Classes

| Class | Role |
|---|---|
| `JenkinsToolWindowFactory` | Entry point; creates the main tool window |
| `BrowserPanel` | Main UI container; owns the job tree and refresh scheduling |
| `RequestManager` | Central coordinator for all Jenkins API calls |
| `JenkinsJsonParser` | Parses Jenkins JSON API responses into model objects |
| `SecurityClient` | HTTP client abstraction (Basic auth / CSRF token variants) |
| `JenkinsSettings` | Project-scoped persistent settings (URL, credentials) |
| `JenkinsAppSettings` | Application-scoped settings (refresh interval, build delay) |
| `RssLogic` | RSS feed polling for build notifications |

### Extension Points

- `JobParameterRenderer` — add custom build parameter UI renderers
- `ViewTestResults` — add custom test result viewers

### Parameter Renderers

`view/parameter/` contains a renderer per Jenkins parameter type (text, choice, boolean, password,
date, file, Git branch, etc.). Each implements `JobParameterRenderer` and is registered in
`plugin.xml`.

## Testing

- JUnit 5 (Jupiter) + JUnit 4 via Vintage Engine
- Mockito 5 for mocking; AssertJ for assertions; AssertJ Swing for Swing UI tests
- Test resources in `src/test/resources/` include real Jenkins JSON/XML/RSS fixtures
- `JobBuilder` is a test-data builder for `Job` objects; `MockUtil` provides mock helpers

## Key Dependencies

- `com.offbytwo.jenkins:jenkins-client` — Jenkins REST API client (wraps the raw HTTP layer)
- `com.github.cliftonlabs:json-simple` — JSON parsing
- Lombok — boilerplate reduction via annotation processing (use `@Getter`, `@Builder`, etc.)

## Plugin Metadata

- Group: `org.codinjutsu`, Plugin ID: `Jenkins Control Plugin`
- Version format: `MAJOR.MINOR.PATCH[-qualifier]` (e.g. `0.13.22-eap1`)
- `plugin.xml` is the authoritative manifest: registers tool windows, actions, settings
  configurables, and extension points
- Compatibility range is set in `gradle.properties` via `pluginSinceBuild` / `pluginUntilBuild`
