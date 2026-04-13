## Why

The `kotlin-microservice` template ships profiles (`default`, `relational-db`, upcoming `nosql-cache`) but offers no opinionated example of how to wire a use case across layers. New services built from the template diverge in package layout, adapter boundaries, and test strategy. A canonical "sample" use case rendered per profile gives users a working reference of the project's hexagonal architecture and locks the layering via ArchUnit so regressions are caught at build time.

## What Changes

- Add a generic `sample` (dummy) use case to `kotlin-microservice/template/` implemented in hexagonal style across five layers: `main`, `infrastructure`, `application`, `domain`, `commons`.
- Define the layering contract: `main` = DI wiring + bootstrap; `infrastructure` = adapter implementations + protocol logic; `application` = use cases orchestrating ports; `domain` = entities, invariants, ports; `commons` = zero-dependency utilities (library-extractable).
- Render the outbound persistence adapter per `stack_profile`:
  - `default`: in-memory adapter.
  - `relational-db`: JPA/Hibernate adapter (replaces current sample `@Entity` + `JpaRepository` pair with a port-driven adapter).
  - `nosql-cache` (in-flight profile): key-value adapter using the profile's client.
- Inbound adapter: a REST controller in `infrastructure/adapters/in/web` exposing the sample use case, replacing any ad-hoc controller shipped today.
- Add ArchUnit tests enforcing layer dependency rules (`commons` depends on nothing; `domain` depends only on `commons`; `application` on `domain` + `commons`; `infrastructure` on `application` + `domain` + `commons`; `main` may depend on all; no cycles; ports live in `domain`, adapters in `infrastructure`).
- Update `kotlin-microservice/template/build.gradle.kts` to add ArchUnit (`archunit-junit5`) as a test dependency.
- Update `kotlin-microservice/template/README.md` and root `CLAUDE.md` to document the layering and the sample use case per profile.

## Capabilities

### New Capabilities

- `kotlin-microservice-hexagonal-layout`: Defines the mandatory 5-layer package layout (`main`, `infrastructure`, `application`, `domain`, `commons`), the allowed dependency direction between layers, and the ArchUnit rules that enforce them.
- `kotlin-microservice-sample-use-case`: Defines the generic sample use case shipped with the template — domain entity + port, application service, inbound REST adapter, and a profile-specific outbound adapter (in-memory / JPA / nosql-cache).

### Modified Capabilities

- `kotlin-microservice-template`: The existing "relational-db profile ships a sample entity, repository, and Flyway migration" requirement is rewritten so the sample entity/repository live in `infrastructure.adapters.out.persistence.jpa` behind a domain port, rather than at `persistence/SampleEntity.kt`.

## Impact

- **Template source**: new packages under `kotlin-microservice/template/src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/` for `commons`, `domain`, `application`, `infrastructure`, `main` (bootstrap moves here). Existing `persistence/` sample is reorganized into `infrastructure/adapters/out/persistence`.
- **Build**: `build.gradle.kts` gains an ArchUnit test dependency; version pin added to `kotlin-microservice/project.json`.
- **Tests**: new ArchUnit test class under `src/test/kotlin/.../architecture/`; existing persistence integration test is rewired to target the outbound adapter via the port.
- **Docs**: `CLAUDE.md` (Kotlin microservice section) and template `README.md` document the layering contract and sample use case.
- **Profiles**: templating conditionals (`{{if eq stack_profile "relational-db"}}`, etc.) select the outbound adapter implementation and its Gradle deps. The in-flight `add-kotlin-microservice-nosql-cache-profile` change must align its adapter with this contract.
- **No runtime breaking change** for generated services already in the wild, but **BREAKING** for anyone regenerating from the template: package layout and the sample persistence entry points move.
