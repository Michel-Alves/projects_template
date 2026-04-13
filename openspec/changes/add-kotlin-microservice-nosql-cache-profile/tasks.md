## 1. Prerequisite check

- [ ] 1.1 Confirm `add-kotlin-microservice-db-profile` has landed: `kotlin-microservice/project.json` already contains a `stack_profile` array with `default` and `relational-db`. If not, STOP and rebase.
- [ ] 1.2 Read `kotlin-microservice/project.json`, `build.gradle.kts`, `application.yml`, `application-local.yml`, and `local/docker/docker-compose.yml` to confirm the exact shape of the existing `stack_profile` conditionals; this change must follow the same templating style.

## 2. project.json updates

- [ ] 2.1 Append `"nosql-cache"` to the `stack_profile` array in `kotlin-microservice/project.json` (order: `default`, `relational-db`, `nosql-cache`).
- [ ] 2.2 Add version pins: `mongo_image_tag` (default `"7"`), `redis_image_tag` (default `"7-alpine"`), `mongo_driver_version` (pin to latest `5.x` Kotlin sync driver at apply time), `mongock_version` (pin to latest `5.4.x` Spring Boot 3-compatible release at apply time).
- [ ] 2.3 Document the new pins in the top-of-file comment if there is one; keep naming consistent with `postgres_image_tag`.

## 3. build.gradle.kts

- [ ] 3.1 Add a `{{ if eq stack_profile "nosql-cache" }}...{{ end }}` block inside `dependencies { ... }` declaring: `implementation("org.mongodb:mongodb-driver-kotlin-sync:{{mongo_driver_version}}")`, `implementation("org.springframework.boot:spring-boot-starter-data-redis")`, `implementation("io.mongock:mongock-springboot-v3:{{mongock_version}}")`, `implementation("io.mongock:mongodb-sync-v4-driver:{{mongock_version}}")`.
- [ ] 3.2 In the same conditional block add `testImplementation("org.testcontainers:mongodb")` (version managed by the existing Testcontainers BOM import).
- [ ] 3.3 Verify the file contains NO reference to `spring-boot-starter-data-mongodb` or `mongodb-springdata-v4-driver`.
- [ ] 3.4 Render the template with `stack_profile=default`, `relational-db`, and `nosql-cache` and run `./gradlew dependencies` against each; confirm the dep set matches the spec.

## 4. MongoConfig and MongoHealthIndicator

- [ ] 4.1 Create `kotlin-microservice/template/src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/persistence/MongoConfig.kt`, wrapped in a `{{ if eq stack_profile "nosql-cache" }}...{{ end }}` conditional at the file-content level. Contents: a `@Configuration` class bound to `@ConfigurationProperties("app.mongo")` with fields `uri: String` and `database: String`, a `@Bean(destroyMethod = "close") fun mongoClient(): MongoClient` built via `MongoClients.create(MongoClientSettings.builder().applyConnectionString(ConnectionString(uri)).codecRegistry(composedRegistry).build())` where `composedRegistry` composes the default codec registry with `CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())`, and a `@Bean fun mongoDatabase(client: MongoClient): MongoDatabase = client.getDatabase(database)`.
- [ ] 4.2 Create `kotlin-microservice/template/src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/persistence/MongoHealthIndicator.kt` (same conditional wrapper). Contents: a `@Component class MongoHealthIndicator(private val client: MongoClient) : HealthIndicator` whose `health()` runs `client.getDatabase("admin").runCommand(Document("ping", 1))` and returns `Health.up().build()` on success, `Health.down(ex).build()` on exception.

## 5. Sample document, repository, Mongock change unit, cache, service

- [ ] 5.1 Create `SampleDocument.kt` as a Kotlin `data class` with `id: String = ""` and `name: String = ""`. No annotations. Default values on every field are required so the POJO codec can instantiate.
- [ ] 5.2 Create `SampleDocumentRepository.kt` as a `@Component class SampleDocumentRepository(db: MongoDatabase)` that obtains `val collection: MongoCollection<SampleDocument> = db.getCollection("sample_documents", SampleDocument::class.java)` and exposes `findById(id: String): SampleDocument?`, `insert(doc: SampleDocument)`, and `deleteById(id: String)`.
- [ ] 5.3 Create `persistence/migration/V001__create_sample_index.kt` as `@ChangeUnit(id = "V001__create_sample_index", order = "001", author = "{{author}}")` with `@Execution fun execute(db: MongoDatabase)` and `@RollbackExecution fun rollback(db: MongoDatabase)`. `execute` creates a unique index on the `name` field of `sample_documents`.
- [ ] 5.4 Create `cache/SampleCache.kt` as `@Component class SampleCache(private val redis: StringRedisTemplate)` exposing `get(key)`, `put(key, value)`, and `evict(key)`. Use a namespace prefix like `sample:` on all keys.
- [ ] 5.5 Create `service/SampleDocumentService.kt` combining the repository and the cache in a cache-aside pattern: `getByName(name: String): SampleDocument?` first checks `SampleCache.get("by-name:$name")`, falls through to a Mongo query, writes the result back into the cache on miss, and returns the value.
- [ ] 5.6 Wrap every file created in tasks 5.1–5.5 in a file-content-level `{{ if eq stack_profile "nosql-cache" }}...{{ end }}` conditional.

## 6. application.yml and application-local.yml

- [ ] 6.1 Add a conditional `app.mongo.database: {{app_name}}` block to `application.yml` under `{{ if eq stack_profile "nosql-cache" }}...{{ end }}`.
- [ ] 6.2 Add a conditional `mongock.migration-scan-package: {{tld}}.{{author}}.{{app_name}}.persistence.migration` (plus any other required mongock keys — `transaction-enabled: false` to avoid requiring a replica set).
- [ ] 6.3 Add a conditional `spring.data.redis.timeout: 2s` block (fail-fast default, well under Spring Boot's 60-second default).
- [ ] 6.4 Add a conditional block in `application-local.yml`: `app.mongo.uri: mongodb://root:root@mongo:27017/{{app_name}}?authSource=admin`, `spring.data.redis.host: redis`, `spring.data.redis.port: 6379`.
- [ ] 6.5 Confirm neither `application.yml` nor `application-local.yml` contains a hardcoded `app.mongo.uri` in the base file (only the local override sets it).

## 7. docker-compose.yml

- [ ] 7.1 Add a conditional `mongo` service block to `kotlin-microservice/template/local/docker/docker-compose.yml` wrapped in `{{ if eq stack_profile "nosql-cache" }}...{{ end }}`: image `mongo:{{mongo_image_tag}}`, env `MONGO_INITDB_ROOT_USERNAME=root` and `MONGO_INITDB_ROOT_PASSWORD=root`, named volume for `/data/db`, healthcheck running `mongosh --quiet --eval 'db.adminCommand({ ping: 1 })'`.
- [ ] 7.2 Add a conditional `redis` service block in the same conditional: image `redis:{{redis_image_tag}}`, no persistent volume, healthcheck `redis-cli ping | grep PONG`.
- [ ] 7.3 Extend the `app` service's `depends_on` block with conditional `mongo: { condition: service_healthy }` and `redis: { condition: service_healthy }` entries wrapped in the same `{{ if eq stack_profile "nosql-cache" }}...{{ end }}` conditional.
- [ ] 7.4 Add the `mongo_data` named volume at the bottom of the compose file under a `nosql-cache` conditional.
- [ ] 7.5 Render the template for `stack_profile=default`, `relational-db`, and `nosql-cache` and run `docker compose -f local/docker/docker-compose.yml config` against each rendered output. All three MUST parse without errors or warnings. Fix any YAML whitespace issues by adjusting `{{- ... -}}` trim markers.

## 8. Integration test

- [ ] 8.1 Create `kotlin-microservice/template/src/test/kotlin/{{tld}}/{{author}}/{{app_name}}/persistence/SampleDocumentRepositoryIntegrationTest.kt`, wrapped in a file-content-level `{{ if eq stack_profile "nosql-cache" }}...{{ end }}` conditional.
- [ ] 8.2 The test class is `@SpringBootTest` with a `companion object` holding `@Container val mongo = MongoDBContainer("mongo:{{mongo_image_tag}}")` and `@Container val redis = GenericContainer(DockerImageName.parse("redis:{{redis_image_tag}}")).withExposedPorts(6379)`. Both use `@JvmStatic` and start once per class.
- [ ] 8.3 Add a `@DynamicPropertySource` method that sets `app.mongo.uri` from `mongo.replicaSetUrl` (fallback to `mongo.connectionString`), `app.mongo.database` to a test DB name, `spring.data.redis.host` to `redis.host`, and `spring.data.redis.port` to `redis.getMappedPort(6379)`.
- [ ] 8.4 Test body: autowire `SampleDocumentService`, `SampleDocumentRepository`, and `SampleCache`. Insert a `SampleDocument(id = "1", name = "alpha")`. Call `service.getByName("alpha")` → assert it returns the doc AND that `SampleCache.get("by-name:alpha")` is now populated. Call `service.getByName("alpha")` again → assert no new Mongo query happened (verify via a spy or by deleting the Mongo document first and confirming the second lookup still returns from cache). Call `cache.evict("by-name:alpha")`. Re-delete nothing; call `service.getByName("alpha")` → assert it hits Mongo again.
- [ ] 8.5 Verify the test runs green against a local Docker daemon.

## 9. Template README and docs

- [ ] 9.1 Extend `kotlin-microservice/template/README.md` with a conditional section describing the `nosql-cache` profile: how to run locally (compose), where migrations live (`persistence/migration/`), how to add a Mongock change unit, how the cache-aside flow works, and the deliberate asymmetry (raw Mongo driver vs. Spring Data Redis) with a one-paragraph rationale.
- [ ] 9.2 Add a "Kotlin data class no-arg gotcha" note explaining why every field on `SampleDocument` has a default value (POJO codec needs a no-arg constructor).
- [ ] 9.3 Update `CLAUDE.md` in this repo to add a row for `nosql-cache` in the kotlin-microservice profile table, and add `mongo_image_tag`, `redis_image_tag`, `mongo_driver_version`, and `mongock_version` to the Prompts list.
- [ ] 9.4 Update the root `README.md` (if it enumerates kotlin-microservice profiles) to mention `nosql-cache`.

## 10. End-to-end verification

- [ ] 10.1 `boilr template save ./kotlin-microservice kotlin-microservice` (re-register after edits).
- [ ] 10.2 Render to `/tmp/nosql-cache-smoke` with `stack_profile=nosql-cache`. Run `gradle wrapper --gradle-version <gradle_version>`, then `./gradlew build` — MUST succeed.
- [ ] 10.3 Run `./gradlew test` against `/tmp/nosql-cache-smoke` with Docker running — the integration test MUST pass.
- [ ] 10.4 Run `docker compose -f local/docker/docker-compose.yml up --build` against `/tmp/nosql-cache-smoke` and `curl localhost:<port>/actuator/health` — the response MUST include both `mongo` and `redis` components with status `UP`.
- [ ] 10.5 Render to `/tmp/default-smoke` and `/tmp/relational-smoke` and confirm both still build and test green (no regression in the other profiles).
- [ ] 10.6 Spot-check that `/tmp/default-smoke` and `/tmp/relational-smoke` contain no Mongo, Redis, or Mongock references anywhere in `build.gradle.kts`, `application.yml`, `application-local.yml`, or `local/docker/docker-compose.yml`.

## 11. Archive prep

- [ ] 11.1 Run `openspec validate add-kotlin-microservice-nosql-cache-profile --strict` and resolve any warnings.
- [ ] 11.2 Commit all changes with a message following the repo convention (see recent commits on `main`).
- [ ] 11.3 Ready for `/opsx:archive` once the parent change `add-kotlin-microservice-db-profile` has been archived first.
