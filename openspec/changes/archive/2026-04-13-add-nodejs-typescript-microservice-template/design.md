## Context

This repo ships boilr templates for project scaffolding. The `kotlin-microservice/` template is the opinionated reference for JVM microservices: Spring Boot 3 + Log4j2 JSON + Micrometer + OTel + AWS SDK v2 for SNS/SQS, with profile-gated persistence (`default`, `relational-db`, `nosql-cache`) and a matching `local/docker/docker-compose.yml` that runs the service alongside LocalStack and an OTel collector. Teams building Node.js/TypeScript services do not have a parallel starting point and currently re-derive the same decisions per service.

This change introduces `nodejs-typescript/` as a sibling template. The design goal is *conceptual parity* with `kotlin-microservice/` — same prompts, same profile shape, same local-dev UX — while using idiomatic Node.js tooling rather than literal translations of JVM APIs. Constraints:

- The template must render with boilr's Go `text/template` engine and profile-gate files via `{{if eq stack_profile ...}}` blocks, both in file contents and path names.
- Generated projects must build and run without network access to private registries (npm only).
- Docker + LocalStack must still be the single external dependency for local dev.
- `kotlin-microservice/` stays frozen inside this change — the two templates must be maintainable in parallel.

## Goals / Non-Goals

**Goals:**

- Ship a `nodejs-typescript/` boilr template that, when used, produces a runnable NestJS service with the same observability, messaging, and local-stack story as `kotlin-microservice/`.
- Mirror the three-profile matrix (`default`, `relational-db`, `nosql-cache`) as a mutually-exclusive `stack_profile` prompt.
- Include a working S3 sample in **every** profile (a deliberate extension beyond the Kotlin template's bundle).
- Include Redis cache in **both** non-default profiles (`relational-db` and `nosql-cache`), not just the NoSQL one.
- Ship one sample domain per profile with a Testcontainers-node integration test that exercises the profile's full persistence + cache path end-to-end.
- Centralize every pinned version in `nodejs-typescript/project.json` so version bumps happen in one place.
- Document the new template in `README.md` and `CLAUDE.md` with the same level of detail as the Kotlin section.

**Non-Goals:**

- Porting the minimal `kotlin/` or `clojure/` templates.
- Supporting GraphQL, gRPC, Kafka, RabbitMQ, or any persistence/cache option beyond Postgres, MongoDB, and Redis.
- Changing anything inside `kotlin-microservice/`.
- Providing a monorepo or Turborepo layout — generated projects are single-package npm projects.
- Shipping production Kubernetes manifests, Helm charts, or CI pipelines — the Kotlin template doesn't, and this one matches.
- Supporting alternative package managers (pnpm/yarn/bun) in v1.
- Supporting ESM-only or CJS-only as a choice — v1 picks one (see Decisions).

## Decisions

### Decision 1: NestJS 10 as the application framework

**Choice:** Use NestJS 10 with decorators, modules, and the built-in DI container.

**Rationale:** NestJS is the closest structural analogue to Spring Boot in the Node ecosystem — modules map to Spring `@Configuration`, providers to `@Service`, lifecycle hooks (`OnModuleInit`, `OnApplicationShutdown`) map to Spring's `SmartLifecycle`. Crucially, the integrations the Kotlin template depends on (Terminus for health, `@willsoto/nestjs-prometheus` for metrics, `@nestjs/config` for config binding) all exist and are maintained. This preserves the "opinionated framework with batteries included" feel that makes the Kotlin template pleasant.

**Alternatives considered:**

- **Fastify (bare)**: faster and leaner, but re-implements DI, config, module composition, and health/metrics by hand — pushes the template toward a micro-framework feel and away from the Spring-like ergonomics.
- **Express (bare)**: same objection as Fastify, plus slower and less modern.
- **tRPC / Hono / Effect**: too niche or too opinionated in a different direction to serve as a general microservice starting point.

NestJS's default HTTP adapter is Express; we keep that default rather than swapping to `@nestjs/platform-fastify` so the sample is the most-documented path. Teams can swap later if they need the perf.

### Decision 2: TypeScript strict + CommonJS output + Node.js LTS

**Choice:** TypeScript 5.x with `"strict": true`, compile to CommonJS, target Node.js 20 LTS (pinned via `node_version`).

**Rationale:** NestJS's documentation, reflection metadata, and decorator tooling are CJS-first. Mixing ESM + NestJS + Prisma + Jest/Vitest still has enough sharp edges in 2025/2026 that v1 picks CJS for zero-friction. Node 20 LTS is the current long-term line; pinning via `project.json` means teams can bump to 22 when it becomes LTS without template changes.

**Alternatives considered:**

- **ESM output**: cleaner long-term, but introduces friction with NestJS CLI tooling and the Prisma client. Defer.
- **Bun runtime**: not yet a credible drop-in for all of NestJS + Prisma + OTel SDK; revisit in a future profile.

### Decision 3: Build tool — `nest build` (tsc under the hood)

**Choice:** Use `nest build` for dev builds and production builds; no `tsup`, no `esbuild` bundler, no `swc` custom wiring.

**Rationale:** The Nest CLI is the path of least surprise for anyone arriving from NestJS docs, handles the `tsconfig-paths` + decorator metadata wiring for us, and produces a `dist/` that the Dockerfile can copy directly. Bundling with esbuild/tsup is a real perf win for startup but adds friction with Prisma's generated client and OTel's instrumentation patching — not worth the complexity in a template.

### Decision 4: Logging — Pino with `nestjs-pino`

**Choice:** `pino` + `nestjs-pino`, configured to emit single-line JSON with trace/span IDs enriched from the active OTel context.

**Rationale:** Pino is the de facto structured logger in Node (fastest, lowest allocation, JSON-first). `nestjs-pino` gives us a drop-in `LoggerModule` that replaces Nest's default logger, adds per-request child loggers, and integrates with `@opentelemetry/instrumentation-pino` to stamp trace IDs — matching what Log4j2's `JsonTemplateLayout` + OTel JVM agent do in the Kotlin template.

**Alternatives considered:**

- **Winston**: slower, more legacy, weaker OTel story.
- **Bunyan**: essentially abandoned.
- **`console` + manual JSON**: regresses DX vs. the Kotlin template.

### Decision 5: Metrics — `@willsoto/nestjs-prometheus` + `prom-client`

**Choice:** Expose a `/metrics` endpoint via `@willsoto/nestjs-prometheus`, backed by `prom-client`'s default registry with default Node/process collectors enabled.

**Rationale:** This is the analogue of Micrometer + Prometheus registry. `prom-client` is the reference Prometheus client for Node; `@willsoto/nestjs-prometheus` wires it into a Nest module with the right defaults. No viable alternative in the Node ecosystem is meaningfully better.

### Decision 6: Tracing — `@opentelemetry/sdk-node` with OTLP/HTTP exporter and auto-instrumentations

**Choice:** Initialize OpenTelemetry in a `tracing.ts` file that is **imported before** the Nest bootstrap (via `--require` in `package.json` scripts and `NODE_OPTIONS` in the Dockerfile), using `@opentelemetry/sdk-node`, `@opentelemetry/auto-instrumentations-node`, and the OTLP/HTTP exporter pointed at the local collector.

**Rationale:** OTel Node requires instrumentation to be initialized before the modules it patches are imported — this is load-order-sensitive in a way the Java agent is not. Putting it in a standalone `tracing.ts` and using `--require` is the officially documented pattern and avoids subtle "no spans" bugs. Auto-instrumentations cover HTTP, `ioredis`, `mongodb`, `pg`, and `@aws-sdk/*` out of the box, which matches the coverage of the Java agent.

**Trade-off:** `--require` means `nest start` needs the flag wired in; the sample scripts handle this explicitly.

### Decision 7: Health — `@nestjs/terminus`

**Choice:** Expose `/health`, `/health/liveness`, `/health/readiness` via `@nestjs/terminus`, with profile-gated health indicators (Postgres indicator in `relational-db`, a hand-wired Mongo indicator in `nosql-cache`, Redis indicator in both non-default profiles).

**Rationale:** Terminus is the canonical NestJS equivalent of Spring Actuator health. The Mongo indicator is hand-wired because (mirroring the Kotlin template's deliberate asymmetry) we are using the raw `mongodb` driver, not an ODM, so Terminus's built-in `MongooseHealthIndicator` does not apply — we ping `admin.command({ ping: 1 })` directly.

### Decision 8: AWS — AWS SDK v3 modular clients; `sqs-consumer` for polling

**Choice:** Use `@aws-sdk/client-sns`, `@aws-sdk/client-sqs`, `@aws-sdk/client-s3` as direct dependencies. Use `bbc/sqs-consumer` wrapped in a Nest provider that starts in `onModuleInit` and stops in `onApplicationShutdown` for the SQS poller.

**Rationale:** SDK v3 is the current supported line; v2 is deprecated. `sqs-consumer` is the community standard for long-polling SQS in Node and handles visibility-timeout heartbeats and graceful shutdown — re-implementing this in the template would be pure liability. The Nest lifecycle wrapper is the analogue of the Kotlin template's `SmartLifecycle`-based poller.

**Alternatives considered:**

- **Hand-rolled poller**: rejected for the same reason the Kotlin template uses a framework integration — too much hidden correctness surface.
- **`@aws-sdk/lib-sqs-consumer`**: does not exist as an official package; the community `sqs-consumer` is what the ecosystem actually uses.

### Decision 9: Relational profile — Prisma + `ioredis`

**Choice:** For `relational-db`, use **Prisma** as the ORM + migration tool, and `ioredis` as the Redis client wrapped in a `SampleCache` provider that implements cache-aside.

**Rationale:**

- **Prisma over TypeORM / Drizzle / raw `pg`:** Prisma is the only option that bundles schema definition, type-safe client generation, and a migration runner in a single tool — matching the role JPA+Flyway plays in the Kotlin template. TypeORM's decorator approach is closer in shape to JPA but its maintenance story is weaker and its type safety is worse. Drizzle is excellent but splits migration tooling from the query layer, adding a step. Raw `pg` pushes too much boilerplate onto the template consumer.
- **`ioredis` over `node-redis`:** `ioredis` has better cluster/sentinel support, is more battle-tested, and is what auto-instrumentation libraries target first. Functionally equivalent for the cache-aside sample.

**Trade-off:** Prisma requires a `prisma generate` step at build time, which the Dockerfile must account for. Documented in the Dockerfile template.

### Decision 10: NoSQL profile — raw `mongodb` driver + `migrate-mongo` + `ioredis`

**Choice:** For `nosql-cache`, depend on the raw `mongodb` Node driver (no ODM), use `migrate-mongo` for schema migrations, and the same `ioredis`-backed `SampleCache` used in `relational-db`.

**Rationale:** This mirrors the Kotlin template's deliberate asymmetry — "raw Mongo driver but Spring Data Redis, because the Mongo abstractions aren't worth their cost, the Redis ones are." Translating that rationale to Node: skip Mongoose (the ODM adds complexity and opinions without proportional benefit for the sample), but keep `ioredis` wrapped in a nice Nest provider (the abstraction is a thin, valuable ergonomics layer).

`migrate-mongo` is the closest Node analogue of Mongock — file-based JS migrations with an up/down lifecycle. It's not as polished as Mongock's Spring integration, but it's the least-bad option in the ecosystem.

**Mongo config key:** lives under `app.mongo.*` in the `@nestjs/config` schema, **not** a well-known key, preserving the Kotlin template's note that auto-config is intentionally absent.

### Decision 11: Redis in both non-default profiles; factored into a shared file

**Choice:** The `relational-db` and `nosql-cache` profiles both include the same `SampleCache` (backed by `ioredis`) and the same Redis health indicator. These files live under `template/src/cache/` and are gated by `{{if or (eq stack_profile "relational-db") (eq stack_profile "nosql-cache")}}`.

**Rationale:** The user explicitly requested Redis in `relational-db` (a divergence from the Kotlin template). Keeping one `SampleCache` file shared across both profiles means the Redis code is written and maintained once. Go-template `or` conditions on both contents and path names are already used elsewhere in this repo.

### Decision 12: S3 sample in the common bundle, not profile-gated

**Choice:** Every profile ships an `S3BlobStorageService` + a `POST /blobs` / `GET /blobs/:key` sample endpoint exercising `PutObject` and `GetObject` against a LocalStack S3 bucket. The LocalStack init script creates the bucket alongside the SNS topic, SQS queue, and subscription.

**Rationale:** The user explicitly asked for an S3 sample in every profile. Placing it in the common bundle avoids profile-gating three identical files. This is a deliberate extension vs. the Kotlin template — we do not back-port it to `kotlin-microservice/` in this change (non-goal), but the README/CLAUDE.md will note the divergence.

### Decision 13: Testing — Vitest + Supertest + `testcontainers` (Node)

**Choice:** Unit and integration tests run on **Vitest**. Integration tests in the profile-gated samples spin up Postgres / MongoDB / Redis via `testcontainers` (the Node port) and use Supertest against a booted Nest application.

**Rationale:**

- **Vitest over Jest**: Vitest is faster, has first-class TypeScript + ESM support, and a better watch-mode DX. Jest is still the NestJS CLI default, so we must override the generated `jest` config — acceptable one-time cost.
- **`testcontainers` (Node)**: direct analogue of Testcontainers Java, same Docker-in-the-loop model as the Kotlin template.

**Trade-off:** Overriding the NestJS CLI's Jest default in generated projects is a minor deviation from Nest defaults; documented clearly in the generated README.

### Decision 14: Docker — multi-stage Dockerfile, `node:{{node_version}}-slim` runtime

**Choice:** Multi-stage `Dockerfile` at the project root. Stage 1: `node:{{node_version}}` builder — `npm ci`, `prisma generate` (relational-db only), `nest build`. Stage 2: `node:{{node_version}}-slim` runtime — copy `dist/`, copy `node_modules` (production only via `npm ci --omit=dev`), set `NODE_OPTIONS=--require ./dist/tracing.js`, run as a non-root user, default command `node dist/main.js`.

**Rationale:** Matches the Kotlin template's production-image focus (the `Dockerfile` stays at the project root; local-dev assets stay under `local/docker/`). Slim base is small and still glibc-based (which is what Prisma's query engine prefers over Alpine's musl).

### Decision 15: Version pinning in `project.json` only

**Choice:** Every pinned version — Node, TypeScript, NestJS, AWS SDK, OTel, Pino, Prisma, Mongo driver, migrate-mongo, ioredis, Testcontainers, Postgres/Mongo/Redis/LocalStack image tags — lives in `nodejs-typescript/project.json` as a prompt default. `package.json` and `docker-compose.yml` reference them via `{{...}}` substitution.

**Rationale:** This is how `kotlin-microservice/` already works. One place to bump versions; template consumers can override at `boilr template use` time.

## Risks / Trade-offs

- **Risk:** OpenTelemetry `--require` load-order is fragile — if a future contributor imports `./tracing` from inside Nest code instead of requiring it at process start, auto-instrumentation silently stops working.
  → **Mitigation:** The generated README has a "Do not move this" note on `tracing.ts`, the npm scripts use `-r ./dist/tracing.js` (not an import), and the Dockerfile sets `NODE_OPTIONS=--require ./dist/tracing.js`.

- **Risk:** Prisma's query engine is a native binary — it breaks if the builder image's libc/arch mismatches the runtime image.
  → **Mitigation:** Both stages of the Dockerfile use `node:{{node_version}}` and `node:{{node_version}}-slim` (both Debian/glibc, same arch); documented in a comment in the Dockerfile template.

- **Risk:** Keeping two parallel templates (Kotlin + Node) in lockstep doubles the "bump a version / add a feature" cost.
  → **Mitigation:** Accepted. Documented in `CLAUDE.md` as a known maintenance tax. The `project.json` pattern keeps it bounded.

- **Risk:** `migrate-mongo` is less actively maintained than Mongock and has a CLI-centric workflow that fits awkwardly into a Nest lifecycle.
  → **Mitigation:** Wrap migration execution in an `onModuleInit` provider that invokes `migrate-mongo`'s programmatic API against the same `MongoClient` Nest already owns. If `migrate-mongo` becomes unmaintained, swap to a hand-rolled migrations table — the blast radius is one file.

- **Risk:** NestJS CLI generates projects with Jest by default; overriding to Vitest means generated consumers diverge from NestJS docs in one specific area.
  → **Mitigation:** Ship a `vitest.config.ts` with clear comments and a README section explaining the deviation and how to revert to Jest in 30 seconds.

- **Risk:** The `default` profile includes S3 + SNS + SQS but no database — some users will expect "default" to mean "minimal." This was also true of the Kotlin template; we keep the framing.
  → **Mitigation:** `README.md` makes the bundle explicit per profile, same wording as the Kotlin section.

- **Risk:** LocalStack's free tier does not include every S3 feature — edge cases (presigned URLs with specific SSE configs, S3 Object Lambda) may behave differently than real S3.
  → **Mitigation:** The sample uses only `PutObject` and `GetObject`, both of which LocalStack community supports reliably.

- **Trade-off:** Using `nest build` (tsc) instead of a bundler means slower cold starts and larger `node_modules` in the runtime image vs. an esbuild-bundled output. Accepted for v1 — simplicity and Prisma/OTel compatibility over startup perf.

- **Trade-off:** CommonJS output (Decision 2) will feel dated to some readers. Accepted for v1 — the NestJS + Prisma + OTel + Jest/Vitest ESM story is still rough enough that CJS is the stable choice.

## Migration Plan

Not applicable — this change adds a new template and does not modify any existing generated projects or runtime systems. Users who want the new template run `boilr template save ./nodejs-typescript nodejs-typescript` after the change lands; existing Kotlin template users are unaffected.

## Open Questions

- **Should `relational-db` default the cache TTL to a specific value** (e.g., 5 minutes) or leave it configurable with no default? Leaning: configurable, default 300s, documented in `application.yml`-equivalent (`config/*.ts`). To confirm when drafting the sample.
- **Which Nest `ConfigModule` schema validator**: `joi` vs `zod`. Leaning `zod` (already idiomatic in modern TS, no extra types package), but `@nestjs/config` docs use Joi. Decide at implementation time; does not affect the spec.
- **Should we ship a GitHub Actions CI file** in the generated project? The Kotlin template does not. Keeping parity → no. Revisit as a follow-up change if requested.
