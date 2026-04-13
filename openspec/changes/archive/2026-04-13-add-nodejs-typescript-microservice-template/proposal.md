## Why

The repository currently ships opinionated microservice templates only for the JVM (`kotlin-microservice/`). Teams working in Node.js/TypeScript have to re-derive the same conventions (observability, AWS messaging, local compose stack, profile-gated persistence) by hand for every new service. Shipping a parallel Node.js/TypeScript template — with the same profile shape and the same local-dev experience — removes that duplication and keeps both stacks in lockstep as conventions evolve.

## What Changes

- Add a new top-level `nodejs-typescript/` boilr template that mirrors `kotlin-microservice/` one-for-one: same prompts, same three mutually-exclusive `stack_profile` values (`default`, `relational-db`, `nosql-cache`), same generated repo layout (`local/docker/`, project-root `Dockerfile`, sample domain per profile, integration tests).
- Pick a modern, opinionated Node.js stack that translates each JVM framework to an idiomatic Node equivalent:
  - **HTTP + DI + modules**: NestJS 10 (closest structural analogue to Spring Boot — decorators, modules, lifecycle, built-in testing).
  - **Logging**: Pino with JSON output (replaces Log4j2 `JsonTemplateLayout`).
  - **Metrics**: `@willsoto/nestjs-prometheus` + `prom-client` (replaces Micrometer + Prometheus).
  - **Tracing**: `@opentelemetry/sdk-node` + OTLP exporter (replaces OTel Java SDK); same OTel collector in compose.
  - **Health**: `@nestjs/terminus` (replaces Spring Actuator health indicators).
  - **AWS**: AWS SDK v3 modular clients — `@aws-sdk/client-sns`, `@aws-sdk/client-sqs`, `@aws-sdk/client-s3` (replaces AWS SDK v2 for Java).
  - **SQS poller**: `sqs-consumer` wired as a Nest lifecycle component (replaces the hand-rolled Kotlin poller).
  - **Testing**: Vitest + Supertest + `testcontainers` (node) for integration tests (replaces JUnit 5 + Testcontainers Java).
  - **Build/runtime**: Node.js LTS, TypeScript 5.x strict, npm as package manager, `tsup` or `tsc` for build, multi-stage Dockerfile producing a slim runtime image.
- **Common bundle — every profile ships**:
  - HTTP (NestJS) + health + metrics + OTel + Pino JSON logging.
  - SNS publisher + SQS poller against LocalStack.
  - **S3 sample** (new vs. Kotlin template): an `S3BlobStorage` service + sample endpoint exercising `PutObject`/`GetObject` against a LocalStack `s3` bucket, wired into every profile.
  - Multi-stage `Dockerfile` at the project root, `local/docker/docker-compose.yml` running app + LocalStack (SNS/SQS/**S3**) + OTel collector, with a LocalStack init script that creates the topic, queue, subscription, and S3 bucket.
- **Profile: `default`** — HTTP + messaging + S3 only (no database, no cache).
- **Profile: `relational-db`** — adds Postgres and **Redis cache** (new vs. Kotlin template, which only gates Redis under `nosql-cache`):
  - **ORM/migrations**: Prisma (schema-first, first-class TypeScript types, built-in migration runner) — translates JPA + Flyway.
  - **Redis**: `ioredis` wrapped in a Nest `CacheModule`-style helper for cache-aside; same shape as the Mongo profile so the Redis code is identical across profiles.
  - **Sample**: a `SampleEntity` model, Prisma migration, repository, cache-aside `SampleEntityService`, Testcontainers-Postgres + Testcontainers-Redis integration test.
  - **Compose**: adds `postgres:{{postgres_image_tag}}` and `redis:{{redis_image_tag}}` services with healthchecks; `app` waits on both.
- **Profile: `nosql-cache`** — adds MongoDB and Redis cache:
  - **Mongo driver**: raw `mongodb` Node driver (mirrors the Kotlin template's deliberate choice to avoid an ODM).
  - **Migrations**: `migrate-mongo` (replaces Mongock).
  - **Redis**: same `ioredis` cache-aside helper as `relational-db`.
  - **Sample**: a `SampleDocument` type, repository wrapping a `Collection<SampleDocument>`, migration, `SampleCache`, cache-aside `SampleDocumentService`, hand-wired Mongo health indicator, Testcontainers-Mongo + Testcontainers-Redis integration test exercising the full flow.
  - **Mongo config** lives under `app.mongo.*` (not auto-config), preserving the Kotlin template's asymmetry rationale.
- `nodejs-typescript/project.json` mirrors `kotlin-microservice/project.json` prompts: `tld`, `author`, `app_name`, `version`, `stack_profile`, plus version pins (`node_version`, `typescript_version`, `nestjs_version`, `aws_sdk_version`, `otel_version`, `pino_version`, `prisma_version`, `mongo_driver_version`, `migrate_mongo_version`, `ioredis_version`, `testcontainers_version`, `postgres_image_tag`, `mongo_image_tag`, `redis_image_tag`, `localstack_image_tag`).
- Update `README.md` and `CLAUDE.md` with a new "Node.js/TypeScript microservice template" section describing profiles, prompts, register/use commands, and local-stack bring-up.
- **Non-goals**: porting the `kotlin/` or `clojure/` templates; introducing GraphQL, gRPC, Kafka, or any persistence/cache option beyond Postgres/Mongo/Redis; changing the existing `kotlin-microservice/` template (kept intact and in lockstep).

## Capabilities

### New Capabilities

- `nodejs-typescript-template`: Defines the boilr template layout, prompts, profile matrix, and generated-project contract for the new Node.js/TypeScript microservice template — i.e. what a user gets when they run `boilr template use nodejs-typescript`. Covers the common bundle (HTTP, observability, AWS SNS/SQS/S3, Dockerfile, compose stack), the three profiles and their persistence/cache wiring, the sample domain shipped per profile, and the local-dev experience (compose up, integration tests via Testcontainers).

### Modified Capabilities

<!-- None. The existing kotlin-microservice template is not changing; this change only adds a parallel template. -->

## Impact

- **New files**: entire `nodejs-typescript/` directory tree — `project.json` plus a `template/` subtree containing `package.json`, `tsconfig.json`, NestJS source under `src/` (profile-gated via Go-template `{{if eq stack_profile ...}}` blocks), `prisma/` (relational-db only), migrations folder (nosql-cache only), `Dockerfile`, `.dockerignore`, `local/docker/docker-compose.yml`, LocalStack init script, OTel collector config, and integration tests.
- **Modified files**: `README.md` and `CLAUDE.md` get a new section for the Node.js template; no other existing files change.
- **Dependencies introduced** (inside generated projects only, not this repo): NestJS 10, TypeScript 5, Pino, `@opentelemetry/*`, `@aws-sdk/client-{sns,sqs,s3}`, `sqs-consumer`, Prisma (relational-db), `mongodb` + `migrate-mongo` (nosql-cache), `ioredis` (both non-default profiles), Vitest, `testcontainers`.
- **Local-dev surface**: a second compose stack pattern to maintain in parallel with the Kotlin one; LocalStack init scripts now also need to create an S3 bucket (new artifact type vs. the Kotlin template).
- **Documentation**: both top-level docs and the `CLAUDE.md` guidance block grow; future version bumps must be made in two `project.json` files instead of one.
- **CI / local tooling**: no changes to this repo's CI. Generated projects will expect Node.js LTS + npm + Docker on the developer's machine (same Docker requirement as the Kotlin template).
