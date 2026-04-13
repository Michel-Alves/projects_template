## 1. Project metadata and dependencies

- [x] 1.1 Add `archunit_version` scalar prompt with a sensible semver default to `kotlin-microservice/project.json`
- [x] 1.2 Add `com.tngtech.archunit:archunit-junit5:{{archunit_version}}` to `testImplementation` in `kotlin-microservice/template/build.gradle.kts`
- [x] 1.3 Verify no ArchUnit artifact leaks onto a runtime configuration (inspect rendered `./gradlew dependencies`)

## 2. Commons layer

- [x] 2.1 Create `kotlin-microservice/template/src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/commons/` package
- [x] 2.2 Add a single minimal utility file to `commons/` (e.g. a `Clock` typealias or a result helper) so the package is not empty, with zero project-internal imports
- [x] 2.3 Confirm `commons/` has no imports from `domain`, `application`, `infrastructure`, or `main`

## 3. Domain layer (profile-agnostic)

- [x] 3.1 Create `domain/sample/SampleId.kt` as a `@JvmInline value class` wrapping `String`
- [x] 3.2 Create `domain/sample/Sample.kt` data class with `id: SampleId` and `name: String`, rejecting blank `name` in `init`
- [x] 3.3 Create `domain/sample/SampleRepository.kt` interface with `save(sample: Sample): Sample` and `findById(id: SampleId): Sample?`
- [x] 3.4 Verify none of the three files import `org.springframework`, `jakarta.persistence`, `com.mongodb`, or `org.springframework.data`

## 4. Application layer (profile-agnostic)

- [x] 4.1 Create `application/sample/SampleService.kt` annotated `@Service`, constructor-injecting `SampleRepository`
- [x] 4.2 Implement `register(name: String): Sample` (generates a `SampleId`, constructs `Sample`, saves via port)
- [x] 4.3 Implement `findById(id: SampleId): Sample?` delegating to the port
- [x] 4.4 Confirm `SampleService.kt` does not import any `infrastructure` class

## 5. Inbound REST adapter (profile-agnostic)

- [x] 5.1 Create `infrastructure/adapters/in/web/SampleRequest.kt` and `SampleResponse.kt` DTOs
- [x] 5.2 Create `infrastructure/adapters/in/web/SampleWebMapper.kt` (or inline extension functions) translating between DTOs and `domain.sample.Sample`
- [x] 5.3 Create `infrastructure/adapters/in/web/SampleController.kt` with `POST /samples` (201) and `GET /samples/{id}` (200/404), calling `SampleService`
- [x] 5.4 Confirm `SampleController` accepts/returns only DTOs, never `domain.sample.Sample`

## 6. Main layer and bootstrap relocation

- [x] 6.1 Move `{{tld}}/{{author}}/{{app_name}}/Application.kt` (or equivalent entry point) into `main/` package
- [x] 6.2 Move any existing `@Configuration` classes (OTel, AWS SDK, Log4j2 config beans) into `main/` or an appropriate `infrastructure/config/` subpackage per D1 — AWS done; Mongo deferred to chunk D
- [x] 6.5 Fold existing `messaging/` package into hexagonal layout: `AwsClientsConfig`/`AwsMessagingProperties` → `infrastructure/config/`, `SnsPublisher` → `infrastructure/adapters/out/messaging/`, `SqsPoller` → `infrastructure/adapters/in/messaging/`, `MessageHandler` + `SampleMessageHandler` → `application/messaging/` (scope expanded during implementation, see design.md Non-Goals reversal)
- [x] 6.3 Update package declarations and any template-rendered path references accordingly
- [x] 6.4 Verify the rendered app still boots via `./gradlew bootRun` for the `default` profile — satisfied by `gradle compileKotlin` + ArchUnit test run on a regenerated default project

## 7. Default profile outbound adapter

- [x] 7.1 Create `infrastructure/adapters/out/persistence/InMemorySampleRepositoryAdapter.kt` gated by `{{if eq stack_profile "default"}}...{{end}}`
- [x] 7.2 Back it with a `ConcurrentHashMap<SampleId, Sample>` and annotate `@Repository` (or register it as a `@Bean` in `main`)
- [x] 7.3 Confirm the file renders empty for non-default profiles and boilr skips the empty file

## 8. Relational-db profile outbound adapter (replaces existing sample)

- [x] 8.1 Delete or empty the existing `template/src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/persistence/SampleEntity.kt` and `SampleRepository.kt`
- [x] 8.2 Create `infrastructure/adapters/out/persistence/jpa/SampleJpaEntity.kt` annotated `@Entity` with `Long` id and `name` column, gated by `{{if eq stack_profile "relational-db"}}`
- [x] 8.3 Create `infrastructure/adapters/out/persistence/jpa/SampleJpaRepository.kt` as `JpaRepository<SampleJpaEntity, Long>`
- [x] 8.4 Create `infrastructure/adapters/out/persistence/jpa/JpaSampleRepositoryAdapter.kt` implementing `domain.sample.SampleRepository`, mapping `SampleJpaEntity` ↔ `Sample`
- [x] 8.5 Keep `src/main/resources/db/migration/V1__init.sql` but align its `CREATE TABLE sample` to the new entity columns
- [x] 8.6 Rewire the existing Testcontainers integration test to go through `SampleController` (POST then GET) instead of calling the JPA repository directly
- [x] 8.7 Verify `domain/sample/Sample.kt` contains no JPA annotations after the refactor

## 9. Nosql-cache profile outbound adapter

- [x] 9.1 Coordinate with in-flight `add-kotlin-microservice-nosql-cache-profile` — resolved: that change was archived first, this change now modifies the archived spec via `kotlin-microservice-template/spec.md` delta
- [x] 9.2 Move the existing sample `data class` + `MongoCollection` wrapper into `infrastructure/adapters/out/persistence/mongo/` as the Mongo side of the adapter, gated by `{{if eq stack_profile "nosql-cache"}}`
- [x] 9.3 Create `infrastructure/adapters/out/persistence/mongo/MongoSampleRepositoryAdapter.kt` implementing `domain.sample.SampleRepository`, composing the Mongo wrapper and a cache-aside `SampleCache` (Spring Data Redis `StringRedisTemplate`) entirely inside the adapter
- [x] 9.4 Move the existing Mongock `ChangeUnit` into the same package, keeping it responsible for index creation
- [x] 9.5 Keep the existing `MongoHealthIndicator` under `infrastructure/config/` and `MongoConfig` under `infrastructure/config/`
- [x] 9.6 Remove any direct reference to `SampleCache`, `StringRedisTemplate`, or the Mongo repository from the `application` layer
- [x] 9.7 Rewire the Testcontainers integration test (`MongoDBContainer` + Redis container) to assert the cache-aside flow through `SampleRepository`

## 9b. Test and bootstrap fixups discovered in-flight

- [x] 9b.1 Move stale `messaging/SqsPollerIntegrationTest.kt` into `infrastructure/adapters/in/messaging/` and update its imports
- [x] 9b.2 Update `ApplicationTests.kt` to `@SpringBootTest(classes = [Application::class])` since `Application` no longer lives in the root test package's walk-up path

## 10. ArchUnit test suite

- [x] 10.1 Create `src/test/kotlin/{{tld}}/{{author}}/{{app_name}}/architecture/ArchitectureTest.kt` with `@AnalyzeClasses(packages = ["{{tld}}.{{author}}.{{app_name}}"])`
- [x] 10.2 Add `@ArchTest` rule: layered architecture (`commons` ← `domain` ← `application` ← `infrastructure` ← `main`), no cycles
- [x] 10.3 Add `@ArchTest` rule: `commons` may not depend on any other project-internal package
- [x] 10.4 Add `@ArchTest` rule: `domain` may not import `org.springframework..`, `jakarta.persistence..`, `com.mongodb..`, `org.springframework.data..`
- [x] 10.5 Add `@ArchTest` rule: `@RestController`-annotated classes reside under `infrastructure.adapters.in.web..`
- [x] 10.6 Add `@ArchTest` rule: `@Entity`- and `@Repository`-annotated classes reside under `infrastructure.adapters.out..`
- [x] 10.7 Add `@ArchTest` rule: outbound port interfaces in `domain..` end with `Port` or `Repository`
- [x] 10.8 Confirm `./gradlew test` runs the ArchUnit suite under all three profiles and it passes on a fresh generation — verified on `default`, `relational-db`, `nosql-cache` regenerations
- [x] 10.9 Add `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` (discovered needed with Gradle 9 during verification)
- [x] 10.10 Add `.allowEmptyShould(true)` to annotation-targeting rules so profiles without JPA/controllers don't fail on empty match sets

## 11. Template conditionals and file gating

- [x] 11.1 For each profile-specific adapter file, wrap content in a top-level `{{if eq stack_profile "<profile>"}}...{{end}}` and verify boilr emits an empty-or-absent file for the non-matching profiles
- [x] 11.2 Update any existing profile-gated blocks that still reference the old `persistence/` package path
- [x] 11.3 Generate all three profiles into `/tmp/` scratch dirs and diff-check expected files present/absent per spec scenarios — verified: default has only `InMemorySampleRepositoryAdapter`, no jpa/ or mongo/ files; relational-db and nosql-cache each render only their own adapter files

## 12. Documentation

- [x] 12.1 Update `CLAUDE.md` "Kotlin microservice template" section to describe the 5-layer hexagonal layout, the ArchUnit enforcement, and the per-profile outbound adapter table
- [x] 12.2 Update `kotlin-microservice/template/README.md` to explain the layering, the sample use case, and the profile-specific adapter file paths
- [x] 12.3 Note in the generated `README.md` that `commons/` is a candidate for library extraction (Layering table row)

## 13. End-to-end verification

- [x] 13.1 Regenerate a `default` project and run `./gradlew test` — ArchUnit passes (`8 tests completed, 0 failed`); `compileKotlin` + `compileTestKotlin` clean
- [x] 13.2 Regenerate a `relational-db` project and run `./gradlew test` — **all tests pass** including `JpaSampleRepositoryAdapterIntegrationTest` (Testcontainers Postgres), `SqsPollerIntegrationTest` (LocalStack), `ApplicationTests.contextLoads`, and 8 ArchUnit rules
- [x] 13.3 Regenerate a `nosql-cache` project and run `./gradlew test` — **all tests pass** including `MongoSampleRepositoryAdapterIntegrationTest` (Testcontainers Mongo + Redis with the full cache-aside round-trip), `SqsPollerIntegrationTest`, and 8 ArchUnit rules
- [x] 13.4 Verification-driven fixes applied: W1 (spec wording aligned to write-through-on-save), W2 (dropped unique index), W3 (full Docker-backed test runs for all 3 profiles), S1 (outbound-port ArchUnit rule made non-tautological), S2 (`Clock` wired through `SampleService` + `Sample.createdAt` + JPA/Mongo adapters), S3 (Mongo adapter routes through `SampleMongoRecord`), S4 (`junit-platform-launcher` documented in hexagonal-layout spec)

## 14. Verification-driven fixups

- [x] 14.1 Create `infrastructure/config/JpaRepositoriesConfig.kt` gated to `relational-db` with `@EnableJpaRepositories` + `@EntityScan` pointing at the JPA adapter package — required after moving `Application.kt` out of the root package broke Spring Data's default repo scan
- [x] 14.2 Gate `ApplicationTests.kt` to `default` profile only (other profiles' adapter integration tests already bootstrap the full Spring context with real containers, so a generic context-load smoke test is redundant and requires profile-specific infrastructure that would duplicate the integration test)
- [x] 14.3 Update `JpaSampleRepositoryAdapterIntegrationTest` and `MongoSampleRepositoryAdapterIntegrationTest` to use `@SpringBootTest(classes = [Application::class])` — walk-up `@SpringBootConfiguration` discovery fails when `Application` lives in a non-root package
- [x] 14.4 Cache-aside cache value changed from bare `name` to `"$name|$createdAt.toEpochMilli"` encoded string so `createdAt` survives cache-serve reads after the Mongo document is deleted
