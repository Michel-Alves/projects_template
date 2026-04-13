## Why

The in-flight `add-kotlin-microservice-db-profile` change introduces a `stack_profile` prompt to `kotlin-microservice/` and adds a `relational-db` profile (Postgres + JPA + Flyway). Many teams, however, build services on a document store plus a distributed cache rather than a relational database — and wire MongoDB, Spring Data MongoDB, Mongock, Redis, and Spring Data Redis by hand every time. Adding a `nosql-cache` profile encodes that canonical MongoDB + Redis shape once, slotting into the `stack_profile` slot reserved by the prior change without restructuring the template.

## What Changes

- Extend the `stack_profile` prompt in `kotlin-microservice/project.json` (added by `add-kotlin-microservice-db-profile`) with a new value: `nosql-cache`. Final set becomes `default`, `relational-db`, `nosql-cache`.
- The `nosql-cache` profile adds, on top of `default`:
  - **MongoDB Java sync driver** (`org.mongodb:mongodb-driver-sync`) — the raw driver, **not** Spring Data MongoDB. A small `MongoConfig` `@Configuration` registers a `MongoClient` and a `MongoDatabase` bean from properties; application code uses the driver API directly (no `MongoRepository`, no `MongoTemplate`).
  - **Mongock** via `mongock-springboot-v3` + `mongodb-sync-v4-driver` (the raw-driver module, **not** `mongodb-springdata-v4-driver`). This keeps Mongock's Spring Boot lifecycle (runs on startup, fail-fast, lock collection) without pulling Spring Data Mongo back in.
  - A hand-wired `MongoHealthIndicator` exposed under `/actuator/health` (~10 lines — pings the admin DB). Needed because without `spring-boot-starter-data-mongodb` there is no auto-configured Mongo health indicator.
  - **Spring Data Redis** (`spring-boot-starter-data-redis`) with the Lettuce client. Kept as the starter (not the raw Lettuce client) because Spring Data Redis is a thin wrapper, gives a free Redis health indicator via Actuator, and matches how most teams already use Redis from Spring. **Asymmetric with Mongo on purpose.**
  - A sample document class (plain Kotlin `data class`, no `@Document` annotation), a thin `SampleDocumentRepository` that wraps a `MongoCollection<SampleDocument>` via the driver's POJO codec, and a first Mongock `ChangeUnit` that creates an index on the sample collection.
  - A sample `RedisTemplate<String, String>` usage (cache-aside helper) — no Spring Cache abstraction in v1, to keep the surface area small.
  - Testcontainers `mongodb` module plus a `GenericContainer("redis:{{redis_image_tag}}")` for Redis (Testcontainers has no dedicated Redis module), and a `@SpringBootTest` integration test using `@DynamicPropertySource` to point the app at both containers. The test exercises the driver-based repository and the Redis cache helper end-to-end.
- Conditional templating in `build.gradle.kts`, `application.yml`, `application-local.yml`, and `local/docker/docker-compose.yml` to switch the new pieces on for `nosql-cache` only. `Application.kt` is unchanged. Spring Data Redis auto-configures from properties; the Mongo side is wired by the template's own `MongoConfig` `@Configuration`. The root-level `Dockerfile` does not change — same fat jar in all shapes.
- `local/docker/docker-compose.yml` (when profile is `nosql-cache`) gains a `mongo` service (`mongo:7`, named volume, root creds via env vars) and a `redis` service (`redis:7-alpine`, no auth for local dev). The `app` service `depends_on` both being healthy. The `local` Spring profile points Spring Data at them.
- Repo docs updated: `CLAUDE.md` and root `README.md` document the new `nosql-cache` profile alongside `relational-db`; the rendered template's `README.md` adapts based on the chosen profile.
- **Mutually exclusive with `relational-db`.** Selecting one profile excludes the other — v1 does not combine JPA and Mongo/Redis in a single generated project. A future `relational-db-cache` or `polyglot` profile can layer them if asked.
- **No removal of existing behavior.** Choosing `default` or `relational-db` produces exactly the same output as after `add-kotlin-microservice-db-profile` lands.

## Capabilities

### New Capabilities
<!-- None — this change extends the capability added/modified by add-kotlin-microservice-db-profile rather than introducing a new one. -->

### Modified Capabilities
- `kotlin-microservice-template`: The `stack_profile` prompt (added by `add-kotlin-microservice-db-profile`) gains a third value `nosql-cache`. New requirements describe the raw Mongo driver + Mongock (raw-driver module) + Spring Data Redis bundling, the `MongoConfig` / `MongoHealthIndicator` that the template ships because no Spring Data Mongo auto-config is present, the Mongo and Redis services in `docker-compose.yml`, and the Testcontainers-based integration test. The existing `default` and `relational-db` requirements are untouched.

## Impact

- **Dependency on `add-kotlin-microservice-db-profile`**: This change assumes that change has landed (or lands first). It builds on the `stack_profile` prompt and conditional templating scaffold introduced there. If the ordering slips, this change must be rebased to introduce `stack_profile` itself — flagged in design.
- **Modified files in this repo**:
  - `kotlin-microservice/project.json` — append `nosql-cache` to the `stack_profile` array; pin `mongo_image_tag`, `redis_image_tag`, `mongock_version`. The Mongo driver version is NOT pinned — Spring Boot's BOM manages `org.mongodb:mongodb-driver-sync` transitively, and pinning it explicitly drifts from the managed `mongodb-driver-core` and fails at runtime with `NoClassDefFoundError`.
  - `kotlin-microservice/template/build.gradle.kts` — extend the profile conditional with a `nosql-cache` branch adding `mongodb-driver-sync`, `spring-boot-starter-data-redis`, `mongock-springboot-v3`, `mongodb-sync-v4-driver`, and Testcontainers `mongodb` + core modules.
  - `kotlin-microservice/template/src/main/resources/application.yml` — conditional `app.mongo.*` block (custom namespace read by `MongoConfig`), `spring.data.redis.*` block (read by Spring Boot auto-config), and `mongock.*` block.
  - `kotlin-microservice/template/src/main/resources/application-local.yml` — conditional `app.mongo.uri` and `spring.data.redis.host`/`port` pointing at the compose services.
  - `kotlin-microservice/template/local/docker/docker-compose.yml` — conditional `mongo` and `redis` services + `depends_on` wiring on `app`.
  - `kotlin-microservice/template/README.md` — conditional section describing Mongo/Redis/Mongock when the profile is selected.
  - `CLAUDE.md` and root `README.md` — document the new `nosql-cache` profile alongside `relational-db`.
- **New files in this repo** (only rendered when `stack_profile=nosql-cache`):
  - `kotlin-microservice/template/src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/persistence/MongoConfig.kt` — `@Configuration` that builds `MongoClient` + `MongoDatabase` beans from `app.mongo.*` properties. No POJO codec registry — the repository layer uses raw `org.bson.Document`.
  - `kotlin-microservice/template/src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/persistence/MongoHealthIndicator.kt` — minimal `HealthIndicator` that runs `{ ping: 1 }` on the admin DB.
  - `kotlin-microservice/template/src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/persistence/SampleDocument.kt` — plain Kotlin `data class`, no annotations.
  - `kotlin-microservice/template/src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/persistence/SampleDocumentRepository.kt` — wraps a `MongoCollection<org.bson.Document>` with small `findById`/`findByName`/`insert`/`deleteById` methods, converting between `SampleDocument` and `Document` internally.
  - `kotlin-microservice/template/src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/persistence/migration/V001__create_sample_index.kt` — Mongock `@ChangeUnit` taking a `MongoDatabase`, creates an index on `sample_documents`.
  - `kotlin-microservice/template/src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/cache/SampleCache.kt` — Redis cache-aside helper over `StringRedisTemplate`.
  - `kotlin-microservice/template/src/test/kotlin/{{tld}}/{{author}}/{{app_name}}/persistence/SampleDocumentRepositoryIntegrationTest.kt` — `@SpringBootTest` with Testcontainers Mongo + generic Redis container, `@DynamicPropertySource` wires `app.mongo.uri` and `spring.data.redis.host`/`port`.
- **Tests for the nosql-cache profile** require Docker (Testcontainers Mongo + Redis container). Matches the existing Docker requirement for OTel/SQS tests and the `relational-db` profile — no new constraint.
- **Version pins**: `mongo_image_tag=7`, `redis_image_tag=7-alpine`, `mongock_version=5.4.4` (verified at apply time). Mongo driver version is NOT pinned — Spring Boot's BOM manages it. No bumps to existing pins.
- **Why raw Mongo driver but Spring Data Redis (asymmetry, deliberate)**: Spring Data MongoDB is a heavy abstraction (`MongoTemplate`, repositories, converters, index auto-creation) that most teams end up fighting; the raw Java sync driver is idiomatic and keeps the template honest. Spring Data Redis, by contrast, is a thin wrapper around Lettuce that gives a free Actuator health indicator and is how most Spring teams already reach for Redis — dropping it would cost more than it saves.
- **Out of scope**:
  - Reactive variants (`spring-boot-starter-data-mongodb-reactive`, Lettuce reactive API).
  - Redis Cluster / Sentinel / TLS — single-node local only in v1.
  - Spring Cache abstraction (`@Cacheable`) — a direct `RedisTemplate` sample is enough for v1; adding `@EnableCaching` can come later without breaking the profile.
  - DynamoDB, Cassandra, Elasticsearch, or any other NoSQL engine — `nosql-cache` in v1 means MongoDB + Redis specifically. Naming keeps the door open for additional nosql profiles (`nosql-dynamo`, etc.) if needed, though `nosql-cache` is deliberately concrete rather than generic.
  - Combining `relational-db` and `nosql-cache` in one generated project.
