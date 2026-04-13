# {{app_name}}

A Kotlin + Spring Boot microservice generated from the `kotlin-microservice` boilr template.

## Bundled stack

- **Spring Boot {{spring_boot_version}}** (Web + Actuator) on Java {{java_version}} / Kotlin {{kotlin_version}}
- **Observability**: Actuator probes (`/actuator/health/liveness`, `/actuator/health/readiness`), Micrometer + Prometheus (`/actuator/prometheus`), OpenTelemetry SDK + OTLP exporter
- **Logging**: Log4j2 with `JsonTemplateLayout` (ECS-shaped JSON to stdout). The default Logback starter is excluded.
- **Messaging**: AWS SDK v2 SNS publisher + SQS poller, configured via `aws.messaging.*` properties
{{- if eq stack_profile "relational-db" }}
- **Persistence**: Spring Data JPA + Hibernate, Flyway migrations, PostgreSQL {{postgres_image_tag}}; Testcontainers Postgres for integration tests
{{- end }}
{{- if eq stack_profile "nosql-cache" }}
- **Persistence**: raw MongoDB Kotlin sync driver ({{mongo_driver_version}}), Mongock ({{mongock_version}}) migrations — **not** Spring Data MongoDB
- **Cache**: Spring Data Redis (Lettuce client) with a `StringRedisTemplate`-backed cache-aside helper
- **Testing**: Testcontainers MongoDB + generic Redis container for integration tests
{{- end }}
- **Production runtime**: multi-stage `Dockerfile` at the project root (and `.dockerignore`)
- **Local dev**: `local/docker/docker-compose.yml` running the service alongside LocalStack (SNS/SQS) and an OpenTelemetry collector{{ if eq stack_profile "relational-db" }} and Postgres{{ end }}{{ if eq stack_profile "nosql-cache" }} and MongoDB + Redis{{ end }}

Stack profile: **{{stack_profile}}**.

## First-run setup

This step is only needed if you want to run `./gradlew` directly (e.g. `./gradlew bootRun` or `./gradlew test`). If you only ever run the service through `docker compose`, you can skip it — the `Dockerfile` builds inside a `gradle:{{gradle_version}}-jdk{{java_version}}` image and brings its own gradle.

The template ships only `gradle/wrapper/gradle-wrapper.properties`. To materialize `gradlew` and `gradle-wrapper.jar` for local `./gradlew` use, run once after rendering:

```bash
gradle wrapper --gradle-version {{gradle_version}}
```

After that, use `./gradlew` for everything.

## Build and run

```bash
./gradlew build           # compile + run unit tests
./gradlew bootRun         # run the service on :8080

curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/actuator/prometheus
```

## Run the full stack with docker-compose

This project separates production and local-dev assets by directory: the root-level `Dockerfile` (and `.dockerignore`) is the **production runtime image** — it's what you build and ship. Everything under `local/docker/` is for **local development only** (compose stack, LocalStack init scripts, OTel collector config). Mixing them at the root would obscure that boundary.

The `Dockerfile` builds inside a `gradle:{{gradle_version}}-jdk{{java_version}}` image, so this works on a fresh checkout — no `gradle wrapper` bootstrap required.

```bash
docker compose -f local/docker/docker-compose.yml up --build
```

This brings up:

- `app` — the service, with `SPRING_PROFILES_ACTIVE=local` and OTel pointed at the collector
- `localstack` — SNS + SQS, with an init script that creates the `{{app_name}}-events` topic and queue and subscribes the queue to the topic
- `otel-collector` — OTLP receiver that prints spans to its stdout

Verify LocalStack is set up:

```bash
docker compose -f local/docker/docker-compose.yml exec localstack awslocal sns list-topics
docker compose -f local/docker/docker-compose.yml exec localstack awslocal sqs list-queues
```

Publish a test message and watch the poller log it as JSON in `docker compose -f local/docker/docker-compose.yml logs app`.

## Configuration

All `aws.messaging.*` properties are bound from `application.yml` and overridable by environment variables (`AWS_MESSAGING_TOPIC_ARN`, `AWS_MESSAGING_QUEUE_URL`, `AWS_MESSAGING_REGION`, etc.). The `local` profile (`application-local.yml`) defaults them to LocalStack.

OpenTelemetry honors the standard env vars: `OTEL_EXPORTER_OTLP_ENDPOINT` (default `http://localhost:4317`) and `OTEL_SERVICE_NAME` (default `{{app_name}}`).
{{ if eq stack_profile "nosql-cache" }}
## Persistence and cache (nosql-cache profile)

This project was generated with `stack_profile=nosql-cache`, which bundles the **raw MongoDB Kotlin sync driver** + **Mongock** migrations + **Spring Data Redis** (Lettuce).

**Deliberate asymmetry**: Mongo is the raw driver (no Spring Data MongoDB) because Spring Data Mongo's abstractions (`MongoTemplate`, derived queries, index auto-creation) cost more than they give for non-trivial documents. Redis uses the Spring Data starter because it's a thin wrapper that gives you `/actuator/health` and auto-config almost for free. If you want Spring Data MongoDB back, it's a one-line Gradle addition — the beans don't conflict with the driver beans.

- **Mongo wiring**: `src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/persistence/MongoConfig.kt` reads `app.mongo.uri` / `app.mongo.database` (custom namespace, **not** `spring.data.mongodb.*`) and exposes `MongoClient` + `MongoDatabase` beans. The client is built with a POJO codec registry so Kotlin data classes round-trip through `MongoCollection<T>`.
- **Mongo health**: `MongoHealthIndicator` is hand-wired (there's no auto-config without Spring Data Mongo). Redis health comes for free from Spring Data Redis.
- **Sample document**: `SampleDocument` is a plain Kotlin `data class`. **Every field has a default value** — this is required so the POJO codec's no-arg constructor path works (Kotlin `data class` doesn't emit a synthetic no-arg constructor by default). If you don't want defaults on your own documents, add the `kotlin-noarg` Gradle plugin.
- **Repository**: `SampleDocumentRepository` wraps a `MongoCollection<SampleDocument>` with driver API calls — no repository interface inheritance.
- **Migrations**: Mongock `@ChangeUnit` classes live in `persistence/migration/` and receive a `MongoDatabase` parameter. `V001__create_sample_index` is the seed migration. Transactions are disabled (`mongock.transaction-enabled: false`) because single-node Mongo doesn't support them.
- **Cache-aside pattern**: `SampleDocumentService.getByName` checks `SampleCache` first, falls through to Mongo on miss, and writes the id back into Redis. `SampleCache` wraps `StringRedisTemplate` with a `sample:` key prefix and a 5-minute TTL.
- **Local dev** uses the `mongo` and `redis` services in `local/docker/docker-compose.yml`. The `local` Spring profile points `app.mongo.uri` at `mongodb://root:root@mongo:27017/{{app_name}}?authSource=admin` and `spring.data.redis.host` at `redis`. Override in deployed envs via `APP_MONGO_URI`, `APP_MONGO_DATABASE`, `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, `SPRING_DATA_REDIS_PASSWORD`.
- **Integration test**: `SampleDocumentRepositoryIntegrationTest` boots `MongoDBContainer` + `GenericContainer("redis:{{redis_image_tag}}")`, wires them via `@DynamicPropertySource`, and exercises the full cache-aside flow. Requires Docker.
- **Redis timeout** is set to `2s` in `application.yml` to fail fast on outages (Spring Boot's default is 60s).

{{ end }}
{{ if eq stack_profile "relational-db" }}
## Persistence (relational-db profile)

This project was generated with `stack_profile=relational-db`, which bundles Spring Data JPA + Hibernate, Flyway migrations, and the PostgreSQL JDBC driver on the main classpath, plus Testcontainers Postgres on the test classpath.

- **Sample entity**: `src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/persistence/SampleEntity.kt` — an intentionally minimal `@Entity` (autogenerated `Long` id + `name` field). Replace it with your real domain.
- **Repository**: `src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/persistence/SampleRepository.kt` — `JpaRepository<SampleEntity, Long>`.
- **Migrations**: `src/main/resources/db/migration/V1__init.sql` creates the `sample_entity` table. Add `V2__*.sql`, `V3__*.sql`, etc. as your schema evolves.
- **Hibernate `ddl-auto`** is set to `validate` in `application.yml`, so schema/entity drift fails at startup. `open-in-view` is disabled.
- **Local dev** uses the `postgres` service in `local/docker/docker-compose.yml` (image `postgres:{{postgres_image_tag}}`). The `local` Spring profile (`application-local.yml`) points `spring.datasource.url` at `jdbc:postgresql://postgres:5432/{{app_name}}` with user/password `{{app_name}}`/`{{app_name}}`. Override any of these via the standard `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` env vars in deployed environments.
- **Integration test**: `SampleRepositoryIntegrationTest` uses Testcontainers' `PostgreSQLContainer` and `@DynamicPropertySource` to boot a real Postgres 16 per test class. Requires Docker.

{{ end }}
## Tests

```bash
./gradlew test
```

`SqsPollerIntegrationTest` uses Testcontainers' `LocalStackContainer` to verify a publish→consume round-trip end to end. It requires a running Docker daemon.

## Trade-offs to know

- **No `@SqsListener`** — the `SqsPoller` is a hand-rolled `@Scheduled` long-poll loop. Swap it for `spring-cloud-aws-starter-sqs` if you want batch handling, automatic visibility extension, or `@SqsListener` ergonomics.
{{- if eq stack_profile "default" }}
- **No database layer** — this profile is HTTP + messaging only. If you need persistence, regenerate with `stack_profile=relational-db` (Spring Data JPA + Postgres + Flyway) or `stack_profile=nosql-cache` (raw Mongo driver + Redis + Mongock).
{{- end }}
{{- if eq stack_profile "nosql-cache" }}
- **No Spring Data MongoDB** — the template uses the raw `mongodb-driver-kotlin-sync` on purpose (see "Persistence and cache" above). If you prefer repositories and `@Document`, add `spring-boot-starter-data-mongodb` to `build.gradle.kts`; it coexists with the driver beans.
- **No `@EnableCaching` / `@Cacheable`** — the template exposes a `SampleCache` helper over `StringRedisTemplate` directly. Add `spring-boot-starter-cache` + `@EnableCaching` if you want the declarative abstraction.
- **Single-node Mongo, no transactions** — `mongock.transaction-enabled` is `false`. Mongo transactions require a replica set, which the local compose image does not run.
{{- end }}
- **OTel collector exports to stdout only** — wire a Jaeger or Tempo backend in `otel-collector/config.yaml` if you want a UI.
