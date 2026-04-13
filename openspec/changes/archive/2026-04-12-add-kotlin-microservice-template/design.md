## Context

This repo is a collection of [boilr](https://github.com/Ilyes512/boilr) templates rendered with Go `text/template`. Each top-level folder is one template with a `project.json` (variable definitions) and a `template/` tree (rendered output). The existing `kotlin/` template uses Ktor for its `web` profile and is intentionally small. The new `kotlin-microservice/` template targets a different audience: teams standing up Spring Boot services that need observability, container assets, and AWS SNS/SQS messaging from day one.

Constraints inherited from the repo:
- Templates must be pure boilr — no post-generation scripts. Everything must work with `boilr template save` + `boilr template use`.
- Both file contents and file/directory names support `{{ ... }}` substitution. Path templating (e.g. `src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/Application.kt`) is the canonical way to land code under the user's package.
- Common prompt keys are `tld`, `author`, `app_name`, `version`. We should reuse them so users have one mental model across templates.
- The repo intentionally avoids `stack_profile` for templates that have a single shape (see `clojure/`). The microservice template is opinionated, so we follow the same pattern — no profile prompt in v1.

Stakeholders: the template author (this repo's maintainer) and downstream service teams who will run `boilr template use kotlin-microservice ...`.

## Goals / Non-Goals

**Goals:**
- Generate a Spring Boot 3.x Kotlin service that builds and runs (`gradle bootRun`) with zero manual edits after `boilr template use`.
- Ship Actuator-backed `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, and a Prometheus `/actuator/prometheus` endpoint wired through Micrometer.
- Wire OpenTelemetry via the Spring Boot starter so traces export to an OTLP endpoint configurable by env var, with sensible localhost defaults pointing at the bundled collector.
- Replace Logback with Log4j2 + a JSON layout so logs are immediately ingestable by an aggregator.
- Provide a working SNS publisher and SQS consumer wired against AWS SDK v2, with profiles that point at LocalStack locally and real AWS in deployed environments.
- Ship a multi-stage `Dockerfile` and a `docker-compose.yml` that brings up the service plus LocalStack (SNS/SQS) and an OTel collector, so a developer can `docker compose up` and see traces, metrics, and a working queue.
- Keep the rendered project free of `TODO: configure me` placeholders for the chosen stack — everything should be wired through `application.yml` and env vars.

**Non-Goals:**
- Multiple stack profiles (no Ktor/Spring switch, no with/without messaging variant) in v1. If demand appears, add a `stack_profile` later.
- A database layer. The microservice template is HTTP + messaging only; teams that need a DB can add Spring Data JPA / Exposed manually, or we add a `db` profile in a follow-up.
- CI configuration (GitHub Actions, etc.) — out of scope for v1; templates in this repo do not currently ship CI.
- Production-grade IAM, secret management, or Terraform. The compose file uses LocalStack with dummy credentials; deployed config is the consumer's responsibility.
- Migrating the existing `kotlin/` template. It stays as-is.

## Decisions

### D1. Spring Boot over Ktor or Http4k
- **Choice**: Spring Boot 3.x with Kotlin and Gradle Kotlin DSL.
- **Why**: The user explicitly asked for Spring Boot, and it dominates the JVM microservice ecosystem — Actuator, Micrometer, the OTel starter, and `spring-cloud-aws` all ship first-class integrations that remove a lot of plumbing. Ktor (already in `kotlin/web`) is a deliberate alternative covered by the other template.
- **Alternatives considered**: Ktor (already covered), Http4k (smaller community, less off-the-shelf observability).

### D2. Single shape, no `stack_profile`
- **Choice**: One opinionated layout. `project.json` exposes only the common keys plus a `java_version` prompt.
- **Why**: Mirrors `clojure/`. Profiles add `{{if eq stack_profile "..."}}` branches in every file and slow iteration. v1 stays simple; we can add profiles later if real demand shows up.
- **Alternatives considered**: A `messaging` on/off profile (rejected — SNS/SQS is the whole point of the template), a `db` profile (deferred to a follow-up change).

### D3. Log4j2 + JSON layout instead of default Logback
- **Choice**: Exclude `spring-boot-starter-logging`, depend on `spring-boot-starter-log4j2`, configure `log4j2.xml` with `JsonTemplateLayout` (ECS-compatible template).
- **Why**: The user wants structured JSON logs. Log4j2's `JsonTemplateLayout` is the lightest path to ECS-shaped logs without pulling Logstash encoders. Aligns with the existing `kotlin/` template which already prefers Log4j2.
- **Alternatives considered**: Logback + `logstash-logback-encoder` (works but introduces a different logging stack from the sibling template); plain Logback with a pattern layout (not structured).

### D4. OpenTelemetry via Spring Boot starter, OTLP exporter
- **Choice**: `io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter` + `opentelemetry-exporter-otlp`. Default endpoint `http://localhost:4317`, overridable via `OTEL_EXPORTER_OTLP_ENDPOINT`. Service name defaults to `{{app_name}}`.
- **Why**: The starter wires auto-instrumentation for Spring Web, the AWS SDK, and JDBC without needing the Java agent at runtime. OTLP is the vendor-neutral default; the bundled collector can fan out to Jaeger/Tempo/etc.
- **Alternatives considered**: Java agent (heavier, complicates the Dockerfile), Micrometer Tracing + Brave/Zipkin (less aligned with current OTel-first direction).

### D5. Micrometer + Prometheus registry for metrics
- **Choice**: `spring-boot-starter-actuator` + `micrometer-registry-prometheus`. Expose `health`, `info`, `prometheus` via `management.endpoints.web.exposure.include`. Probes (`liveness`, `readiness`) enabled.
- **Why**: Standard Spring Boot path; the Prometheus endpoint is what every k8s scrape config expects.
- **Alternatives considered**: OTel metrics exporter only (less universal scrape support today).

### D6. AWS SDK v2, not Spring Cloud AWS, for SNS/SQS in v1
- **Choice**: Depend directly on `software.amazon.awssdk:sns` and `:sqs`. Provide a thin `SnsPublisher` and an `SqsPoller` (a `@Scheduled` poll loop using `ReceiveMessageRequest`) wired as Spring beans. Endpoint override and credentials provider are read from `application.yml`, defaulting to LocalStack on `localhost:4566` with static dummy creds in the `local` profile.
- **Why**: AWS SDK v2 is the lowest common denominator and avoids pulling the entire Spring Cloud AWS BOM for two clients. A short hand-written poller is easy to read and to swap for `@SqsListener` later if a team adopts Spring Cloud AWS.
- **Alternatives considered**: `io.awspring.cloud:spring-cloud-aws-starter-sns` / `-sqs` (nicer DX with `@SqsListener`, but ties the template to a specific Spring Cloud AWS version cadence and adds a heavy dependency surface). We can revisit in a follow-up if teams ask for it.

### D7. LocalStack via docker-compose for local dev
- **Choice**: `docker-compose.yml` brings up: the service (built from the template's `Dockerfile`), LocalStack (with `SERVICES=sns,sqs`), and an OTel collector image with a minimal config that exports traces to stdout (so devs can verify wiring without setting up Jaeger). An init script (run via LocalStack's `/etc/localstack/init/ready.d/`) creates the sample SNS topic and SQS queue and subscribes the queue to the topic.
- **Why**: Lets a generated project run end-to-end with `docker compose up` — produce a message, see it consumed, see a trace in the collector logs.
- **Alternatives considered**: Testcontainers-only (great for tests but doesn't help local manual dev); stub clients (doesn't exercise the real SDK code paths).

### D8. Multi-stage Dockerfile with Eclipse Temurin
- **Choice**: Stage 1 uses `eclipse-temurin:{{java_version}}-jdk` to run `gradle bootJar` (with a Gradle wrapper rendered into the template). Stage 2 uses `eclipse-temurin:{{java_version}}-jre` and copies the fat jar. Non-root user, `EXPOSE 8080`, `HEALTHCHECK` hitting `/actuator/health/liveness`.
- **Why**: Standard, vendor-neutral, and small enough. Templating the Java version through `{{java_version}}` keeps it consistent with the Gradle toolchain.
- **Alternatives considered**: Distroless (smaller but harder to debug for newcomers), buildpacks via `bootBuildImage` (no Dockerfile artifact in the repo, which the user explicitly asked for).

### D9. Package layout via path templating
- **Choice**: Source files live under `template/src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/...` and tests under `template/src/test/kotlin/{{tld}}/{{author}}/{{app_name}}/...`, mirroring how `kotlin/` already does it. Package declarations in file contents use the same `{{tld}}.{{author}}.{{app_name}}` triplet.
- **Why**: Keeps prompt semantics identical across templates.

### D10. Versions pinned in `project.json`
- **Choice**: Pin Spring Boot, Kotlin, Gradle, AWS SDK v2 BOM, OpenTelemetry BOM, and Log4j2 versions as defaults in `project.json` so they're easy to bump in one place. Initial pins (subject to verification at implementation time): Spring Boot `3.3.x`, Kotlin `2.1.10` (matches `kotlin/`), Gradle `8.12.1` (matches `kotlin/`), Java `21`, AWS SDK v2 `2.28.x` BOM, OpenTelemetry `2.x` BOM.
- **Why**: One place to update when versions move; consistency with the sibling Kotlin template where it makes sense.
- **Alternatives considered**: Hard-coding versions in `build.gradle.kts` (harder to bump, inconsistent with how `kotlin/` already exposes versions through prompts).

## Risks / Trade-offs

- **[Risk] Spring Boot + OTel + AWS SDK pulls a heavy dependency tree; first build is slow.** → Document expected first-build time in the rendered README; rely on Gradle's build cache for subsequent runs. Not blocking — this is the cost of the stack the user asked for.
- **[Risk] LocalStack image is large (~1GB) and slows `docker compose up` the first time.** → Use the `localstack/localstack` slim variant pinned to a known-good tag; document the one-time pull cost in the rendered README.
- **[Risk] Hand-rolled `SqsPoller` lacks the ergonomics of `@SqsListener` (no automatic visibility extension, no batch handling).** → Keep the poller minimal and well-commented so teams can replace it with Spring Cloud AWS later. Document this trade-off in the rendered README. Acceptable for v1.
- **[Risk] Pinning Spring Boot / OTel / AWS SDK versions means the template will go stale.** → Centralize versions in `project.json` so a single PR can bump them. Add a note in `CLAUDE.md` about where versions live.
- **[Risk] The OTel Spring Boot starter is still on a 2.x line and its API surface has churned across releases.** → Pin to a specific minor version, verified to build green at implementation time. Re-verify on every bump.
- **[Risk] JSON log layout differs from the sibling `kotlin/` template, which uses a plain Log4j2 console layout.** → This is intentional (microservices want structured logs). Call it out explicitly in the README so users aren't surprised.
- **[Trade-off] No `stack_profile` in v1 means anyone who wants "Spring Boot without messaging" has to delete files post-generation.** → Acceptable; the whole point of this template is the bundled stack. Revisit if a real user asks.
- **[Trade-off] No DB layer means teams persisting state still need to wire JPA/Flyway by hand.** → Out of scope for v1 by design. A `db` profile or a sibling `kotlin-microservice-db` template is a reasonable follow-up.

## Open Questions

- Which exact Spring Boot 3.3.x and OpenTelemetry 2.x versions build cleanly together with Kotlin 2.1.10? Resolve at implementation time by running `gradle build` on the rendered output.
- Should the bundled OTel collector export to stdout only, or also forward to a Jaeger/Tempo container in compose? Stdout-only keeps the compose footprint small; revisit if developers ask for a UI.
- Do we want a `health` indicator that pings SNS/SQS (via `ListTopics` / `GetQueueUrl`)? Useful in deployed envs, noisy locally. Default: no, document how to add one.
