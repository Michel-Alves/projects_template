# Project Templates Project

This repository stores my `boilr` templates.

## Install templates from this repository

Clone the repository and save each template folder into your local `boilr` registry.

```bash
git clone https://github.com/Michel-Alves/projects_template.git
cd projects_template

boilr template save ./kotlin kotlin
boilr template save ./kotlin-microservice kotlin-microservice
boilr template save ./nodejs-typescript nodejs-typescript
boilr template save ./clojure clojure
```

You can use any tag name you want, but using the folder name keeps things simple.

After saving a template, generate a new project with:

```bash
boilr template use <template-tag> <target-dir>
```

Example:

```bash
boilr template use kotlin ~/Workspace/my-kotlin-app
```

`boilr` will prompt you for the values declared in the template `project.json` file and render the files into the target directory.

## Create a new template

Each template in this repository lives in its own folder. The basic structure is:

```text
my-template/
├── project.json
└── template/
    ├── README.md
    └── ...
```

Notes:

- `project.json` defines the values that `boilr` will ask for.
- Only the contents of the `template/` directory are copied and rendered.
- An optional local `README.md` can be added to document a specific template.

### Example `project.json`

This repository already uses lowercase keys such as `author`, `app_name`, and `template_type`. Keep the same naming style when creating new templates.

```json
{
  "author": "michelsilves",
  "app_name": "myapp",
  "version": "0.1.0",
  "template_type": [
    "default",
    "cli"
  ]
}
```

Notes:

- Scalar values become prompt defaults.
- Arrays become selectable options during template generation.
- The same key names should be used inside the files in `template/`.

### Use Go templates inside `template/`

`boilr` templates are powered by Go `text/template`, so you can use placeholders and control structures inside file contents and file names.

Examples:

```text
{{app_name}}
{{author}}
{{if eq template_type "cli"}}cmd/{{app_name}}{{end}}
{{range template_type}}- {{.}}{{end}}
```

Example file and directory names:

```text
template/
├── {{app_name}}.md
└── src/
    └── {{app_name}}/
```

Useful patterns:

- Value substitution: `{{app_name}}`
- Conditionals: `{{if eq template_type "cli"}}...{{end}}`
- Loops: `{{range template_type}}...{{end}}`
- Whitespace trimming: `{{- ... -}}`

Keep the template simple at first: define the prompts in `project.json`, create the files under `template/`, then save and test the template locally with `boilr template save` and `boilr template use`.

## Current templates

- [kotlin](./kotlin) — general-purpose Kotlin/Gradle template with `default`/`cli`/`web`/`db`/`web-db` stack profiles
- [kotlin-microservice](./kotlin-microservice) — Spring Boot 3 microservice with Actuator, Micrometer/Prometheus, OpenTelemetry, Log4j2 JSON logging, AWS SDK v2 SNS/SQS, and a `docker-compose` LocalStack stack. Has two optional stack profiles: `relational-db` (Spring Data JPA + PostgreSQL 16 + Flyway + Testcontainers Postgres) and `nosql-cache` (raw MongoDB Java sync driver + Mongock + Spring Data Redis + Testcontainers Mongo/Redis).
- [nodejs-typescript](./nodejs-typescript) — NestJS 10 microservice mirroring the Kotlin one: Terminus health, `@willsoto/nestjs-prometheus`, OpenTelemetry Node SDK, Pino JSON logging, AWS SDK v3 SNS/SQS/**S3**, `sqs-consumer`, Vitest + Testcontainers-node. Profiles: `relational-db` (Prisma + Postgres + `ioredis` cache) and `nosql-cache` (raw `mongodb` driver + `migrate-mongo` + `ioredis` cache). **Divergences from the Kotlin template:** ships an S3 sample in every profile, and Redis cache is present in **both** non-default profiles (not just `nosql-cache`).
- [clojure](./clojure/README.md) — minimal Leiningen Clojure template

### Node.js/TypeScript microservice template

Opinionated NestJS 10 microservice template. Common bundle (every profile): NestJS HTTP + DI, `nestjs-pino` JSON logging, Prometheus `/metrics` via `@willsoto/nestjs-prometheus`, `@nestjs/terminus` health endpoints, OpenTelemetry Node SDK with OTLP/HTTP exporter, AWS SDK v3 SNS publisher, `sqs-consumer`-backed SQS poller, S3 `PutObject`/`GetObject` sample, multi-stage Dockerfile, and a `local/docker/docker-compose.yml` running the service alongside LocalStack (SNS/SQS/**S3**) and an OpenTelemetry collector.

**Stack profiles** (selected via `stack_profile` prompt):

| Profile | Extra dependencies |
|---|---|
| `default` | none — HTTP + messaging + S3 only |
| `relational-db` | Prisma (schema + migrations + generated client), PostgreSQL driver, `ioredis` cache-aside, `@testcontainers/postgresql` for integration tests. Adds `postgres:{{postgres_image_tag}}` and `redis:{{redis_image_tag}}` services to the compose stack with healthchecks; the `app` service waits on both. Ships a Prisma `SampleEntity`, an initial migration, a repository, a cache-aside service, and a Testcontainers-backed integration test. |
| `nosql-cache` | **Raw** MongoDB Node driver (`mongodb`, **not** Mongoose), `migrate-mongo` for migrations, `ioredis` cache-aside, `@testcontainers/mongodb` + generic Redis container for integration tests. Adds `mongo:{{mongo_image_tag}}` and `redis:{{redis_image_tag}}` services with healthchecks; the `app` service waits on both. Ships a `SampleDocument` type, a repository wrapping a `Collection<SampleDocument>`, a `migrate-mongo` migration, a cache-aside service, a hand-wired Mongo health indicator, and a Testcontainers integration test. Mongo config lives under `app.mongo.*` (not a well-known key) because there is no Spring-Data-Mongo analogue. |

**Register and use:**

```bash
boilr template save ./nodejs-typescript nodejs-typescript
boilr template use nodejs-typescript ~/Workspace/my-svc
```

Then, in the generated project:

```bash
npm ci
npm run build
docker compose -f local/docker/docker-compose.yml up --build
```

## Go further

- [boilr README](https://github.com/Ilyes512/boilr/blob/main/README.md)
- [boilr usage](https://github.com/Ilyes512/boilr/blob/main/wiki/Usage.md)
- [boilr creating templates](https://github.com/Ilyes512/boilr/blob/main/wiki/Creating-Templates.md)
- [Go `text/template`](https://pkg.go.dev/text/template)