## Context

The in-flight change `add-kotlin-microservice-db-profile` introduces a `stack_profile` prompt to `kotlin-microservice/` with values `default` and `relational-db`. It was deliberately named `relational-db` (not `db`) precisely to reserve room for a NoSQL shape. This change fills that slot with `nosql-cache` = MongoDB (raw driver) + Redis (Spring Data) + Mongock migrations.

The user picked MongoDB + Redis combined over two separate profiles (`nosql-db` + `cache`) because the two technologies are almost always used together in practice — a document store for records of truth, a distributed cache for hot reads. Splitting them would force every team to pick both profiles anyway, and a "pick one but not the other" outcome isn't a supported use case for this template.

A deliberate asymmetry in dep choice: **raw Mongo driver, but Spring Data Redis**. The user explicitly chose this after discussion. Rationale captured in D3 below.

Constraints inherited from the parent change and the base template:
- `stack_profile` prompt, conditional templating with `{{ if eq stack_profile "nosql-cache" }}...{{ end }}`, pinned versions in `project.json`, local integration via `local/docker/docker-compose.yml` + `application-local.yml`, Testcontainers-backed integration tests that require Docker.
- The three `stack_profile` values (`default`, `relational-db`, `nosql-cache`) are **mutually exclusive**. No profile combines relational + NoSQL in v1.
- The Dockerfile is profile-agnostic — both Mongo driver and Spring Data Redis ride the same fat jar regardless of profile selection (only enabled/disabled by Gradle conditionals).

Stakeholders: template author and downstream service teams running `boilr template use kotlin-microservice ...`.

## Goals / Non-Goals

**Goals:**
- Add `nosql-cache` as a third `stack_profile` value. `default` and `relational-db` produce byte-identical output to whatever `add-kotlin-microservice-db-profile` ships. `nosql-cache` adds the Mongo + Redis + Mongock bundle with zero manual edits after `boilr template use`.
- Use the **raw MongoDB Java sync driver** (`mongodb-driver-sync`) with a template-owned `MongoConfig` that builds `MongoClient` + `MongoDatabase` beans from `app.mongo.*` properties. No Spring Data Mongo on the classpath.
- Use **Mongock** (`mongock-springboot-v3` + `mongodb-sync-v4-driver`) for document/schema migrations. `ChangeUnit` classes take a raw `MongoDatabase`.
- Use **Spring Data Redis** (`spring-boot-starter-data-redis`) with the default Lettuce client. Keep Spring Boot auto-config for Redis.
- Ship a minimal sample: plain Kotlin `data class`, a thin repository wrapping a `MongoCollection<SampleDocument>`, one Mongock `ChangeUnit` creating an index, one `RedisTemplate` cache-aside helper, and one `@SpringBootTest` integration test using Testcontainers Mongo + a generic Redis container.
- Manually wire a `MongoHealthIndicator` so `/actuator/health` still reports Mongo status (lost when we skipped Spring Data Mongo). Redis health comes for free from Spring Data Redis.
- `nosql-cache` MUST NOT change anything observable when `default` or `relational-db` is selected.

**Non-Goals:**
- Reactive variants (`spring-boot-starter-data-redis-reactive`, Mongo reactive streams driver). The template's HTTP stack is blocking.
- Redis cluster / Sentinel / TLS. Single-node local Redis only.
- `@EnableCaching` / `@Cacheable` abstraction. Direct `RedisTemplate` usage is enough for v1.
- Spring Data Mongo — deliberately excluded per D3.
- Combining `relational-db` and `nosql-cache` in one generated project.
- Additional NoSQL engines (DynamoDB, Cassandra, Elasticsearch). The name `nosql-cache` is concrete — a future `nosql-dynamo` profile would sit alongside, not replace it.
- A sample REST controller over the Mongo collection.

## Decisions

### D1. Depend on `add-kotlin-microservice-db-profile` landing first
- **Choice**: This change assumes the `stack_profile` prompt and its conditional-templating scaffold are already present. We append a third value, we don't introduce the prompt ourselves.
- **Why**: Avoids a three-way merge between two changes that both touch `project.json`, `build.gradle.kts`, `application*.yml`, and `docker-compose.yml`. The parent change is actively being implemented; this one rebases on top.
- **Alternatives considered**: Land first (rejected — we'd then own the `stack_profile` scaffold, and `add-kotlin-microservice-db-profile` would have to rebase). Land in parallel with a manual merge (rejected — wasted diff churn).
- **Contingency**: If the parent change slips, this one must be rebased to also introduce the prompt. The rebase is mechanical — conditional blocks are already scoped by profile value, so a third arm is additive.

### D2. Value name is `nosql-cache`, not `nosql-db` or `mongo-redis`
- **Choice**: `nosql-cache`.
- **Why**: The bundle is specifically a document store **plus** a distributed cache — that combination is the selling point. `nosql-db` would suggest MongoDB only, inviting a later renaming when someone asks "where's the cache?". `mongo-redis` names the tools, not the intent, and locks the label to implementation choices that could change.
- **Alternatives considered**: `nosql-db` (too narrow), `mongo-redis` (too implementation-coupled), `document-cache` (accurate but awkward), `nosql` (too broad — future `nosql-dynamo` wouldn't fit cleanly next to it).

### D3. Raw Mongo driver (no Spring Data Mongo), Spring Data Redis kept
- **Choice**: Depend on `mongodb-driver-sync` directly; application code uses `MongoClient` / `MongoDatabase` / `MongoCollection` APIs. For Redis, use `spring-boot-starter-data-redis` with Lettuce and `RedisTemplate`.
- **Why (Mongo side)**: Spring Data MongoDB is a heavy abstraction — `MongoTemplate`, repository interfaces, custom converters, index-auto-creation, query derivation. Most teams end up fighting it for non-trivial queries. The raw Java sync driver is small, idiomatic, well-documented, and keeps the template honest about what "using MongoDB" actually looks like.
- **Why (Redis side asymmetry)**: Spring Data Redis is a thin wrapper over Lettuce. It costs roughly one extra dep and nothing in application complexity, and in return we get: auto-configuration from `spring.data.redis.*` properties, a free `/actuator/health` Redis indicator, `RedisTemplate`/`StringRedisTemplate` with sensible serializers, and the idiomatic Spring path that most teams already know. Dropping to raw Lettuce would save almost nothing and cost ergonomics.
- **Consequence**: We lose Spring Boot's Mongo auto-config. The template must ship a small `MongoConfig` `@Configuration` that builds the `MongoClient` and exposes `MongoDatabase` as a bean (D5), and a hand-rolled `MongoHealthIndicator` (D6). The custom property namespace is `app.mongo.*` (D4) because `spring.data.mongodb.*` is Spring Data Mongo's namespace and we're not using it.
- **Alternatives considered**: Spring Data Mongo (rejected — the abstraction tax is not worth it for a template that should show idiomatic driver usage); raw Lettuce for Redis (rejected — loses Actuator health, loses auto-config, saves almost nothing).

### D4. Config namespace: `app.mongo.*` for Mongo, `spring.data.redis.*` for Redis
- **Choice**: Mongo config is read from `app.mongo.uri` and `app.mongo.database` via `@ConfigurationProperties("app.mongo")` inside `MongoConfig`. Redis config stays on Spring Boot's auto-configured `spring.data.redis.host`/`port`/`password` keys.
- **Why**: Using `spring.data.mongodb.*` would be misleading — teams reading the config would reasonably expect Spring Data Mongo behavior (index auto-creation, repositories, etc.) and there would be none. A distinct `app.mongo.*` namespace makes it obvious the template owns this wiring.
- **Alternatives considered**: `mongo.*` (too generic, could collide), `spring.data.mongodb.*` (misleading), env-var-only (loses typed config).

### D5. `MongoConfig` builds `MongoClient` + `MongoDatabase` beans; repository uses raw `org.bson.Document`
- **Choice**: A `@Configuration` class registers a `MongoClient` from `app.mongo.uri` (closed via `@Bean(destroyMethod = "close")`) and a `MongoDatabase` from `mongoClient.getDatabase(app.mongo.database)`. **No POJO codec registry.** The repository layer operates on `MongoCollection<org.bson.Document>` and converts between `SampleDocument` (a plain Kotlin `data class`) and `Document` at its own boundary, mapping the Kotlin `id` field to Mongo's `_id` field.
- **Why**: The Mongo POJO codec plus `automatic(true)` is fragile with Kotlin data classes. The codec scans getters and needs a way to materialize instances during deserialization; Kotlin data classes don't emit a synthetic no-arg constructor and their constructor-parameter annotations don't propagate to the getter reliably. During apply we tried `@BsonId` and then `@get:BsonId` on the `id` field — both produced round-trips where the deserialized document had `id == ""` because the codec's getter-based property discovery didn't see the annotation on the synthetic getter. Rather than ship a template that "works if you understand Kotlin annotation target resolution", we chose raw `Document` with a small manual mapping inside the repository.
- **Trade-off**: Manual conversion is ~6 lines per document type (`toBson()` / `toSampleDocument()`) instead of zero. Acceptable for a sample — it makes the `_id` mapping explicit and leaves `SampleDocument` annotation-free.
- **Alternatives considered (all tried or rejected)**: POJO codec with `@BsonId` (tried, failed on round-trip), POJO codec with `@get:BsonId` (tried, failed on round-trip), `@BsonCreator` + `@BsonProperty` on constructor parameters (more boilerplate than manual conversion), `bson-kotlinx` + `kotlinx.serialization` (pulls another serialization framework into a template that today has none), `kotlin-noarg` Gradle plugin (requires a marker annotation and extra plugin setup for a sample).

### D6. Hand-rolled `MongoHealthIndicator`, Redis health from Spring Data Redis
- **Choice**: A tiny `@Component class MongoHealthIndicator(val client: MongoClient) : HealthIndicator` that runs `client.getDatabase("admin").runCommand(Document("ping", 1))` inside `health()` and returns `Health.up()` on success, `Health.down(ex)` on failure.
- **Why**: Without `spring-boot-starter-data-mongodb` there is no auto-registered `MongoHealthContributor`. Actuator health is table-stakes for a production-oriented template, so we replicate the ~10 lines manually. Redis auto-registers its own health indicator from `spring-boot-starter-data-redis` — no work needed.
- **Alternatives considered**: No Mongo health at all (rejected — breaks the "production-ready" promise), pull Spring Data Mongo just for the health indicator (rejected — contradicts D3 for a trivial amount of code).

### D7. Mongock with the raw-driver module (`mongodb-sync-v4-driver`), not the Spring Data module
- **Choice**: Depend on `io.mongock:mongock-springboot-v3` + `io.mongock:mongodb-sync-v4-driver`. **Not** `mongodb-springdata-v4-driver` (which requires Spring Data Mongo). `ChangeUnit` classes take a `MongoDatabase` parameter injected by Mongock; they run `db.getCollection(...).createIndex(...)` directly.
- **Why**: Keeps Mongock's Spring Boot lifecycle (runs on startup before the app is ready, fail-fast, lock collection for concurrent instances) without re-introducing Spring Data Mongo. This is the exact reason the `mongodb-sync-v4-driver` module exists.
- **Alternatives considered**: `mongodb-springdata-v4-driver` (rejected — pulls Spring Data Mongo back in, defeats D3), Liquibase Mongo extension (changesets in XML/YAML not Kotlin, heavier), hand-rolled `@PostConstruct` migration runner (OK for <5 migrations, rots quickly after that and gives up Mongock's lock behavior for multi-instance deploys), Flamingock (Mongock's successor, too young to bet on in a template), mongobee (unmaintained — do not use).

### D8. Mongock version `5.4.x` (to confirm at apply time)
- **Choice**: Pin `mongock_version` in `project.json` to the latest Mongock `5.4.x` release that's compatible with Spring Boot 3.3.x. Confirm the exact value when writing `build.gradle.kts` by checking Mongock's compatibility matrix.
- **Why**: Mongock 5.4 is the current line and explicitly supports Spring Boot 3. Earlier 5.x lines support Spring Boot 2 only.
- **Alternatives considered**: Pin to a specific patch version now (rejected — would force a re-spin if that patch is broken); leave unpinned / let the BOM manage it (rejected — Spring Boot's BOM doesn't manage Mongock).

### D9. Testcontainers: `MongoDBContainer` + `GenericContainer("redis:...")`
- **Choice**: Add `org.testcontainers:mongodb` to `testImplementation`. Redis uses `GenericContainer(DockerImageName.parse("redis:{{redis_image_tag}}")).withExposedPorts(6379)` — Testcontainers has no first-party Redis module. The integration test is a `@SpringBootTest` with both containers as `companion object` fields (started once per class) and a `@DynamicPropertySource` that injects `app.mongo.uri`, `app.mongo.database`, `spring.data.redis.host`, and `spring.data.redis.port`.
- **Why**: Mirrors the LocalStack pattern already used in this template. `@DynamicPropertySource` is the canonical Spring Boot 3 way to wire Testcontainers. Running both containers in one test class keeps cold-start cost to one image pull pair and exercises the cache-aside helper against both backends in the same test.
- **Alternatives considered**: Embedded Mongo like `flapdoodle` (rejected — slower cold start in practice, and it diverges from real Mongo on edge cases), `redis-embedded` (unmaintained), separate test classes for Mongo and Redis (more boilerplate, doesn't exercise the combined path).

### D10. Compose adds `mongo` and `redis` services; `app` waits on healthchecks
- **Choice**: `local/docker/docker-compose.yml` gains conditional `mongo` and `redis` services when `stack_profile == nosql-cache`:
  - `mongo`: `mongo:{{mongo_image_tag}}`, named volume for `/data/db`, root creds from env, healthcheck `mongosh --eval 'db.adminCommand({ ping: 1 })'`.
  - `redis`: `redis:{{redis_image_tag}}`, no volume (cache is ephemeral by design), healthcheck `redis-cli ping`.
  - `app.depends_on` gains `mongo` and `redis` with `condition: service_healthy`.
- **Why**: Same pattern the parent change uses for `postgres`. Spinning these up only when the profile is selected avoids surprising `default`/`relational-db` users with 300MB of images.
- **Risk**: YAML is whitespace-sensitive; three-way conditional (none / postgres / mongo+redis) compounds the fragility of the parent change's two-way conditional. → Mitigation: render the template with all three profile values during implementation and run `docker compose -f local/docker/docker-compose.yml config` on each to YAML-lint before declaring done.
- **Alternatives considered**: Always render the services as commented-out (rejected — users would still have to edit the compose file to enable), separate `docker-compose.nosql.yml` overlay (rejected — extra command for the user).

### D11. `application.yml` / `application-local.yml` layout
- **Choice**: Base `application.yml` gains conditional blocks: `app.mongo.database: {{app_name}}`, `mongock.migration-scan-package: {{tld}}.{{author}}.{{app_name}}.persistence.migration`, `spring.data.redis.*` defaults (host `localhost`, port `6379`). `application-local.yml` sets `app.mongo.uri: mongodb://root:root@mongo:27017/{{app_name}}?authSource=admin` and `spring.data.redis.host: redis` pointing at the compose service names.
- **Why**: Mirrors how `application-local.yml` already targets compose service hostnames for LocalStack. Deployed environments override via env vars (`APP_MONGO_URI`, `SPRING_DATA_REDIS_HOST`, etc.) which Spring picks up automatically.
- **Alternatives considered**: Single-file config with env-var placeholders everywhere (loses the clean local-vs-prod split).

### D12. Sample is intentionally tiny and exercises both backends together
- **Choice**: One `SampleDocument(id: String, name: String)` with a `SampleDocumentRepository` exposing `findById`/`insert`/`deleteById` over `MongoCollection<SampleDocument>`. One Mongock `V001__create_sample_index` ChangeUnit creating a unique index on `name`. One `SampleCache` that wraps `StringRedisTemplate` with `get(key)` / `put(key, value)` / `evict(key)`. One `SampleDocumentService` that combines them: `getByName(name)` checks the cache, falls back to Mongo, writes through to the cache. One `@SpringBootTest` that exercises the happy path: insert, first lookup (cache miss), second lookup (cache hit), evict, third lookup (cache miss again).
- **Why**: Proves wiring end-to-end AND demonstrates the canonical "document store of record + cache-aside" pattern that justifies bundling the two. Still small enough that a team replaces it in an hour.
- **Alternatives considered**: Separate Mongo-only and Redis-only samples (loses the "why bundle them" story), full CRUD + REST controller (scope creep), no service layer (then the cache-aside pattern lives in the test — weird).

### D13. Delta spec uses ADDED Requirements only; no MODIFIED blocks
- **Choice**: The delta spec at `openspec/changes/add-kotlin-microservice-nosql-cache-profile/specs/kotlin-microservice-template/spec.md` uses `## ADDED Requirements` exclusively. The requirements added by the parent change (about `stack_profile`, `relational-db`, and the postgres service) are not changed — we're adding a third profile value alongside them.
- **Why**: Adding a new `stack_profile` value doesn't change the behavior of existing values. The parent change already MODIFIED the "Template prompts" requirement to include `stack_profile`; extending the set of valid values is purely additive at the requirement level (the requirement text enumerates the profiles, but adding a value to an enumeration is an ADDED behavior, not a modification of existing ones — a new requirement "The template SHALL offer a `nosql-cache` profile" captures it cleanly).
- **Risk**: If the parent change's spec phrasing is "the `stack_profile` prompt accepts exactly `default` and `relational-db`", then adding `nosql-cache` is a modification of that exact-match clause. → Mitigation: when writing specs, read the parent change's delta spec first and phrase this change's requirements to ADD alongside rather than contradict. If a contradiction is unavoidable, fall back to MODIFIED Requirements with the whole updated block.

## Risks / Trade-offs

- **[Risk] Parent change slips or its `stack_profile` scaffold lands in a different shape than expected.** → Mitigation: the apply phase reads the actual state of `kotlin-microservice/project.json` and related files before editing. If the scaffold is missing, bail out and escalate rather than duplicating it.
- **[Risk, resolved] Kotlin POJO codec + data class round-trip.** → Dropped the POJO codec entirely (D5). Repository uses raw `org.bson.Document`. No longer a risk.
- **[Risk] Mongock 5.4 release cadence is fast; the version we pin today could be months out of date by the time a team generates a project.** → Acceptable — that's the `version pins in project.json` bargain. A single-line bump in `project.json` updates every future render.
- **[Risk] Three-way conditional in `docker-compose.yml` compounds YAML whitespace fragility.** → Mitigation: render and validate all three profile outputs during apply (see D10).
- **[Risk] Testcontainers Mongo + Redis containers pull ~300MB on first run.** → Acceptable; matches the existing LocalStack/Postgres cost profile. Documented in the rendered README.
- **[Risk] A team using the raw Mongo driver may later want Spring Data Mongo for something (e.g. `@Transactional` across repositories) and find the template works against them.** → Acceptable. Adding `spring-boot-starter-data-mongodb` on top of the raw driver is a one-line Gradle change; the driver beans don't conflict with Spring Data's. Documented as "upgrade path" in the rendered README.
- **[Trade-off] Deliberate asymmetry between Mongo (raw) and Redis (Spring Data) will surprise some readers.** → The asymmetry is explained in both design and the rendered README. The rationale (D3) is defensible.
- **[Trade-off] No `@EnableCaching` / `@Cacheable` in v1.** → Direct `RedisTemplate` is enough to show the pattern. `@Cacheable` can be layered on later without touching the profile's dep set (`spring-boot-starter-cache` is a one-line addition).
- **[Trade-off] No Redis password in the local compose Redis.** → Matches how `postgres` in the parent change ships with weak local creds. Both are local-dev-only; deployed environments inject real secrets.

## Migration Plan

Additive from the perspective of existing users:

1. Projects already generated with `default` or `relational-db` don't change.
2. Future renders see a third value in the `stack_profile` prompt: `nosql-cache`.
3. Picking `default` or `relational-db` is byte-identical to post-parent-change output. Picking `nosql-cache` gets the Mongo + Redis + Mongock bundle.

No rollback strategy needed — this is a template, not a deployed system.

## Open Questions

- **Exact Mongock version?** Pin during apply after checking Mongock's Spring Boot 3 compatibility page. Likely `5.4.4` or newer.
- **Exact Mongo Java driver version?** **Resolved**: do NOT pin it. Spring Boot 3.3.5's BOM manages `org.mongodb:mongodb-driver-sync` via its `mongodb.version` property. Pinning to a newer version drifts from `mongodb-driver-core` (also managed by the BOM) and fails at runtime with `NoClassDefFoundError: com/mongodb/internal/TimeoutSettings`. Declare `implementation("org.mongodb:mongodb-driver-sync")` without a version.
- **Should the sample exercise transactions?** Leaning no — Mongo transactions require a replica set, which `mongo:7` as a single-node container does not provide without extra setup. Keeping the sample non-transactional avoids that tarpit.
- **Kotlin data class no-arg strategy?** **Resolved**: not applicable. Dropped the POJO codec in favor of raw `Document` + manual conversion (see D5).
- **Do we need a `spring.data.redis.timeout` default?** Spring Boot defaults to 60s which is high for a cache. Consider setting `2000ms` in `application.yml` to fail fast on Redis outages. Confirm during apply.
