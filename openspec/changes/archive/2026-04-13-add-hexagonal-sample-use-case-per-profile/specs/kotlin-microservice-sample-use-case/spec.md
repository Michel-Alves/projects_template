## ADDED Requirements

### Requirement: Template ships a generic Sample domain model

The generated project SHALL include a minimal, framework-free domain model for a generic `Sample` aggregate, placed under `domain.sample`. The model MUST consist of:

- A `Sample` data class with fields `id: SampleId` and `name: String`, enforcing the invariant that `name` is non-blank (the constructor or a factory MUST reject blank names).
- A `SampleId` value class (Kotlin `@JvmInline value class`) wrapping a `String`.
- A `SampleRepository` interface with exactly two functions: `fun save(sample: Sample): Sample` and `fun findById(id: SampleId): Sample?`.

None of these files MAY import from `org.springframework..`, `jakarta.persistence..`, `com.mongodb..`, `org.springframework.data..`, or any other adapter-layer framework. The same domain files MUST be rendered identically regardless of `stack_profile`.

#### Scenario: Domain files exist and are framework-free

- **WHEN** the template is generated with any `stack_profile`
- **THEN** the files `Sample.kt`, `SampleId.kt`, and `SampleRepository.kt` exist under `src/main/kotlin/<tld>/<author>/<app_name>/domain/sample/`
- **AND** none of those files contain the substrings `org.springframework`, `jakarta.persistence`, `com.mongodb`, or `org.springframework.data`

#### Scenario: Blank name is rejected at construction

- **WHEN** application code attempts to construct `Sample(id = SampleId("x"), name = "")` or `Sample(id = SampleId("x"), name = "   ")`
- **THEN** construction throws `IllegalArgumentException`

### Requirement: Application layer exposes a SampleService use case

The generated project SHALL include a `SampleService` class under `application.sample` that depends only on the domain `SampleRepository` port and exposes two operations: `register(name: String): Sample` (creates a `Sample` with a freshly generated `SampleId` and persists it via the port) and `findById(id: SampleId): Sample?`. The class MAY be annotated with `@Service` but MUST NOT import from `infrastructure..`.

#### Scenario: SampleService is annotated and wired via the port

- **WHEN** the application context starts
- **THEN** a `SampleService` bean is registered and its single constructor-injected dependency is a `SampleRepository`-typed bean
- **AND** `SampleService` does not import any class from `infrastructure..`

#### Scenario: Register-then-find round-trip succeeds

- **WHEN** a test calls `sampleService.register("foo")` and then `sampleService.findById(returnedSample.id)`
- **THEN** the returned `Sample` has the same `id` and `name = "foo"` as the registered one

### Requirement: Inbound REST adapter exposes the sample use case

The generated project SHALL include a REST controller at `infrastructure.adapters.in.web.SampleController` that exposes two endpoints backed by `SampleService`:

- `POST /samples` — accepts a JSON body `{"name": "..."}`, returns HTTP 201 with `{"id": "...", "name": "..."}`.
- `GET /samples/{id}` — returns HTTP 200 with the sample if found, or HTTP 404 if not.

Request and response DTOs (`SampleRequest`, `SampleResponse`) and any domain↔DTO mapping MUST live under `infrastructure.adapters.in.web`. The controller MUST NOT return or accept `domain.sample.Sample` directly across the HTTP boundary; mapping happens inside the adapter.

#### Scenario: Create and fetch over HTTP

- **WHEN** a client sends `POST /samples` with body `{"name":"alpha"}`, then `GET /samples/{id}` with the returned id
- **THEN** the `POST` responds 201 with a body containing `"name":"alpha"` and a non-empty `id`
- **AND** the subsequent `GET` responds 200 with a body matching the same `id` and `"name":"alpha"`

#### Scenario: Missing sample returns 404

- **WHEN** a client sends `GET /samples/does-not-exist`
- **THEN** the response status is 404

#### Scenario: DTOs live in the web adapter package

- **WHEN** a reader inspects `infrastructure/adapters/in/web/`
- **THEN** `SampleRequest.kt` and `SampleResponse.kt` exist in that package
- **AND** `SampleController.kt` uses those DTOs as its request/response types, not `domain.sample.Sample`

### Requirement: Outbound persistence adapter is selected by stack_profile

The generated project SHALL render exactly one outbound persistence adapter implementing `domain.sample.SampleRepository`, determined by the `stack_profile` prompt. All adapters MUST live under `infrastructure.adapters.out.persistence` and MUST NOT be bypassed by the application layer.

- `stack_profile=default` → `InMemorySampleRepositoryAdapter` backed by a `ConcurrentHashMap<SampleId, Sample>`.
- `stack_profile=relational-db` → `JpaSampleRepositoryAdapter` that delegates to an internal Spring Data `SampleJpaEntity` + `SampleJpaRepository` pair, converting between the JPA entity and the domain `Sample` at the adapter boundary. The domain `Sample` MUST NOT carry JPA annotations. A Flyway migration `V1__init.sql` SHALL create the backing `sample` table.
- `stack_profile=nosql-cache` → `MongoSampleRepositoryAdapter` that wraps a raw `MongoCollection`-based read/write path AND a `StringRedisTemplate`-backed cache-aside layer. The cache-aside logic (read-through on `findById`, write-through on `save`) SHALL live inside the adapter, not in `application`. A Mongock `ChangeUnit` SHALL create any required indexes.

Exactly one of the three adapter source files SHALL be rendered for any given generation.

#### Scenario: Default profile renders only the in-memory adapter

- **WHEN** the template is generated with `stack_profile=default`
- **THEN** `InMemorySampleRepositoryAdapter.kt` exists under `infrastructure/adapters/out/persistence/`
- **AND** no file named `JpaSampleRepositoryAdapter.kt` or `MongoSampleRepositoryAdapter.kt` is rendered
- **AND** the rendered `build.gradle.kts` contains no JPA, Flyway, PostgreSQL, Mongo, Mongock, or Spring Data Redis dependencies

#### Scenario: Relational-db profile renders only the JPA adapter

- **WHEN** the template is generated with `stack_profile=relational-db`
- **THEN** `JpaSampleRepositoryAdapter.kt`, `SampleJpaEntity.kt`, and `SampleJpaRepository.kt` exist under `infrastructure/adapters/out/persistence/jpa/`
- **AND** `SampleJpaEntity` is annotated `@Entity` and the domain `Sample` is not
- **AND** `src/main/resources/db/migration/V1__init.sql` exists and contains a `CREATE TABLE sample` statement
- **AND** no `InMemorySampleRepositoryAdapter.kt` or `MongoSampleRepositoryAdapter.kt` file is rendered

#### Scenario: Nosql-cache profile renders only the Mongo+Redis adapter

- **WHEN** the template is generated with `stack_profile=nosql-cache`
- **THEN** `MongoSampleRepositoryAdapter.kt` exists under `infrastructure/adapters/out/persistence/mongo/`
- **AND** the adapter constructor takes both a `MongoCollection<...>` (or a thin wrapper) and a `StringRedisTemplate`-backed cache type
- **AND** a Mongock `ChangeUnit` class exists that creates indexes on the backing collection
- **AND** no `InMemorySampleRepositoryAdapter.kt` or `JpaSampleRepositoryAdapter.kt` file is rendered

#### Scenario: Cache-aside is invisible to the application layer

- **WHEN** a reader inspects `application/sample/SampleService.kt` in a `nosql-cache`-generated project
- **THEN** the file contains no import or type reference to `StringRedisTemplate`, `RedisTemplate`, `SampleCache`, or any other cache type
- **AND** `SampleService` depends only on `SampleRepository` (the domain port)

### Requirement: Testcontainers integration test exercises the profile-specific adapter

When `stack_profile` is `relational-db` or `nosql-cache`, the generated project SHALL include a Testcontainers-backed integration test that boots the full Spring context and exercises the sample use case end-to-end through the REST controller. The test MUST verify a round-trip (register a sample, fetch it back, assert equality on `name`) using a real container for the profile's backing store(s). The `default` profile SHALL include an equivalent integration test that uses the in-memory adapter and does not require Docker.

#### Scenario: relational-db integration test uses a PostgreSQL container

- **WHEN** a user runs `./gradlew test` against a freshly generated `relational-db` project with Docker available
- **THEN** an integration test starts a `PostgreSQLContainer`, wires its connection details via `@DynamicPropertySource`, executes `POST /samples` and `GET /samples/{id}` against the running Spring context, and asserts the round-trip succeeds
- **AND** Flyway runs `V1__init.sql` against the container before the test executes

#### Scenario: nosql-cache integration test uses Mongo and Redis containers

- **WHEN** a user runs `./gradlew test` against a freshly generated `nosql-cache` project with Docker available
- **THEN** an integration test starts both a `MongoDBContainer` and a Redis container, wires their connection details into the Spring context, executes `POST /samples` and `GET /samples/{id}`, and asserts the round-trip succeeds
- **AND** the test observes at least one Redis cache population as a side effect of the write-through path

#### Scenario: default profile integration test needs no containers

- **WHEN** a user runs `./gradlew test` against a freshly generated `default` project with no Docker daemon running
- **THEN** the sample integration test passes using the in-memory adapter
