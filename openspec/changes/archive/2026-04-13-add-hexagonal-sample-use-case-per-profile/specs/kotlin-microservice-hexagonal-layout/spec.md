## ADDED Requirements

### Requirement: Generated project uses a five-layer hexagonal package layout

The generated project SHALL organize Kotlin source code under `src/main/kotlin/<tld>/<author>/<app_name>/` into exactly five layer packages: `main`, `infrastructure`, `application`, `domain`, and `commons`. Every non-test Kotlin file (other than the legacy top-level `Application.kt` bootstrap, which MAY live in `main`) MUST belong to exactly one of these layer packages. The layers MUST be used for the purposes below:

- `main` — `@SpringBootApplication` entry point, `@Configuration` classes, bean wiring, and application bootstrap only.
- `infrastructure` — adapter implementations (inbound under `infrastructure.adapters.in.*`, outbound under `infrastructure.adapters.out.*`) and any framework-specific configuration that is not part of bootstrap (e.g. Mongo client builder, Redis template wiring).
- `application` — use case services that orchestrate outbound ports.
- `domain` — entities, value objects, invariants, and outbound port interfaces. No framework imports.
- `commons` — pure utility code with zero project-internal dependencies. Intended to be extractable into its own library artifact without code changes.

#### Scenario: All five layer packages exist after generation

- **WHEN** the template is generated with default prompts
- **THEN** the directories `src/main/kotlin/<tld>/<author>/<app_name>/main/`, `.../infrastructure/`, `.../application/`, `.../domain/`, and `.../commons/` all exist
- **AND** the `@SpringBootApplication`-annotated class lives under the `main` package

#### Scenario: Inbound and outbound adapters are separated

- **WHEN** a reader inspects `infrastructure/adapters/`
- **THEN** the folder contains an `in/` subfolder for inbound adapters (driving side) and an `out/` subfolder for outbound adapters (driven side)
- **AND** REST controllers live under `infrastructure/adapters/in/web/`
- **AND** persistence adapters live under `infrastructure/adapters/out/persistence/`

### Requirement: Layer dependency direction is enforced by ArchUnit

The generated project SHALL include an ArchUnit test suite that fails the build when layer dependency rules are violated. The rules MUST enforce:

- `commons` SHALL NOT depend on any other project-internal package (`main`, `infrastructure`, `application`, `domain`).
- `domain` SHALL depend only on `commons` (and JDK/Kotlin stdlib). `domain` MUST NOT import classes from `org.springframework..`, `jakarta.persistence..`, `com.mongodb..`, `org.springframework.data..`, or any other framework package used by adapters.
- `application` SHALL depend only on `domain` and `commons`.
- `infrastructure` SHALL depend only on `application`, `domain`, and `commons` — never on `main`.
- `main` MAY depend on all other layers.
- No dependency cycles SHALL exist between layers.
- Any class annotated with `@RestController` MUST reside under `infrastructure.adapters.in.web..`.
- Any class annotated with `@Entity` (JPA) or `@Repository` MUST reside under `infrastructure.adapters.out..`.
- Outbound port interfaces SHALL reside in `domain..` and have a name ending in `Port` or `Repository`.

The ArchUnit suite MUST run as part of the standard `./gradlew test` task with no additional configuration.

#### Scenario: ArchUnit test class exists and runs with the standard test task

- **WHEN** the template is generated with default prompts
- **THEN** the file `src/test/kotlin/<tld>/<author>/<app_name>/architecture/ArchitectureTest.kt` exists and contains at least one function annotated with `@ArchTest`
- **AND** running `./gradlew test` against the generated project executes the ArchUnit tests and reports them in the JUnit output

#### Scenario: Domain package rejects framework imports

- **WHEN** a developer adds a file under `domain/` that imports `org.springframework.stereotype.Service`, `jakarta.persistence.Entity`, `com.mongodb.client.MongoCollection`, or `org.springframework.data.redis.core.StringRedisTemplate`
- **THEN** `./gradlew test` fails with an ArchUnit rule violation naming the offending class and the forbidden package

#### Scenario: RestController in the wrong layer is rejected

- **WHEN** a developer places a `@RestController`-annotated class outside `infrastructure.adapters.in.web..`
- **THEN** `./gradlew test` fails with an ArchUnit rule violation

#### Scenario: Dependency direction violations are rejected

- **WHEN** a developer adds a class in `application/` that imports from `infrastructure/`, or a class in `domain/` that imports from `application/`
- **THEN** `./gradlew test` fails with an ArchUnit layered-architecture violation

#### Scenario: Commons is dependency-free

- **WHEN** a developer adds an import from any of `main`, `infrastructure`, `application`, or `domain` to a file under `commons/`
- **THEN** `./gradlew test` fails with an ArchUnit rule violation identifying `commons` as the forbidden origin

### Requirement: ArchUnit dependency is pinned via project.json

The `kotlin-microservice/project.json` SHALL declare an `archunit_version` prompt with a sensible default, and the rendered `build.gradle.kts` SHALL add `com.tngtech.archunit:archunit-junit5:{{archunit_version}}` to `testImplementation`. The rendered `build.gradle.kts` SHALL ALSO add `org.junit.platform:junit-platform-launcher` to `testRuntimeOnly` — Gradle 9+ requires the JUnit Platform launcher to be explicitly present on the test runtime classpath, and without it the ArchUnit tests (and every other JUnit 5 test) fail with `Failed to load JUnit Platform` before any test method runs. No ArchUnit artifact SHALL appear on a non-test configuration.

#### Scenario: ArchUnit is a test-only dependency

- **WHEN** a reader inspects the rendered `build.gradle.kts`
- **THEN** `archunit-junit5` appears exactly once, only on `testImplementation`
- **AND** `junit-platform-launcher` appears exactly once, only on `testRuntimeOnly`
- **AND** `./gradlew dependencies --configuration runtimeClasspath` does not list `archunit-junit5` or `junit-platform-launcher`

#### Scenario: Version is centralized in project.json

- **WHEN** a reader inspects `kotlin-microservice/project.json`
- **THEN** `archunit_version` is declared as a scalar prompt with a semver default
- **AND** the string `{{archunit_version}}` appears in `kotlin-microservice/template/build.gradle.kts`
