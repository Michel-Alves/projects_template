## 1. Scaffold template directory and project.json

- [x] 1.1 Create `nodejs-typescript/` at the repo root with an empty `template/` subdirectory
- [x] 1.2 Write `nodejs-typescript/project.json` with prompts: `tld`, `author`, `app_name`, `version`, and `stack_profile` as an array `["default", "relational-db", "nosql-cache"]`
- [x] 1.3 Add version-pin prompts to `project.json`: `node_version`, `typescript_version`, `nestjs_version`, `aws_sdk_version`, `otel_version`, `pino_version`, `prisma_version`, `mongo_driver_version`, `migrate_mongo_version`, `ioredis_version`, `testcontainers_version`, `postgres_image_tag`, `mongo_image_tag`, `redis_image_tag`, `localstack_image_tag`
- [x] 1.4 Verify boilr can register the empty-but-valid template via `boilr template save ./nodejs-typescript nodejs-typescript`

## 2. Common bundle — project skeleton

- [x] 2.1 Add `template/package.json` templated with `{{app_name}}`, `{{version}}`, `{{node_version}}` in `engines.node`, and npm scripts: `build`, `start`, `start:dev`, `test`, `test:e2e`, `lint`
- [x] 2.2 Wire npm `start` and `start:dev` scripts with `-r ./dist/tracing.js` / `-r ts-node/register -r tsconfig-paths/register -r ./src/tracing.ts` respectively
- [x] 2.3 Add `template/tsconfig.json` with strict mode, CommonJS output, decorator metadata enabled, `experimentalDecorators` + `emitDecoratorMetadata`
- [x] 2.4 Add `template/nest-cli.json`
- [x] 2.5 Add `template/.gitignore`, `template/.dockerignore`, `template/.editorconfig`
- [x] 2.6 Add `template/vitest.config.ts` with separate unit + integration projects, and override the default Jest config

## 3. Common bundle — observability and HTTP

- [x] 3.1 Add `template/src/tracing.ts` initializing `@opentelemetry/sdk-node`, OTLP/HTTP exporter, and `auto-instrumentations-node`, reading the endpoint from `OTEL_EXPORTER_OTLP_ENDPOINT`
- [x] 3.2 Add `template/src/main.ts` that creates the Nest app, uses `nestjs-pino` as the logger, enables graceful shutdown hooks, and listens on `PORT` (default 8080)
- [x] 3.3 Add `template/src/app.module.ts` composing `ConfigModule`, `LoggerModule` (pino), `PrometheusModule`, `TerminusModule`, and the feature modules
- [x] 3.4 Add `template/src/config/configuration.ts` with a typed schema (including `app.mongo.*` under `{{if eq stack_profile "nosql-cache"}}`)
- [x] 3.5 Add `template/src/health/health.controller.ts` with `/health`, `/health/liveness`, `/health/readiness` using Terminus, with profile-gated indicators
- [x] 3.6 Add `template/src/metrics/` wiring `@willsoto/nestjs-prometheus` with default collectors

## 4. Common bundle — AWS integrations

- [x] 4.1 Add `template/src/messaging/sns-publisher.service.ts` wrapping `@aws-sdk/client-sns` with a typed `publish` method
- [x] 4.2 Add `template/src/messaging/sqs-poller.service.ts` using `sqs-consumer`, started in `onModuleInit` and stopped in `onApplicationShutdown`
- [x] 4.3 Add `template/src/messaging/messaging.module.ts` exporting both providers
- [x] 4.4 Add `template/src/blobs/s3-blob-storage.service.ts` wrapping `@aws-sdk/client-s3` with `put`/`get` methods
- [x] 4.5 Add `template/src/blobs/blobs.controller.ts` exposing `POST /blobs` and `GET /blobs/:key`
- [x] 4.6 Add `template/src/blobs/blobs.module.ts`
- [x] 4.7 Configure the AWS SDK clients to honor `AWS_ENDPOINT_URL` so LocalStack is transparent in local dev

## 5. Common bundle — Docker and local stack

- [x] 5.1 Add `template/Dockerfile` with multi-stage build (`node:{{node_version}}` builder → `node:{{node_version}}-slim` runtime), non-root user, `NODE_OPTIONS=--require ./dist/tracing.js`, default command `node dist/main.js`
- [x] 5.2 Add `template/.dockerignore` excluding `node_modules`, `dist`, `.git`, `local/`
- [x] 5.3 Add `template/local/docker/docker-compose.yml` with services: `app` (build context = project root), `localstack` (`SERVICES=sns,sqs,s3`), `otel-collector`
- [x] 5.4 Add `template/local/docker/localstack/init-aws.sh` creating the SNS topic `{{app_name}}-events`, SQS queue `{{app_name}}-events-queue`, subscription, and S3 bucket `{{app_name}}-blobs`
- [x] 5.5 Add `template/local/docker/otel-collector-config.yaml` with OTLP receiver + logging exporter
- [x] 5.6 Add profile-specific `depends_on` conditions to the `app` service via `{{if ... }}` blocks

## 6. Shared Redis cache (relational-db + nosql-cache)

- [x] 6.1 Add `template/src/cache/sample-cache.service.ts` gated by `{{if or (eq stack_profile "relational-db") (eq stack_profile "nosql-cache")}}`, wrapping `ioredis` with `get`/`set`/`del`
- [x] 6.2 Add `template/src/cache/redis-health.indicator.ts` under the same gate, pinging Redis via `PING`
- [x] 6.3 Add `template/src/cache/cache.module.ts` under the same gate, exporting the providers and registering the health indicator
- [x] 6.4 Wire the cache module into `app.module.ts` under the same conditional

## 7. Profile: `relational-db`

- [x] 7.1 Under `{{if eq stack_profile "relational-db"}}` gate: add `template/prisma/schema.prisma` with a `SampleEntity` model
- [x] 7.2 Add an initial Prisma migration under `template/prisma/migrations/`
- [x] 7.3 Add `template/src/sample/sample-entity.repository.ts` using `PrismaClient`
- [x] 7.4 Add `template/src/sample/sample-entity.service.ts` implementing cache-aside against `SampleCache`
- [x] 7.5 Add `template/src/sample/sample-entity.controller.ts` exposing `POST /samples` and `GET /samples/:id`
- [x] 7.6 Add `template/src/sample/sample.module.ts`
- [x] 7.7 Add Postgres + Redis `depends_on` conditions to the `app` service in compose
- [x] 7.8 Add `postgres:{{postgres_image_tag}}` and `redis:{{redis_image_tag}}` services with healthchecks to compose
- [x] 7.9 Add `template/src/health/postgres.indicator.ts` and wire into `/health/readiness`
- [x] 7.10 Add `template/test/sample-entity.integration.test.ts` using `testcontainers` for Postgres + Redis, running Prisma migrations, and asserting cache-aside behavior

## 8. Profile: `nosql-cache`

- [x] 8.1 Under `{{if eq stack_profile "nosql-cache"}}` gate: add `template/src/mongo/mongo.module.ts` providing a `MongoClient` singleton and a `Db` provider using `app.mongo.*` config
- [x] 8.2 Add `template/src/sample/sample-document.repository.ts` wrapping a `Collection<SampleDocument>` using the raw `mongodb` driver
- [x] 8.3 Add `template/src/sample/sample-document.service.ts` implementing cache-aside against `SampleCache`
- [x] 8.4 Add `template/src/sample/sample-document.controller.ts` with `POST /samples` and `GET /samples/:id`
- [x] 8.5 Add `template/src/sample/sample.module.ts`
- [x] 8.6 Add `template/migrations/` with a `migrate-mongo` config and one initial migration creating the `samples` collection and an index
- [x] 8.7 Add `template/src/mongo/migrate-runner.service.ts` invoking `migrate-mongo` programmatically in `onModuleInit`
- [x] 8.8 Add `template/src/health/mongo.indicator.ts` pinging `admin.command({ ping: 1 })` and wire into `/health/readiness`
- [x] 8.9 Add `mongo:{{mongo_image_tag}}` and `redis:{{redis_image_tag}}` services with healthchecks to compose
- [x] 8.10 Add `template/test/sample-document.integration.test.ts` using `testcontainers` for MongoDB + Redis, running `migrate-mongo`, and asserting cache-aside behavior

## 9. Profile-gated dependencies in package.json

- [x] 9.1 Gate `@prisma/client`, `prisma` dependencies behind `{{if eq stack_profile "relational-db"}}`
- [x] 9.2 Gate `mongodb`, `migrate-mongo` dependencies behind `{{if eq stack_profile "nosql-cache"}}`
- [x] 9.3 Gate `ioredis` dependency behind `{{if or (eq stack_profile "relational-db") (eq stack_profile "nosql-cache")}}`
- [x] 9.4 Ensure `default` profile has zero persistence/cache dependencies

## 10. Generated-project README

- [x] 10.1 Add `template/README.md` documenting prerequisites (Node LTS, npm, Docker), npm scripts, `docker compose -f local/docker/docker-compose.yml up --build`, and profile-specific setup notes
- [x] 10.2 Document the `tracing.ts`-must-be-required-first rule with a prominent warning
- [x] 10.3 Document the `app.mongo.*` config convention (nosql-cache profile only)

## 11. Repository documentation updates

- [x] 11.1 Add a "Node.js/TypeScript microservice template" section to the repo root `README.md` matching the shape of the existing Kotlin section (profile table, prompts, register/use commands)
- [x] 11.2 Add the corresponding section to `CLAUDE.md` with the same detail level as the Kotlin section, explicitly calling out the deliberate divergences (S3 in every profile; Redis in `relational-db`; raw Mongo driver; Mongo config under `app.mongo.*`)
- [x] 11.3 Note in `CLAUDE.md` that version bumps must be applied to both `kotlin-microservice/project.json` and `nodejs-typescript/project.json`

## 12. Render-and-run verification (per profile)

- [x] 12.1 Register the template locally: `boilr template save ./nodejs-typescript nodejs-typescript`
- [x] 12.2 Render `stack_profile=default` into a temp dir; run `npm ci && npm run build && npm test`; bring up `docker compose` and hit `/health`, `/metrics`, `POST /blobs`, `GET /blobs/:key`
- [x] 12.3 Render `stack_profile=relational-db` into a temp dir; run the same commands plus the Testcontainers integration test
- [x] 12.4 Render `stack_profile=nosql-cache` into a temp dir; run the same commands plus the Testcontainers integration test
- [x] 12.5 Confirm in each rendered project that no unused profile files leaked through the Go-template gates
