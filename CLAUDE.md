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

## Kotlin microservice template

Opinionated Spring Boot 3.x microservice template in Kotlin. Common bundle (every profile): Spring Boot Web + Actuator, Micrometer + Prometheus, OpenTelemetry SDK + OTLP exporter, Log4j2 with `JsonTemplateLayout` (Logback excluded), AWS SDK v2 SNS publisher and SQS poller, multi-stage `Dockerfile` at the project root (production runtime image), and a `local/docker/docker-compose.yml` running the service alongside LocalStack (SNS/SQS) and an OpenTelemetry collector for local development.

**Stack profiles** (selected via `stack_profile` prompt):

| Profile | Extra dependencies |
|---------|-------------------|
| `default` | none — HTTP + messaging only |
| `relational-db` | Spring Data JPA + Hibernate, Flyway (core + `flyway-database-postgresql`), PostgreSQL JDBC driver, Testcontainers Postgres for integration tests. Adds a `postgres:{{postgres_image_tag}}` service to the compose stack with `pg_isready` healthcheck; the `app` service waits on it. Ships a sample `@Entity`, `JpaRepository`, `V1__init.sql` migration, and a Testcontainers-backed integration test. |
| `nosql-cache` | **Raw** MongoDB Java sync driver (`mongodb-driver-sync`, **not** Spring Data MongoDB), Mongock (`mongock-springboot-v3` + `mongodb-sync-v4-driver`) for migrations, Spring Data Redis (Lettuce) for a `StringRedisTemplate` cache-aside helper, Testcontainers MongoDB + generic Redis container for integration tests. Adds `mongo:{{mongo_image_tag}}` and `redis:{{redis_image_tag}}` services to the compose stack with healthchecks; the `app` service waits on both. Ships a sample `data class`, repository wrapping a `MongoCollection`, a Mongock `ChangeUnit`, a `SampleCache`, a cache-aside `SampleDocumentService`, a hand-wired `MongoHealthIndicator`, and a Testcontainers integration test exercising the full cache-aside flow. **Deliberate asymmetry**: raw Mongo driver but Spring Data Redis — the Mongo abstractions aren't worth their cost, Redis's are. Mongo config is under `app.mongo.*` (not `spring.data.mongodb.*`) because Spring Data Mongo auto-config is intentionally absent. |

The three profiles are mutually exclusive. Future profiles extend the same `stack_profile` array rather than introducing a new prompt.

**Hexagonal layering (profile-agnostic, ArchUnit-enforced):** every generated project organizes Kotlin source into five packages under `<tld>.<author>.<app_name>`:

| Layer | Purpose | May depend on |
|---|---|---|
| `commons` | Zero-dependency utilities (extractable as a library) | *(nothing project-internal)* |
| `domain` | Entities, invariants, outbound ports. Framework-free (no Spring, JPA, Mongo, Spring Data, Mongock imports). | `commons` |
| `application` | Use case services (`@Service`) orchestrating domain ports | `domain`, `commons` |
| `infrastructure` | Adapters (`adapters/in/web`, `adapters/in/messaging`, `adapters/out/persistence`, `adapters/out/messaging`) + framework config (`config/`) | `application`, `domain`, `commons` |
| `main` | `@SpringBootApplication`, bean wiring, bootstrap. Uses `scanBasePackages = ["<tld>.<author>.<app_name>"]` so component scan still reaches the siblings. | all |

The rules are enforced at build time by `src/test/kotlin/.../architecture/ArchitectureTest.kt` using ArchUnit (`archunit-junit5`, version pinned via `archunit_version`). Violations fail `./gradlew test`. Specific rules: layered-architecture direction, no cycles, `commons` depends on nothing project-internal, `domain` rejects framework imports, `@RestController` must live in `infrastructure.adapters.in.web`, `@Entity`/`@Repository` must live in `infrastructure.adapters.out`.

**Sample use case (profile-agnostic, outbound adapter varies by profile):** every profile ships a canonical `Sample` use case exposed at `POST /samples` / `GET /samples/{id}` through `SampleController` → `SampleService` → `SampleRepository` (domain port). The outbound persistence adapter is selected by `stack_profile`:

- `default` → `InMemorySampleRepositoryAdapter` (`ConcurrentHashMap`).
- `relational-db` → `JpaSampleRepositoryAdapter` in `infrastructure/adapters/out/persistence/jpa/`, with a separate `SampleJpaEntity` (annotated) and hand-written domain mapper — the domain `Sample` stays JPA-free. Flyway migration `V1__init.sql` creates the `sample` table.
- `nosql-cache` → `MongoSampleRepositoryAdapter` in `infrastructure/adapters/out/persistence/mongo/`, which owns a `MongoCollection` and an internal `SampleCache` (Spring Data Redis `StringRedisTemplate`) and performs cache-aside **inside the adapter** — the application layer sees only the domain port. Mongock `@ChangeUnit` in the same package creates the sample-collection index.

Per-profile integration tests live under `src/test/kotlin/.../infrastructure/adapters/out/persistence/{jpa,mongo}/` and exercise the adapter through the domain port (plus an end-to-end variant that drives `SampleController` via MockMvc for the relational-db profile). The default profile's test uses the in-memory adapter and requires no Docker.

**Prompts** (`kotlin-microservice/project.json`): `tld`, `author`, `app_name`, `version`, `java_version`, `stack_profile`, plus version pins (`spring_boot_version`, `kotlin_version`, `gradle_version`, `aws_sdk_version`, `otel_version`, `log4j_version`, `micrometer_version`, `testcontainers_version`, `flyway_version`, `postgres_image_tag`, `mongo_image_tag`, `redis_image_tag`, `mongock_version`, `archunit_version`). Bump versions in one place by editing `project.json`.

**Register and use:**

```bash
boilr template save ./kotlin-microservice kotlin-microservice
boilr template use kotlin-microservice ~/Workspace/my-svc
```

**Bootstrap the wrapper after generating** (the template ships only `gradle-wrapper.properties`):

```bash
cd ~/Workspace/my-svc
gradle wrapper --gradle-version <gradle_version>
./gradlew bootRun
```

**Run the full local stack:**

```bash
docker compose -f local/docker/docker-compose.yml up --build
```

The compose stack and its supporting configs (LocalStack init, OTel collector) live under `local/docker/`. The root-level `Dockerfile` and `.dockerignore` are intentionally kept at the project root because they are production artifacts, not local-dev assets.

This brings up the app, LocalStack (with an init script that creates the `<app_name>-events` topic + queue and subscribes them), and an OTel collector that prints spans to stdout.

## Node.js/TypeScript microservice template

Opinionated NestJS 10 microservice template in TypeScript. Common bundle (every profile): NestJS HTTP + DI, `nestjs-pino` JSON logging, `@willsoto/nestjs-prometheus` `/metrics` endpoint, `@nestjs/terminus` health endpoints, OpenTelemetry Node SDK + OTLP/HTTP exporter initialized via `--require ./dist/tracing.js` (load-order-sensitive — do NOT import from inside Nest code), AWS SDK v3 SNS publisher and `sqs-consumer`-backed SQS poller, an `S3BlobStorage` sample wrapping `@aws-sdk/client-s3` with a `POST /blobs` + `GET /blobs/:key` controller, a multi-stage `Dockerfile` at the project root, and a `local/docker/docker-compose.yml` running the service alongside LocalStack (SNS/SQS/**S3**) and an OpenTelemetry collector. Vitest + Testcontainers-node for integration tests.

**Stack profiles** (selected via `stack_profile` prompt):

| Profile | Extra dependencies |
|---------|-------------------|
| `default` | none — HTTP + messaging + S3 only |
| `relational-db` | Prisma (schema + generated client + migration runner), PostgreSQL driver, `ioredis` cache-aside helper, `@testcontainers/postgresql` for integration tests. Adds `postgres:{{postgres_image_tag}}` and `redis:{{redis_image_tag}}` services to the compose stack with healthchecks; the `app` service waits on both. Ships a Prisma `SampleEntity`, an initial migration, a `PrismaService`, a repository, a cache-aside `SampleEntityService`, and a Testcontainers-backed integration test. |
| `nosql-cache` | **Raw** `mongodb` Node driver (**not** Mongoose), `migrate-mongo` for migrations, `ioredis` cache-aside helper, `@testcontainers/mongodb` + generic Redis container for integration tests. Adds `mongo:{{mongo_image_tag}}` and `redis:{{redis_image_tag}}` services with healthchecks; the `app` service waits on both. Ships a `SampleDocument` interface, a repository wrapping a `Collection<SampleDocument>`, a `migrate-mongo` migration creating the `samples` collection + index, a `SampleCache`, a cache-aside `SampleDocumentService`, a hand-wired `MongoHealthIndicator`, and a Testcontainers integration test exercising the full cache-aside flow. **Deliberate asymmetry**: raw Mongo driver but `ioredis` wrapped in a Nest `CacheModule`-style provider — the Mongo abstractions aren't worth their cost, the Redis wrapper is. Mongo config is under `app.mongo.*` (not a well-known key) because there is no Spring-Data-Mongo analogue to auto-wire. |

The three profiles are mutually exclusive. Future profiles extend the same `stack_profile` array rather than introducing a new prompt.

**Deliberate divergences from `kotlin-microservice/`:**

1. **S3 sample ships in every profile**, not profile-gated — `S3BlobStorage` + `POST /blobs` + `GET /blobs/:key` wire a LocalStack S3 bucket into the common bundle. The LocalStack init script creates the bucket alongside the topic, queue, and subscription.
2. **Redis cache is present in `relational-db` too**, not just `nosql-cache`. The `SampleCache` + `RedisHealthIndicator` files live under `src/cache/` and are gated by a single `{{if or (eq stack_profile "relational-db") (eq stack_profile "nosql-cache")}}` condition so the Redis code is written once and shared.
3. **NestJS CLI's default Jest runner is overridden to Vitest**, with a `vitest.config.ts` defining separate `unit` and `integration` projects. Generated projects diverge from NestJS docs in this one area.
4. **CommonJS output** (not ESM). NestJS + Prisma + OTel + Vitest ESM integration is still rough enough that CJS is the stable choice.

**Prompts** (`nodejs-typescript/project.json`): `tld`, `author`, `app_name`, `version`, `stack_profile`, plus version pins (`node_version`, `typescript_version`, `nestjs_version`, `aws_sdk_version`, `otel_version`, `otel_sdk_version`, `pino_version`, `nestjs_pino_version`, `prisma_version`, `mongo_driver_version`, `migrate_mongo_version`, `ioredis_version`, `testcontainers_version`, `vitest_version`, `sqs_consumer_version`, `postgres_image_tag`, `mongo_image_tag`, `redis_image_tag`, `localstack_image_tag`). Bump versions in one place by editing `project.json`.

**IMPORTANT — version bumps:** when upgrading a shared dependency (AWS SDK, OTel, Postgres/Mongo/Redis image tags, Testcontainers), update **both** `kotlin-microservice/project.json` and `nodejs-typescript/project.json` so the two templates stay in lockstep. This is a known maintenance tax — accepted in exchange for stack parity.

**Register and use:**

```bash
boilr template save ./nodejs-typescript nodejs-typescript
boilr template use nodejs-typescript ~/Workspace/my-svc
```

**Install dependencies and run after generating:**

```bash
cd ~/Workspace/my-svc
npm ci
# relational-db profile only:
npx prisma generate
npm run start:dev
```

**Run the full local stack:**

```bash
docker compose -f local/docker/docker-compose.yml up --build
```

The compose stack, its supporting configs (LocalStack init, OTel collector), and the compose file itself live under `local/docker/`. The root-level `Dockerfile` and `.dockerignore` are intentionally kept at the project root because they are production artifacts, not local-dev assets.

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
