## Context

The `kotlin-microservice` template now has three profiles: `default`, `relational-db`, and `nosql-cache`. Each ships its own ad-hoc "sample" code:

- `relational-db`: `@Entity` + `JpaRepository` + `V1__init.sql` + Testcontainers test.
- `nosql-cache`: a `data class`, a repository wrapping `MongoCollection`, a Mongock `ChangeUnit`, a `SampleCache` (Spring Data Redis), a cache-aside `SampleDocumentService`, a `MongoHealthIndicator`, and a Testcontainers test.
- `default`: nothing beyond HTTP + messaging.

These samples are not aligned on package layout, adapter boundaries, or how the use case is exposed over HTTP. A user generating from the template has to reverse-engineer the intended layering. We want a single, enforced hexagonal layout and a single sample use case whose outbound adapter varies by profile, with ArchUnit catching drift at build time.

Constraints carried over from existing decisions:
- `nosql-cache` uses the **raw** MongoDB Kotlin sync driver (no Spring Data Mongo) and **Spring Data Redis** for the cache. Mongo config lives under `app.mongo.*`. These asymmetries must survive the refactor.
- Profiles are mutually exclusive and selected via `stack_profile`.
- Template files and paths use Go `text/template` syntax; per-profile files are gated with `{{if eq stack_profile "..."}}` or live in profile-specific directories.

## Goals / Non-Goals

**Goals:**
- Define a single 5-layer package layout (`main`, `infrastructure`, `application`, `domain`, `commons`) and enforce it with ArchUnit in the template's test suite.
- Ship one generic `Sample` use case whose domain + application + inbound-REST code is identical across profiles, and whose outbound adapter is selected by `stack_profile`.
- Replace the existing per-profile sample code with the hexagonal version (JPA adapter, Mongo+Redis adapter, in-memory adapter) without losing existing behavior (migrations, cache-aside, health indicator, Testcontainers coverage).
- Document the layering contract in `CLAUDE.md` and the template `README.md`.

**Non-Goals:**
- Introducing a DI framework other than Spring (we keep `@Configuration` + `@Bean` / `@Component`).
- Extracting `commons` into a separate Gradle module or published artifact — it is a package with a zero-dependency rule, nothing more, for now.
- Adding CQRS, event sourcing, or a mapper library (MapStruct etc.). Hand-written mappers in `infrastructure`.
- ~~Changing the messaging (SNS/SQS) sample — out of scope for this change.~~ **Reversed during implementation**: the existing `messaging/` package violates the ArchUnit layered rule in D2 (no layer = no place), so it was folded into the hexagonal layout. `AwsClientsConfig` + `AwsMessagingProperties` → `infrastructure/config/`; `SnsPublisher` → `infrastructure/adapters/out/messaging/`; `SqsPoller` → `infrastructure/adapters/in/messaging/`; `MessageHandler` (inbound port) + `SampleMessageHandler` (sample impl) → `application/messaging/`. No behavior change.
- Backwards compatibility for already-generated services.

## Decisions

### D1. Package layout

```
{{tld}}.{{author}}.{{app_name}}
├── main            // @SpringBootApplication, @Configuration classes, bean wiring
├── infrastructure
│   ├── adapters
│   │   ├── in
│   │   │   └── web         // @RestController, DTOs, request/response mappers
│   │   └── out
│   │       └── persistence // profile-specific outbound adapter(s)
│   └── config              // infrastructure-only config (Mongo client, Redis template, etc.)
├── application
│   └── sample              // use case services, implements inbound ports (if any)
├── domain
│   └── sample              // entities, value objects, invariants, outbound ports
└── commons                 // pure utilities, no framework, no other-layer imports
```

**Rationale:** Matches the user's stated layering. Keeping `in`/`out` subfolders under `adapters` makes ArchUnit rules trivial to express and makes the direction of dependency obvious to readers.

**Alternative considered:** Gradle multi-module split (one module per layer). Rejected — boilr template complexity and build-time cost outweigh the benefit for a single-service template. ArchUnit gives the same enforcement cheaper.

### D2. Layer dependency rules (enforced by ArchUnit)

- `commons` → depends on nothing in `{{tld}}.{{author}}.{{app_name}}.*`.
- `domain` → may depend on `commons` only.
- `application` → may depend on `domain`, `commons`.
- `infrastructure` → may depend on `application`, `domain`, `commons`.
- `main` → may depend on all layers.
- No cycles anywhere.
- `domain..` must not import `org.springframework..`, `jakarta.persistence..`, `com.mongodb..`, `org.springframework.data..`, or any adapter framework. (Keeps domain framework-free.)
- Classes annotated `@RestController` must live in `infrastructure.adapters.in.web..`.
- Classes annotated `@Repository`, `@Entity`, or implementing a port suffix `*Adapter` must live in `infrastructure.adapters.out..`.
- Outbound ports (interfaces) must live in `domain..` and have names ending in `Port` or `Repository`.

**Rationale:** ArchUnit's `layeredArchitecture()` DSL covers most of this; annotation-location rules catch the common mistakes (a controller slipping into `application`, a JPA entity leaking into `domain`).

**Alternative considered:** Konsist. Rejected — ArchUnit is more established, has first-class JUnit 5 integration, and the user explicitly asked for ArchUnit.

### D3. Sample use case shape

One use case: **"register a sample and fetch it by id"**. Minimal but exercises both a write and a read path, which is what the relational and nosql adapters need to differ on.

- `domain.sample.Sample` — data class with `id: SampleId`, `name: String`, invariant: name non-blank.
- `domain.sample.SampleId` — value class wrapping a string.
- `domain.sample.SampleRepositoryPort` — `fun save(sample: Sample): Sample` + `fun findById(id: SampleId): Sample?`.
- `application.sample.SampleService` — orchestrates the port, no framework annotations beyond `@Service`.
- `infrastructure.adapters.in.web.SampleController` — `POST /samples`, `GET /samples/{id}`, with `SampleRequest`/`SampleResponse` DTOs and a mapper.
- `infrastructure.adapters.out.persistence.*SampleRepositoryAdapter` — one implementation per profile, implements `SampleRepositoryPort`.

**Rationale:** A single use case with both read and write keeps the sample small but forces every profile to demonstrate its persistence idioms. The domain stays agnostic of storage details.

### D4. Per-profile outbound adapters

| Profile | Adapter class | Backing tech |
|---|---|---|
| `default` | `InMemorySampleRepositoryAdapter` | `ConcurrentHashMap` |
| `relational-db` | `JpaSampleRepositoryAdapter` | Spring Data JPA `SampleJpaEntity` + `SampleJpaRepository` (internal to `infrastructure.adapters.out.persistence.jpa`), Flyway `V1__init.sql` creates `sample` table |
| `nosql-cache` | `MongoSampleRepositoryAdapter` wrapping a `SampleMongoCollection` wrapper + `SampleCache` (Spring Data Redis `StringRedisTemplate`). Cache-aside lives **inside** the adapter, so the application layer sees a single port. Mongock `ChangeUnit` creates indexes. |

**Rationale:** Keeping cache-aside inside the adapter preserves the existing `nosql-cache` behavior but moves the decision out of the application layer — the use case doesn't know a cache exists. This is the correct hexagonal placement: caching is an infrastructure concern.

**Alternative considered:** Two ports (`SampleRepositoryPort` + `SampleCachePort`) composed in the application layer. Rejected — it leaks the caching strategy into the use case and complicates the default profile (which has no cache).

### D5. JPA entity vs domain entity

`JpaSampleRepositoryAdapter` owns a separate `SampleJpaEntity` annotated with `@Entity`, and maps to/from `domain.sample.Sample` at the adapter boundary. The domain `Sample` has **no** JPA annotations.

**Rationale:** The ArchUnit rule forbids `jakarta.persistence..` in `domain`. Hand-written mapping is a few lines and keeps the domain pure.

### D6. ArchUnit placement

- Test class: `src/test/kotlin/.../architecture/ArchitectureTest.kt` (profile-agnostic — always rendered).
- Uses `@AnalyzeClasses(packages = ["{{tld}}.{{author}}.{{app_name}}"])` with `@ArchTest` functions.
- Runs as part of `./gradlew test`, no separate task.

### D7. Template rendering strategy

- Domain/application/commons/main files are always rendered (identical across profiles).
- `infrastructure.adapters.in.web.*` is always rendered.
- `infrastructure.adapters.out.persistence.*` uses profile-gated files. Two approaches considered:
  1. **Directory gating** — profile-specific subdirs with `{{if eq stack_profile "..."}}` inside file contents wrapping the whole file.
  2. **Separate files per profile** — e.g. `InMemorySampleRepositoryAdapter.kt` only rendered when `default`, etc.
  - **Chosen**: (2) — each adapter file has a top-level `{{if eq stack_profile "..."}}...{{end}}` wrapping the whole content, so the file renders empty (and boilr is told to skip empty files) for non-matching profiles. This matches what the template already does elsewhere.

### D8. ArchUnit dependency

Add `com.tngtech.archunit:archunit-junit5:{{archunit_version}}` to `testImplementation`, and `archunit_version` to `project.json`. No runtime cost.

## Risks / Trade-offs

- **Risk:** Existing `nosql-cache` integration test exercises the cache-aside flow end-to-end. Moving cache-aside inside the adapter could mask cache misses in application-level tests. **Mitigation:** Keep the Testcontainers integration test at the controller level (`MockMvc` + real Mongo + real Redis containers), so the cache behavior is still observed end-to-end.
- **Risk:** ArchUnit rules are strict and will fail builds if a user adds a controller in the wrong package. **Mitigation:** The README calls out each rule with a one-line rationale, and the ArchUnit test messages are self-explanatory.
- **Risk:** Domain purity rule (`no jakarta.persistence in domain`) breaks if a user wants to annotate the domain entity for brevity. **Trade-off accepted:** That is precisely the anti-pattern the rule is meant to prevent; the template should guide, not accommodate, that shortcut.
- **Risk:** Five layers is heavyweight for a tiny service. **Trade-off accepted:** The template targets production microservices, not scripts; the overhead is a handful of packages.
- **Risk:** The `commons` layer is initially empty (no utilities yet). **Mitigation:** Ship a single placeholder (e.g. a `Clock` typealias or a `Result`-like helper actually used by the domain), so the package isn't a dead folder, and document that anything added there must stay dependency-free.

## Migration Plan

This is template code, not runtime code, so "migration" means updating the template source and the in-flight nosql-cache change:

1. Land this change's spec + tasks.
2. Refactor `kotlin-microservice/template/src/main/kotlin/.../persistence/` (relational sample) into `infrastructure/adapters/out/persistence/jpa/` + domain/application/web layers. Rewire the integration test.
3. Coordinate with `add-kotlin-microservice-nosql-cache-profile` (already in flight): its sample classes move into the new layout before that change archives. If that change archives first, a follow-up refactor pass is needed — flag this in tasks.
4. Add ArchUnit dep + test class.
5. Update `CLAUDE.md` and `README.md`.
6. Manually regenerate a project per profile (`default`, `relational-db`, `nosql-cache`) and run `./gradlew test` to verify ArchUnit passes and Testcontainers still works.

**Rollback:** Revert the commit; template users regenerating get the old layout back. No runtime state to reverse.

## Open Questions

- **Q1:** Should `commons` ship as a package or as a Gradle source set (`src/commons/kotlin`) so the "no dependencies" rule is enforced at compile time in addition to ArchUnit? *Leaning: package-only for now; revisit if drift happens.*
- **Q2:** Do we want ArchUnit to also forbid `application` from importing `infrastructure.*` by name, or is the layered rule sufficient? *Leaning: the layered rule is sufficient — adding a second rule is redundant.*
- **Q3:** Name of the port — `SampleRepositoryPort` vs `SampleRepository`. The rule in D2 allows both suffixes; pick one convention in the spec. *Leaning: `SampleRepository` (shorter, idiomatic), with the `Port` suffix reserved for non-repository outbound ports.*
- **Q4:** Should the inbound web adapter also go through an explicit inbound port (interface) in `application`, or can the controller call the service directly? *Leaning: direct call — an inbound port adds ceremony without catching bugs that ArchUnit wouldn't already catch. Document the decision.*
