## ADDED Requirements

### Requirement: nosql-cache profile bundles raw Mongo driver, Spring Data Redis, and Mongock
When the user selects `stack_profile=nosql-cache`, the generated project SHALL include the MongoDB Kotlin sync driver (`org.mongodb:mongodb-driver-kotlin-sync`), Spring Data Redis (`spring-boot-starter-data-redis`, Lettuce client pulled transitively), Mongock for MongoDB migrations (`io.mongock:mongock-springboot-v3` PLUS `io.mongock:mongodb-sync-v4-driver` — the raw-driver module, NOT `mongodb-springdata-v4-driver`), and the Testcontainers MongoDB module (`org.testcontainers:mongodb`) on the test classpath. The generated project SHALL NOT include `spring-boot-starter-data-mongodb` — Mongo access is through the raw driver only. When the user selects `stack_profile=default` or `stack_profile=relational-db`, NONE of these dependencies SHALL appear on the build classpath.

#### Scenario: nosql-cache classpath includes Mongo driver, Spring Data Redis, and Mongock raw-driver module
- **WHEN** the template is generated with `stack_profile=nosql-cache`
- **THEN** the rendered `build.gradle.kts` declares dependencies on `org.mongodb:mongodb-driver-kotlin-sync`, `org.springframework.boot:spring-boot-starter-data-redis`, `io.mongock:mongock-springboot-v3`, and `io.mongock:mongodb-sync-v4-driver`
- **AND** the rendered `build.gradle.kts` declares `org.testcontainers:mongodb` on `testImplementation`
- **AND** the rendered `build.gradle.kts` does NOT contain the string `spring-boot-starter-data-mongodb`
- **AND** the rendered `build.gradle.kts` does NOT contain the string `mongodb-springdata-v4-driver`

#### Scenario: default and relational-db profiles do not include any nosql-cache dependencies
- **WHEN** the template is generated with `stack_profile=default` or `stack_profile=relational-db`
- **THEN** the rendered `build.gradle.kts` does NOT contain the strings `mongodb-driver-kotlin-sync`, `spring-boot-starter-data-redis`, `mongock-springboot-v3`, `mongodb-sync-v4-driver`, or `testcontainers:mongodb`

### Requirement: nosql-cache profile wires MongoClient, MongoDatabase, and a Mongo health indicator manually
Because `stack_profile=nosql-cache` deliberately excludes `spring-boot-starter-data-mongodb`, Spring Boot's Mongo auto-configuration is NOT available. The generated project SHALL therefore ship a `MongoConfig` `@Configuration` class that:
1. Reads `app.mongo.uri` and `app.mongo.database` from Spring configuration (custom namespace — NOT `spring.data.mongodb.*`).
2. Exposes a `MongoClient` bean built with the default codec registry plus a `PojoCodecProvider.builder().automatic(true).build()` registry so plain Kotlin data classes round-trip through `MongoCollection<T>`. The bean SHALL be registered with `destroyMethod = "close"` so it releases connections on shutdown.
3. Exposes a `MongoDatabase` bean obtained from `mongoClient.getDatabase(app.mongo.database)`.

The generated project SHALL ALSO ship a `MongoHealthIndicator` `@Component` that implements Spring Boot Actuator's `HealthIndicator` interface. It SHALL report `UP` when `mongoClient.getDatabase("admin").runCommand(Document("ping", 1))` succeeds, and `DOWN` (with the exception as the health detail) when it throws. The indicator SHALL register automatically with Actuator's `/actuator/health` endpoint. The Redis health indicator comes transitively from `spring-boot-starter-data-redis` and requires no additional code.

#### Scenario: MongoConfig exposes MongoClient and MongoDatabase beans under nosql-cache
- **WHEN** the template is generated with `stack_profile=nosql-cache`
- **THEN** the file `src/main/kotlin/<tld>/<author>/<app_name>/persistence/MongoConfig.kt` exists, is annotated with `@Configuration`, reads properties under the prefix `app.mongo`, declares a `@Bean(destroyMethod = "close") fun mongoClient(): MongoClient`, and declares a `@Bean fun mongoDatabase(client: MongoClient): MongoDatabase`
- **AND** the `MongoClient` bean is built with a `CodecRegistry` that composes the default codec registry with `CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())`

#### Scenario: MongoHealthIndicator reports DOWN when Mongo is unreachable
- **WHEN** the template is generated with `stack_profile=nosql-cache` AND the rendered project is started against a Mongo URI that points at a port with no Mongo listening
- **THEN** `/actuator/health` returns overall status `DOWN`, and the `mongo` component reports `DOWN` with the underlying exception in its details

#### Scenario: Redis health indicator is present without extra code under nosql-cache
- **WHEN** the template is generated with `stack_profile=nosql-cache` AND the rendered project is started against a reachable Redis
- **THEN** `/actuator/health` reports a `redis` component with status `UP`
- **AND** no file under `src/main/kotlin/<tld>/<author>/<app_name>/` defines a `RedisHealthIndicator` class (the indicator comes from `spring-boot-starter-data-redis`)

### Requirement: nosql-cache profile ships a sample document, repository, Mongock change unit, cache helper, and integration test
When `stack_profile=nosql-cache`, the generated project SHALL include:
- A plain Kotlin `data class SampleDocument` (NOT annotated with `@Document`; no Spring Data Mongo annotations anywhere), with each field given a default value so the Mongo POJO codec can construct instances via the no-arg constructor that Kotlin's default-value compilation produces.
- A `SampleDocumentRepository` class that wraps a `MongoCollection<SampleDocument>` obtained from the injected `MongoDatabase`, exposing at minimum `findById(id: String): SampleDocument?`, `insert(doc: SampleDocument)`, and `deleteById(id: String)`.
- A Mongock `@ChangeUnit` class at `src/main/kotlin/<tld>/<author>/<app_name>/persistence/migration/V001__create_sample_index.kt` that takes a `MongoDatabase` parameter and creates a unique index on the `name` field of the sample documents collection.
- A `SampleCache` class that wraps a `StringRedisTemplate` (injected by Spring Data Redis) with at minimum `get(key: String): String?`, `put(key: String, value: String)`, and `evict(key: String)`.
- A `SampleDocumentService` (or equivalent) that combines the repository and the cache in a canonical cache-aside pattern: reads check the cache first, fall through to Mongo on miss, and write the resolved value back into the cache.
- A Testcontainers-backed `@SpringBootTest` integration test that boots both a `MongoDBContainer` AND a `GenericContainer("redis:<redis_image_tag>").withExposedPorts(6379)`, wires their connection details into the Spring context via `@DynamicPropertySource` (setting `app.mongo.uri`, `app.mongo.database`, `spring.data.redis.host`, `spring.data.redis.port`), and exercises the cache-aside service end-to-end: insert a document, first lookup (cache miss, hits Mongo), second lookup (cache hit), evict, third lookup (cache miss again).

NONE of these files SHALL be rendered when `stack_profile=default` or `stack_profile=relational-db`.

#### Scenario: Sample document, repository, Mongock change unit, cache helper, and service exist for nosql-cache
- **WHEN** the template is generated with `stack_profile=nosql-cache`
- **THEN** the file `src/main/kotlin/<tld>/<author>/<app_name>/persistence/SampleDocument.kt` exists, declares `package <tld>.<author>.<app_name>.persistence`, is a Kotlin `data class`, and every field has a default value
- **AND** the file `src/main/kotlin/<tld>/<author>/<app_name>/persistence/SampleDocument.kt` contains no Spring Data Mongo annotations (neither `@Document` nor `@Id` from `org.springframework.data.mongodb.core.mapping`)
- **AND** the file `src/main/kotlin/<tld>/<author>/<app_name>/persistence/SampleDocumentRepository.kt` exists and declares a class that takes a `MongoDatabase` and operates on a `MongoCollection<SampleDocument>` obtained via `db.getCollection("sample_documents", SampleDocument::class.java)`
- **AND** the file `src/main/kotlin/<tld>/<author>/<app_name>/persistence/migration/V001__create_sample_index.kt` exists, is annotated `@ChangeUnit(id = "V001__create_sample_index", order = "001", author = "<author>")`, and creates a unique index on the `name` field
- **AND** the file `src/main/kotlin/<tld>/<author>/<app_name>/cache/SampleCache.kt` exists and uses `StringRedisTemplate`

#### Scenario: Persistence and cache files do NOT render under default or relational-db
- **WHEN** the template is generated with `stack_profile=default` or `stack_profile=relational-db`
- **THEN** none of the files `MongoConfig.kt`, `MongoHealthIndicator.kt`, `SampleDocument.kt`, `SampleDocumentRepository.kt`, `V001__create_sample_index.kt`, or `SampleCache.kt` are rendered as non-empty files inside `src/main/kotlin/<tld>/<author>/<app_name>/`
- **AND** the parent directories MAY exist as empty directories as an accepted side-effect of the content-level conditional rendering strategy (same trade-off already accepted for the `relational-db` profile's persistence directory)

#### Scenario: Integration test exercises real Mongo and Redis via Testcontainers with a cache-aside flow
- **WHEN** a user runs `./gradlew test` against a freshly generated `nosql-cache` project with a running Docker daemon
- **THEN** `SampleDocumentRepositoryIntegrationTest` boots a `MongoDBContainer` AND a `GenericContainer("redis:<redis_image_tag>")`, wires their addresses into the Spring context via `@DynamicPropertySource`
- **AND** Mongock runs `V001__create_sample_index` against the Mongo container before the test body executes
- **AND** the test inserts a `SampleDocument`, asserts that the first lookup resolves from Mongo and populates the cache, asserts that the second lookup resolves from the cache without hitting Mongo, evicts the cache key, and asserts that the third lookup resolves from Mongo again

### Requirement: nosql-cache profile configures Mongo under app.mongo.* and Redis under spring.data.redis.*
When `stack_profile=nosql-cache`:
- The base `application.yml` SHALL contain an `app.mongo.database: <app_name>` value AND a `mongock.migration-scan-package` value pointing at `<tld>.<author>.<app_name>.persistence.migration`. It SHALL NOT contain a hardcoded `app.mongo.uri`.
- The base `application.yml` SHALL contain a `spring.data.redis.*` block with at minimum a `timeout` set to a value well under Spring Boot's 60-second default (to fail fast on Redis outages) and SHALL NOT contain hardcoded `spring.data.redis.host` or `spring.data.redis.port` values.
- The rendered `application-local.yml` SHALL supply `app.mongo.uri: mongodb://root:root@mongo:27017/<app_name>?authSource=admin` and `spring.data.redis.host: redis` and `spring.data.redis.port: 6379`, pointing at the compose service names.
- Connection details SHALL be overridable at runtime via the standard Spring environment variables `APP_MONGO_URI`, `APP_MONGO_DATABASE`, `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, and `SPRING_DATA_REDIS_PASSWORD`.

#### Scenario: Base application.yml declares app.mongo.database and mongock scan package but not the Mongo URI
- **WHEN** the template is generated with `stack_profile=nosql-cache`
- **THEN** the rendered `application.yml` contains `app.mongo.database: <app_name>` and a `mongock.migration-scan-package` value pointing at the persistence migration package
- **AND** the rendered `application.yml` does NOT contain `app.mongo.uri` as a hardcoded value

#### Scenario: Local profile points Mongo and Redis at compose service names
- **WHEN** a user runs the nosql-cache service with `SPRING_PROFILES_ACTIVE=local` and no other overrides against the bundled compose stack
- **THEN** Spring resolves `app.mongo.uri` to `mongodb://root:root@mongo:27017/<app_name>?authSource=admin` (reading it from `application-local.yml`) and resolves `spring.data.redis.host` to `redis` with port `6379`

#### Scenario: Redis timeout is configured to fail fast
- **WHEN** the template is generated with `stack_profile=nosql-cache`
- **THEN** the rendered `application.yml` contains a `spring.data.redis.timeout` value less than or equal to `5s` (well below Spring Boot's 60-second default)

## MODIFIED Requirements

### Requirement: Template prompts for the common project keys
The `project.json` SHALL define the prompts `tld`, `author`, `app_name`, `version`, `java_version`, and `stack_profile` using lowercase snake_case keys, matching the convention used by the sibling `kotlin/` and `clojure/` templates. Each prompt MUST have a sensible scalar default. The `stack_profile` prompt SHALL be defined as a JSON array of allowed values, with `default` listed first (so it is selected when the user accepts the default), `relational-db` listed second, and `nosql-cache` listed third. The three `stack_profile` values are mutually exclusive — the template does NOT support selecting more than one persistence backend in a single generated project. Future profiles MUST be added to this same array rather than introduced as new prompts.

#### Scenario: Prompts are exposed to the user
- **WHEN** a user runs `boilr template use kotlin-microservice /tmp/svc`
- **THEN** boilr prompts for `tld`, `author`, `app_name`, `version`, `java_version`, and `stack_profile` in some order, each showing its default
- **AND** the `stack_profile` prompt presents `default`, `relational-db`, and `nosql-cache` as the only choices, with `default` as the default selection

#### Scenario: stack_profile prompt is an array with default first
- **WHEN** a reader inspects `kotlin-microservice/project.json`
- **THEN** the `stack_profile` key is an array whose first element is `"default"`, second element is `"relational-db"`, and third element is `"nosql-cache"`
- **AND** no other prompt in `project.json` shadows or overrides `stack_profile`

#### Scenario: Default profile produces output equivalent to the pre-profile template
- **WHEN** the template is generated with `stack_profile=default` and all other defaults accepted
- **THEN** the rendered project's Kotlin source tree, `build.gradle.kts`, `application.yml`, `application-local.yml`, `Dockerfile`, and `local/docker/docker-compose.yml` contain no JPA, Hibernate, Flyway, Postgres driver, Testcontainers Postgres, `postgres` compose service, Mongo driver, Spring Data Redis, Mongock, Testcontainers Mongo, `mongo` compose service, or `redis` compose service references
- **AND** `./gradlew dependencies` reports no `spring-boot-starter-data-jpa`, `flyway-core`, `flyway-database-postgresql`, `org.postgresql:postgresql`, `org.testcontainers:postgresql`, `mongodb-driver-kotlin-sync`, `spring-boot-starter-data-redis`, `mongock-springboot-v3`, `mongodb-sync-v4-driver`, or `testcontainers:mongodb` on any configuration
- **AND** documentation files such as the rendered `README.md` MAY mention `relational-db` or `nosql-cache` as a discoverability hint (e.g. in a "Trade-offs" section pointing users at the alternative profiles) — documentation is explicitly exempt from this byte-equivalence requirement

### Requirement: docker-compose stack brings up service, LocalStack, and OTel collector
The generated project SHALL include a `docker-compose.yml` at `local/docker/docker-compose.yml` (not at the project root) that defines at minimum three services: the application (built from the root-level `Dockerfile` via a `build.context` of `../..` so the Dockerfile and the source tree are part of the build context), `localstack` configured with `SERVICES=sns,sqs`, and an OpenTelemetry collector. The compose file's volume mounts for the LocalStack init scripts and the OTel collector config SHALL resolve to `local/docker/localstack/init/` and `local/docker/otel-collector/config.yaml` respectively — i.e. the `localstack/` and `otel-collector/` directories MUST live alongside the compose file, not at the project root. LocalStack MUST run an init script on startup that creates the configured SNS topic and SQS queue and subscribes the queue to the topic. The application service MUST set `SPRING_PROFILES_ACTIVE=local` and point its OTel exporter at the collector. The root-level `Dockerfile` and `.dockerignore` MUST remain at the project root because they are production artifacts, not local-dev assets.

When `stack_profile=relational-db`, the compose file SHALL additionally define a `postgres` service (image `postgres:<postgres_image_tag>`, named volume for data persistence, env-var-supplied `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` defaulting to `<app_name>` for all three, healthcheck via `pg_isready`), and the `app` service's `depends_on` block SHALL gain `postgres: { condition: service_healthy }`.

When `stack_profile=nosql-cache`, the compose file SHALL additionally define a `mongo` service (image `mongo:<mongo_image_tag>`, named volume for `/data/db`, env-var-supplied `MONGO_INITDB_ROOT_USERNAME` and `MONGO_INITDB_ROOT_PASSWORD` both defaulting to `root` for local dev only, healthcheck via `mongosh --eval 'db.adminCommand({ ping: 1 })'`) AND a `redis` service (image `redis:<redis_image_tag>`, no persistent volume because cache state is ephemeral by design, no password in local dev, healthcheck via `redis-cli ping`), and the `app` service's `depends_on` block SHALL gain `mongo: { condition: service_healthy }` AND `redis: { condition: service_healthy }`.

When `stack_profile=default`, the compose file SHALL contain none of the `postgres`, `mongo`, or `redis` service blocks and none of the corresponding `depends_on` entries. Profile selections are mutually exclusive — the compose file SHALL contain at most one of the persistence-backend blocks in any given render.

#### Scenario: Compose stack starts cleanly from the new path
- **WHEN** a user runs `docker compose -f local/docker/docker-compose.yml up --build` from the project root of a freshly generated project
- **THEN** all services for the selected `stack_profile` reach a healthy state and the application logs show a successful Spring Boot startup banner

#### Scenario: SNS topic and SQS queue exist after startup
- **WHEN** the compose stack is running
- **THEN** the init script has created the configured SNS topic and SQS queue inside LocalStack and the queue is subscribed to the topic, verifiable via `awslocal sns list-topics` and `awslocal sqs list-queues`

#### Scenario: Compose file references the root Dockerfile via relative build context
- **WHEN** a reader inspects `local/docker/docker-compose.yml`
- **THEN** the `app` service's `build:` block declares `context: ../..` and `dockerfile: Dockerfile`, so `docker compose build` resolves the production `Dockerfile` and the `src/` tree from the project root

#### Scenario: Local-dev assets are colocated under local/docker/
- **WHEN** a reader runs `tree local/docker` in a freshly generated project
- **THEN** the output contains `docker-compose.yml`, `localstack/init/init-aws.sh`, and `otel-collector/config.yaml` and no other files outside the `local/docker/` subtree are required for `docker compose up`

#### Scenario: Production Dockerfile stays at the project root
- **WHEN** a reader lists the project root of a freshly generated project
- **THEN** `Dockerfile` and `.dockerignore` are present at the root and are NOT duplicated under `local/docker/`

#### Scenario: Postgres service is added under relational-db
- **WHEN** the template is generated with `stack_profile=relational-db`
- **THEN** the rendered `local/docker/docker-compose.yml` contains a `postgres` service with image `postgres:<postgres_image_tag>`, a `pg_isready`-based healthcheck, and a named volume for data persistence
- **AND** the `app` service's `depends_on` block contains `postgres: { condition: service_healthy }`
- **AND** the rendered compose file contains no `mongo` or `redis` service block

#### Scenario: Mongo and Redis services are added under nosql-cache
- **WHEN** the template is generated with `stack_profile=nosql-cache`
- **THEN** the rendered `local/docker/docker-compose.yml` contains a `mongo` service with image `mongo:<mongo_image_tag>`, a `mongosh`-based ping healthcheck, and a named volume for `/data/db`
- **AND** the rendered `local/docker/docker-compose.yml` contains a `redis` service with image `redis:<redis_image_tag>` and a `redis-cli ping` healthcheck and NO persistent volume
- **AND** the `app` service's `depends_on` block contains both `mongo: { condition: service_healthy }` and `redis: { condition: service_healthy }`
- **AND** the rendered compose file contains no `postgres` service block

#### Scenario: Persistence services are absent under default
- **WHEN** the template is generated with `stack_profile=default`
- **THEN** the rendered `local/docker/docker-compose.yml` contains no `postgres`, `mongo`, or `redis` service block and the `app` service's `depends_on` block does not mention any of them
- **AND** `docker compose -f local/docker/docker-compose.yml config` parses cleanly with no warnings

#### Scenario: Postgres healthcheck gates app startup under relational-db
- **WHEN** a user runs `docker compose -f local/docker/docker-compose.yml up` against a `relational-db`-rendered project
- **THEN** the `app` service starts only after the `postgres` service reports `(healthy)`, and the application's startup logs show Flyway running `V1__init.sql` against the container before Hibernate validates the schema

#### Scenario: Mongo and Redis healthchecks gate app startup under nosql-cache
- **WHEN** a user runs `docker compose -f local/docker/docker-compose.yml up` against a `nosql-cache`-rendered project
- **THEN** the `app` service starts only after both the `mongo` and `redis` services report `(healthy)`, and the application's startup logs show Mongock running `V001__create_sample_index` against the Mongo container before the Spring Boot startup banner
