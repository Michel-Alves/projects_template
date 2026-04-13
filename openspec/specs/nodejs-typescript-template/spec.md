
### Requirement: Top-level template directory and boilr registration

The repository SHALL contain a top-level `nodejs-typescript/` directory that is a valid boilr template, consisting of a `project.json` at its root and a `template/` subdirectory whose contents are rendered into the generated project.

#### Scenario: Template can be registered with boilr

- **WHEN** a user runs `boilr template save ./nodejs-typescript nodejs-typescript` from the repository root
- **THEN** boilr SHALL accept the template without errors
- **AND** `boilr template use nodejs-typescript <output-dir>` SHALL render a complete project into `<output-dir>`

#### Scenario: project.json is not copied into output

- **WHEN** a user renders the template into a new directory
- **THEN** the output directory SHALL NOT contain `project.json`
- **AND** the output directory SHALL contain every file from `nodejs-typescript/template/` with Go-template expressions resolved

### Requirement: Prompts and version pins mirror the Kotlin microservice template

`nodejs-typescript/project.json` SHALL define prompts using lowercase snake_case keys that include, at minimum: `tld`, `author`, `app_name`, `version`, `stack_profile`, `node_version`, `typescript_version`, `nestjs_version`, `aws_sdk_version`, `otel_version`, `pino_version`, `prisma_version`, `mongo_driver_version`, `migrate_mongo_version`, `ioredis_version`, `testcontainers_version`, `postgres_image_tag`, `mongo_image_tag`, `redis_image_tag`, and `localstack_image_tag`.

The `stack_profile` prompt SHALL be defined as a JSON array containing exactly `default`, `relational-db`, and `nosql-cache` so that boilr renders it as a multiple-choice prompt.

All other prompts SHALL be scalar string defaults.

#### Scenario: stack_profile is a multiple-choice prompt with exactly three options

- **WHEN** `project.json` is parsed
- **THEN** `stack_profile` SHALL be an array with the three values `default`, `relational-db`, `nosql-cache` in that order
- **AND** no fourth option SHALL be present

#### Scenario: Version pins are centralized

- **WHEN** a generated `package.json`, `Dockerfile`, or `docker-compose.yml` references a version
- **THEN** that version SHALL be written as a `{{...}}` template expression bound to a key in `project.json`
- **AND** no pinned version SHALL appear as a hard-coded literal outside `project.json`

### Requirement: Common bundle — shipped by every profile

Every rendered project, regardless of the selected `stack_profile`, SHALL ship:

1. A NestJS application bootstrap in `src/main.ts` that initializes OpenTelemetry before Nest, creates a Nest app, and listens on an HTTP port configured via environment variable.
2. Pino-based JSON logging via `nestjs-pino` replacing Nest's default logger.
3. A Prometheus `/metrics` endpoint served via `@willsoto/nestjs-prometheus` with default process and Node.js collectors enabled.
4. Health endpoints `/health`, `/health/liveness`, and `/health/readiness` served via `@nestjs/terminus`.
5. OpenTelemetry initialization in a standalone `src/tracing.ts` file that is loaded via Node's `--require` (or `-r`) flag, using `@opentelemetry/sdk-node` with the OTLP/HTTP exporter and `@opentelemetry/auto-instrumentations-node`.
6. An `SnsPublisher` provider wrapping `@aws-sdk/client-sns`.
7. An `SqsPoller` provider that uses `bbc/sqs-consumer` and is started in `onModuleInit` and stopped in `onApplicationShutdown`.
8. An `S3BlobStorage` provider wrapping `@aws-sdk/client-s3`, plus a sample HTTP controller exposing `POST /blobs` (PutObject) and `GET /blobs/:key` (GetObject).
9. A multi-stage `Dockerfile` at the project root that produces a production runtime image.
10. A `local/docker/docker-compose.yml` that runs the app alongside LocalStack and an OpenTelemetry collector, plus a LocalStack init script creating the SNS topic, SQS queue, SNS→SQS subscription, and an S3 bucket.
11. A `README.md` in the generated project documenting local-stack bring-up and the npm scripts.

#### Scenario: S3 sample is present in every profile

- **WHEN** a project is rendered with `stack_profile` set to any of `default`, `relational-db`, or `nosql-cache`
- **THEN** the rendered `src/` tree SHALL contain the `S3BlobStorage` provider and the blob sample controller
- **AND** the LocalStack init script SHALL create the S3 bucket

#### Scenario: OpenTelemetry loads before Nest

- **WHEN** the generated project runs `npm run start:dev` or `node dist/main.js`
- **THEN** `tracing.ts` (or its compiled `tracing.js`) SHALL be loaded via `--require` before `main.ts`
- **AND** npm scripts and the Dockerfile SHALL wire this via `-r` or `NODE_OPTIONS=--require ...` rather than a runtime `import`

#### Scenario: Health endpoints reflect profile dependencies

- **WHEN** a request is made to `/health/readiness` on a rendered project
- **THEN** the response SHALL include a check for every external dependency present in the selected profile (Postgres for `relational-db`, MongoDB for `nosql-cache`, Redis for both non-default profiles) in addition to the common checks
- **AND** a liveness probe on `/health/liveness` SHALL NOT depend on any external dependency

### Requirement: Three mutually-exclusive stack profiles

The template SHALL support exactly three values for `stack_profile`: `default`, `relational-db`, and `nosql-cache`. Files that are profile-specific SHALL be gated by Go-template `{{if eq stack_profile ...}}` or `{{if or (eq stack_profile ...) (eq stack_profile ...)}}` blocks, applied in both file contents and path names as needed.

#### Scenario: default profile contains no database or cache code

- **WHEN** a project is rendered with `stack_profile=default`
- **THEN** the output SHALL NOT contain a `prisma/` directory
- **AND** the output SHALL NOT contain any import of `mongodb`, `migrate-mongo`, `ioredis`, or `@prisma/client`
- **AND** the compose file SHALL contain only `app`, `localstack`, and the OTel collector services

#### Scenario: relational-db profile ships Postgres + Redis

- **WHEN** a project is rendered with `stack_profile=relational-db`
- **THEN** the output SHALL contain a `prisma/` directory with a `schema.prisma` and at least one migration
- **AND** `package.json` dependencies SHALL include `@prisma/client`, `prisma`, and `ioredis`
- **AND** the compose file SHALL add `postgres:{{postgres_image_tag}}` and `redis:{{redis_image_tag}}` services with healthchecks
- **AND** the `app` service SHALL declare `depends_on` conditions waiting for both to become healthy
- **AND** the output SHALL NOT import `mongodb` or `migrate-mongo`

#### Scenario: nosql-cache profile ships MongoDB + Redis via the raw driver

- **WHEN** a project is rendered with `stack_profile=nosql-cache`
- **THEN** `package.json` dependencies SHALL include `mongodb`, `migrate-mongo`, and `ioredis`
- **AND** `package.json` dependencies SHALL NOT include `mongoose` or `@nestjs/mongoose`
- **AND** the output SHALL contain at least one `migrate-mongo` migration file
- **AND** the compose file SHALL add `mongo:{{mongo_image_tag}}` and `redis:{{redis_image_tag}}` services with healthchecks
- **AND** the `app` service SHALL declare `depends_on` conditions waiting for both to become healthy
- **AND** the output SHALL NOT contain `prisma/` or import `@prisma/client`

#### Scenario: Mongo config is under app.mongo.*

- **WHEN** a project is rendered with `stack_profile=nosql-cache`
- **THEN** the NestJS `ConfigModule` schema SHALL expose Mongo settings under the `app.mongo.*` namespace
- **AND** no Spring-Data-Mongo-equivalent auto-configuration SHALL be used

### Requirement: Shared Redis cache code for non-default profiles

A `SampleCache` provider backed by `ioredis` and a Redis health indicator SHALL be shared between the `relational-db` and `nosql-cache` profiles, meaning the same source files are gated by a single `{{if or (eq stack_profile "relational-db") (eq stack_profile "nosql-cache")}}` condition rather than being duplicated.

#### Scenario: Redis cache files render for both non-default profiles

- **WHEN** a project is rendered with `stack_profile=relational-db`
- **THEN** the output SHALL contain the `SampleCache` and Redis health indicator files
- **WHEN** a project is rendered with `stack_profile=nosql-cache`
- **THEN** the output SHALL contain the same `SampleCache` and Redis health indicator files (same content, profile-independent)
- **WHEN** a project is rendered with `stack_profile=default`
- **THEN** neither file SHALL be present

### Requirement: Per-profile sample domain with cache-aside and integration test

Each non-default profile SHALL ship a runnable sample domain consisting of a model, a repository, a cache-aside service, an HTTP controller, and a Testcontainers-based integration test that exercises the full cache-aside path end-to-end.

#### Scenario: relational-db sample exercises Postgres and Redis

- **WHEN** the integration test for the `relational-db` sample runs
- **THEN** it SHALL spin up Postgres and Redis via `testcontainers`
- **AND** it SHALL run Prisma migrations against the Postgres container
- **AND** it SHALL make two successive calls to the sample GET endpoint and assert the second one is served from Redis (cache hit) without hitting Postgres a second time

#### Scenario: nosql-cache sample exercises MongoDB and Redis

- **WHEN** the integration test for the `nosql-cache` sample runs
- **THEN** it SHALL spin up MongoDB and Redis via `testcontainers`
- **AND** it SHALL run `migrate-mongo` migrations against the Mongo container
- **AND** it SHALL make two successive calls to the sample GET endpoint and assert the second one is served from Redis without issuing a second Mongo query

### Requirement: Local-dev compose stack

`template/local/docker/docker-compose.yml` SHALL bring up the generated app, LocalStack (configured with `SERVICES=sns,sqs,s3`), an OpenTelemetry collector that prints traces to stdout, and profile-specific datastores, on a single `docker compose up --build` invocation.

Supporting assets (LocalStack init script, OTel collector config) SHALL live under `local/docker/`. The production `Dockerfile` and `.dockerignore` SHALL live at the generated project root, not under `local/docker/`.

#### Scenario: LocalStack init script is profile-independent

- **WHEN** the LocalStack container starts
- **THEN** the init script SHALL create the SNS topic `{{app_name}}-events`, the SQS queue `{{app_name}}-events-queue`, an SNS→SQS subscription, and an S3 bucket named after `{{app_name}}`
- **AND** these resources SHALL be created regardless of `stack_profile`

#### Scenario: Production Dockerfile stays at the project root

- **WHEN** a project is rendered
- **THEN** `Dockerfile` and `.dockerignore` SHALL be at the output root
- **AND** `local/docker/` SHALL contain compose and supporting dev-only configs only

### Requirement: Documentation updates

The root `README.md` and `CLAUDE.md` SHALL gain a "Kotlin microservice template" -parallel section describing the Node.js/TypeScript template: profiles, prompts, register/use commands, local-stack bring-up, and the deliberate divergences from the Kotlin template (S3 sample in every profile; Redis in both non-default profiles; raw Mongo driver; Mongo config under `app.mongo.*`).

#### Scenario: README has a Node.js/TypeScript section

- **WHEN** a reader opens `README.md`
- **THEN** they SHALL find a section describing the Node.js/TypeScript template alongside the existing Kotlin microservice section
- **AND** that section SHALL list the three profiles and their extra dependencies in a table matching the shape of the Kotlin section

#### Scenario: CLAUDE.md documents the divergences

- **WHEN** a reader opens `CLAUDE.md`
- **THEN** they SHALL find a section describing the Node.js/TypeScript template that explicitly calls out the deliberate divergences from the Kotlin template (S3 in common bundle, Redis in relational-db, raw Mongo driver, Mongo config under `app.mongo.*`)
