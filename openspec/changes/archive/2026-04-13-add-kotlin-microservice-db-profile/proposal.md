## Why

The `kotlin-microservice/` template currently has no persistence layer — teams that need a database have to wire Spring Data JPA, a connection pool, migrations, a Postgres driver, and Testcontainers by hand every time. That's the same kind of repeated setup the template was created to remove. Adding a `relational-db` stack profile encodes the canonical Postgres + JPA + Flyway shape once and keeps the door open for a future `nosql-db` profile (DynamoDB, MongoDB, etc.) without restructuring the template.

## What Changes

- Introduce a `stack_profile` prompt in `kotlin-microservice/project.json` with values `default` and `relational-db`. The first iteration (the existing template content) becomes `default`; `relational-db` is the new opinionated profile.
- The `relational-db` profile adds, on top of `default`:
  - **Spring Data JPA** (`spring-boot-starter-data-jpa`) with Hibernate
  - **PostgreSQL driver** (`org.postgresql:postgresql`)
  - **Flyway** (`flyway-core` + `flyway-database-postgresql`)
  - **HikariCP** (already pulled by Spring Boot, no extra dep)
  - A sample `@Entity`, a `JpaRepository<Entity, Long>`, and a `V1__init.sql` Flyway migration
  - A Spring `DataSource` health indicator already exposed via Actuator (no extra code; comes with JPA)
  - Testcontainers `postgresql` module + a `@SpringBootTest` integration test that uses `@DynamicPropertySource` to point Spring at a Postgres container
- Conditional templating in `build.gradle.kts`, `application.yml`, `application-local.yml`, `Application.kt` (no-op — JPA auto-configures), and `local/docker/docker-compose.yml` to switch the new pieces on for `relational-db` only. The root-level `Dockerfile` does not change — JPA, Hibernate, the Postgres driver, and Flyway are all on the classpath when the profile is selected, so the same fat jar runs in both shapes without Dockerfile branching.
- `local/docker/docker-compose.yml` (when profile is `relational-db`) gains a `postgres` service (`postgres:16-alpine`, named volume, env-var creds), and the `app` service depends on it being healthy. The `local` Spring profile points the JPA datasource at it.
- Repo docs updated: `CLAUDE.md` and `README.md` document the new `stack_profile` prompt and the `relational-db` shape; the rendered template's `README.md` adapts based on the chosen profile.
- **No removal of existing behavior.** Choosing `default` produces exactly the same output as today.

## Capabilities

### New Capabilities
<!-- None — this change extends the existing capability rather than introducing a new one. -->

### Modified Capabilities
- `kotlin-microservice-template`: Currently single-shape with no `stack_profile`. The capability gains a `stack_profile` prompt and a new `relational-db` profile that bundles Spring Data JPA, PostgreSQL, Flyway, Testcontainers Postgres, and a `postgres` service in `docker-compose.yml`. The existing requirements about prompts ("Template prompts for the common project keys") and the existing absence-of-`stack_profile` scenario MUST be revised; new requirements describe the JPA/Flyway/Postgres bundling and the docker-compose Postgres service.

## Impact

- **Modified files in this repo**:
  - `kotlin-microservice/project.json` — add `stack_profile` array prompt and pin `postgres_version`, `flyway_version` (Spring Boot's BOM manages JPA/Hibernate versions, so no extra pins for those).
  - `kotlin-microservice/template/build.gradle.kts` — conditional `dependencies { ... }` block for the JPA/Postgres/Flyway/Testcontainers-postgres deps.
  - `kotlin-microservice/template/src/main/resources/application.yml` — conditional `spring.datasource.*`, `spring.jpa.*`, `spring.flyway.*` blocks.
  - `kotlin-microservice/template/src/main/resources/application-local.yml` — conditional Postgres datasource pointing at the compose Postgres service.
  - `kotlin-microservice/template/local/docker/docker-compose.yml` — conditional `postgres` service + `depends_on` wiring on `app`. (The compose file was relocated under `local/docker/` by a sibling change that landed first; this change targets the new path.)
  - `kotlin-microservice/template/README.md` — conditional sections describing JPA/Flyway/Postgres when the profile is enabled.
  - `CLAUDE.md` and root `README.md` — document the new `stack_profile` prompt.
- **New files in this repo** (only rendered when `stack_profile=relational-db`):
  - `kotlin-microservice/template/src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/persistence/SampleEntity.kt`
  - `kotlin-microservice/template/src/main/kotlin/{{tld}}/{{author}}/{{app_name}}/persistence/SampleRepository.kt`
  - `kotlin-microservice/template/src/main/resources/db/migration/V1__init.sql`
  - `kotlin-microservice/template/src/test/kotlin/{{tld}}/{{author}}/{{app_name}}/persistence/SampleRepositoryIntegrationTest.kt`
- **Naming intentionally future-proof**: the prompt is `stack_profile` and the value is `relational-db` (not `db`) so a future `nosql-db` profile slots in cleanly without renames.
- **No version bumps** to existing pins (Spring Boot 3.3.5, Java 21, etc.). Postgres pinned to `16-alpine`, Flyway to a recent stable (TBD in design — probably ~10.x to match Spring Boot 3.3.x's compatible range).
- **Tests for the relational-db profile** require Docker (Testcontainers Postgres). This matches the existing OTel/SQS integration test which already needs Docker — no new constraint.
- **Out of scope**: Liquibase, MySQL/MariaDB, R2DBC reactive variants, multiple datasources, read replicas, schema-per-tenant. All deferred to follow-ups if asked for.
