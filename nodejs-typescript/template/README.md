# {{app_name}}

Opinionated NestJS microservice scaffolded from the `nodejs-typescript` template.

**Stack profile:** `{{stack_profile}}`

## Requirements

- Node.js >= {{node_version}}
- npm
- Docker (for the local stack and integration tests)

## Install

```bash
npm ci
{{- if eq stack_profile "relational-db" }}
npx prisma generate
{{- end }}
```

## Scripts

| Script | Purpose |
|---|---|
| `npm run build` | Compile TypeScript into `dist/` |
| `npm run start` | Run the compiled app (production entry) |
| `npm run start:dev` | Watch mode with ts-node |
| `npm test` | Unit tests via Vitest |
| `npm run test:e2e` | Integration tests (spins up Testcontainers) |
| `npm run lint` | ESLint |
{{- if eq stack_profile "relational-db" }}
| `npm run prisma:migrate` | Apply Prisma migrations |
{{- end }}
{{- if eq stack_profile "nosql-cache" }}
| `npm run migrate:up` | Apply migrate-mongo migrations |
{{- end }}

## Local stack

```bash
docker compose -f local/docker/docker-compose.yml up --build
```

Brings up this service plus LocalStack (SNS / SQS / S3), an OTel collector{{if eq stack_profile "relational-db"}}, Postgres, and Redis{{end}}{{if eq stack_profile "nosql-cache"}}, MongoDB, and Redis{{end}}.

LocalStack initialization creates:

- SNS topic `{{app_name}}-events`
- SQS queue `{{app_name}}-events-queue`, subscribed to the topic
- S3 bucket `{{app_name}}-blobs`

## Observability

- `GET /metrics` — Prometheus scrape endpoint
- `GET /health`, `/health/liveness`, `/health/readiness` — Terminus health checks
- OTLP/HTTP traces exported to `OTEL_EXPORTER_OTLP_ENDPOINT` (defaults to the local collector)

### ⚠️ Do not move `src/tracing.ts`

OpenTelemetry for Node requires instrumentation to be initialized **before** the modules it patches are imported. `src/tracing.ts` is loaded via Node's `--require` flag:

- npm scripts use `-r ./dist/tracing.js` (production) / `-r ./src/tracing.ts` (dev)
- The Dockerfile sets `NODE_OPTIONS=--require=./dist/tracing.js`

Importing `tracing.ts` from inside Nest code instead of requiring it at process start will silently disable auto-instrumentation.

## AWS integration

The AWS SDK v3 clients honor `AWS_ENDPOINT_URL`, so LocalStack is transparent in local dev. In production, unset the env var and the real AWS endpoints take over.

- `SnsPublisher.publish(payload)` — publish to the topic in `APP_SNS_TOPIC_ARN`
- `SqsPoller` — starts on module init, dispatches to `SampleMessageHandler`
- `S3BlobStorage.put(key, body)` / `get(key)` — wraps `@aws-sdk/client-s3`
- `POST /blobs` / `GET /blobs/:key` — sample controller exercising S3

{{- if eq stack_profile "nosql-cache" }}

## MongoDB configuration

Mongo settings live under `app.mongo.*` in `src/config/configuration.ts` (`APP_MONGO_URI`, `APP_MONGO_DATABASE`) — **not** under a `spring.data.mongodb.*`-equivalent namespace. This template deliberately uses the raw `mongodb` Node driver, not an ODM, and there is no Spring-Data-Mongo-style auto-config.

Migrations are `migrate-mongo` files under `migrations/` and are applied on startup by `MigrateRunner` (invoked in `onModuleInit`). You can also run them manually with `npm run migrate:up`.
{{- end }}

## Testing

Integration tests under `test/` use [`testcontainers`](https://github.com/testcontainers/testcontainers-node) to spin up real{{if eq stack_profile "relational-db"}} Postgres and Redis{{end}}{{if eq stack_profile "nosql-cache"}} MongoDB and Redis{{end}} containers. They require a running Docker daemon.
