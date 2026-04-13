## Context

The `kotlin-microservice/` template was deliberately shipped single-shape in its first iteration: opinionated Spring Boot + Actuator + OTel + Log4j2 JSON + AWS SDK SNS/SQS, with no `stack_profile`. The original design doc (D2) noted "we can add profiles later if real demand shows up." That demand is now: teams need a Postgres-backed shape, and the next likely follow-up is a NoSQL shape. Adding the prompt now — with a forward-looking value name (`relational-db`, not `db`) — avoids a renaming churn later.

The repo's other Kotlin template (`kotlin/`) already uses `stack_profile` with a `db` value (Exposed + HikariCP + Flyway + Postgres). That template targets a different audience (no Spring) and uses a different ORM. We deliberately diverge here: Spring Boot teams will expect Spring Data JPA, not Exposed.

Constraints inherited from the existing `kotlin-microservice/` template:
- Path templating works in both file contents and file/directory names; conditional blocks use `{{ if eq stack_profile "relational-db" }}...{{ end }}` (same syntax already used by `kotlin/template/build.gradle.kts`).
- The Dockerfile uses `gradle:{{gradle_version}}-jdk{{java_version}}` and runs `gradle bootJar` directly — no wrapper bootstrap. Nothing about JPA changes that.
- The `local` Spring profile is the integration point with `docker-compose.yml`. Adding Postgres means adding a service to compose AND adding a `spring.datasource.*` block to `application-local.yml`.
- Versions are pinned in `project.json` so a single PR can bump them.
- Tests that need a real backend (LocalStack today) require Docker. Adding Testcontainers Postgres doesn't change that contract.

Stakeholders: same as the parent change — the template author and downstream service teams who run `boilr template use kotlin-microservice ...`.

## Goals / Non-Goals

**Goals:**
- Add a `stack_profile` prompt with values `default` and `relational-db`. `default` produces byte-identical output to today (modulo the prompt itself). `relational-db` adds JPA + Postgres + Flyway + Testcontainers Postgres + a compose `postgres` service, with zero manual edits after `boilr template use`.
- Use **Spring Data JPA** with Hibernate — the canonical Spring persistence path. Spring Boot's BOM manages JPA/Hibernate versions, so no extra version pins.
- Use **Flyway** for migrations, configured via `spring-boot-starter-data-jpa`'s auto-config (Flyway runs on startup before JPA opens its session factory).
- Use **PostgreSQL 16** (`postgres:16-alpine`) as the only supported database in this profile. The driver and Flyway dialect both target Postgres.
- Use **Testcontainers** `postgresql` module + `@DynamicPropertySource` for the integration test, matching the pattern used for the SQS round-trip test.
- Keep the prompt name `stack_profile` and value `relational-db` so a future `nosql-db` profile slots in without renames.
- The `relational-db` profile MUST NOT change anything observable when `default` is selected — no new prompts, no new files, no behavior change.

**Non-Goals:**
- Multiple datasources, read replicas, or multi-tenant schemas.
- R2DBC / reactive variants. Spring WebFlux is not used in this template; sticking with the blocking JDBC stack matches the rest of the bundle.
- Liquibase. Flyway matches `kotlin/db` and the broader ecosystem default; one tool, one pattern.
- MySQL/MariaDB. Adding a second DB doubles the test matrix without much value; revisit only on real demand.
- Generated REST endpoints over the entity (no `@RepositoryRestResource`, no Spring Data REST). The sample is intentionally minimal: an `@Entity` and a `JpaRepository` to prove the wiring.
- A new top-level template — we extend the existing one.

## Decisions

### D1. Single template with `stack_profile`, not a sibling template
- **Choice**: Add a `stack_profile` prompt to the existing `kotlin-microservice/` template. Conditional blocks in shared files; new files (entity, repository, migration, integration test) live in normal paths and are guarded by `{{ if eq stack_profile "relational-db" }}...{{ end }}` at the file-content level OR placed in directories that boilr renders unconditionally but whose contents are empty under `default`.
- **Why**: User explicitly chose this in /opsx:new. Avoids template duplication (every dependency bump would otherwise need to land in two places). The `stack_profile` prompt is a natural extension point that the user expects from the sibling `kotlin/` template.
- **Alternatives considered**: Sibling template `kotlin-microservice-db/` (rejected — duplication, drift); a separate "overlay" mechanism (boilr doesn't support overlays, so this would be a custom post-process step the user has to run, defeating the purpose).

### D2. Value name is `relational-db`, not `db`
- **Choice**: The two values for `stack_profile` are `default` and `relational-db`.
- **Why**: A future `nosql-db` profile (DynamoDB, MongoDB, etc.) is on the roadmap. If we used `db` now, adding `nosql` later would either rename `db` → `relational-db` (breaking everyone who picked `db`) or sit awkwardly next to `nosql-db`. Picking the more specific name now is a one-line cost today and zero migration cost later.
- **Alternatives considered**: `db` (rejected — see above); `postgres` (too narrow, hardcodes a specific RDBMS into the prompt vocabulary); `sql` (less explicit than `relational-db`).

### D3. Spring Data JPA + Hibernate, not Spring JDBC, jOOQ, or Exposed
- **Choice**: `spring-boot-starter-data-jpa` (which pulls Hibernate as the JPA provider) plus a sample `@Entity` and `JpaRepository<SampleEntity, Long>`.
- **Why**: It's the path of least surprise for Spring Boot teams. Hibernate is bundled, schema-validation-on-startup is automatic, repositories are one line, and Actuator gets a `db` health indicator for free. The user explicitly asked for Spring Data JPA.
- **Alternatives considered**: Spring JDBC + jOOQ (lighter, type-safe queries, but requires a code-gen step that complicates the build), Exposed (matches `kotlin/db` but is unidiomatic in a Spring Boot project), MyBatis (extra config, no clear advantage here).

### D4. Flyway with `flyway-database-postgresql` module
- **Choice**: Depend on `org.flywaydb:flyway-core` AND `org.flywaydb:flyway-database-postgresql`. Migrations live at `src/main/resources/db/migration/V1__init.sql`. Spring Boot auto-configures Flyway and runs migrations before JPA opens its session factory.
- **Why**: Flyway 10+ split the Postgres dialect into a separate module — without `flyway-database-postgresql` Flyway will refuse to start against Postgres. Easy to forget; pinning both up front prevents the foot-gun.
- **Alternatives considered**: Liquibase (more flexible XML/YAML/SQL DSL but more ceremony for the simple cases this template targets), Flyway-only without the dialect module (broken on Flyway 10+).

### D5. PostgreSQL 16 alpine, no other RDBMS support
- **Choice**: `postgres:16-alpine` in compose; `org.postgresql:postgresql` driver from Spring Boot's BOM (no explicit version pin needed); Flyway dialect module is Postgres-only.
- **Why**: One DB, one driver, one Flyway dialect — keeps the matrix small. Postgres 16 is the current stable LTS line and matches what most teams target. Alpine for image size.
- **Alternatives considered**: Postgres 15 (older but no real benefit), MySQL/MariaDB additionally (doubles test matrix).

### D6. Testcontainers Postgres + `@DynamicPropertySource` for integration tests
- **Choice**: Add `org.testcontainers:postgresql` to `testImplementation` (the BOM is already imported for the existing LocalStack module). Write `SampleRepositoryIntegrationTest.kt` as a `@SpringBootTest` that boots a `PostgreSQLContainer` once per test class and uses `@DynamicPropertySource` to inject `spring.datasource.url`, `username`, `password` into the Spring context. Flyway runs against the container automatically.
- **Why**: Same pattern already used for the LocalStack test; teams already need Docker for `gradlew test`. `@DynamicPropertySource` is the canonical Spring 6 / Boot 3 way to wire Testcontainers — no manual `ApplicationContextInitializer` plumbing.
- **Alternatives considered**: H2 in-memory (rejected — Hibernate dialect mismatches with real Postgres are how teams ship subtle bugs), an embedded Postgres binary like `zonky/embedded-postgres` (extra unmaintained dep, slower than Testcontainers cold-start in practice).

### D7. Compose Postgres service is rendered conditionally
- **Choice**: `docker-compose.yml` gains a `postgres` service block AND `app.depends_on.postgres.condition: service_healthy` only when `stack_profile == relational-db`. Both are wrapped in `{{ if eq stack_profile "relational-db" }}...{{ end }}` blocks at the YAML level.
- **Why**: Spinning up a Postgres container the user doesn't need would be a confusing surprise. Conditional rendering at the YAML level is the same approach `kotlin/template/build.gradle.kts` already uses for its profile-specific deps — well-trodden in this repo.
- **Risk**: YAML is whitespace-sensitive, so the templating has to be careful with `{{- ... -}}` trimming. Mitigation: render with both profile values during apply and YAML-lint both outputs before declaring done.
- **Alternatives considered**: Always render the postgres service but documented as optional (rejected — users would still pull the image and pay the startup cost); a separate `docker-compose.db.yml` overlay (rejected — boilr can't render compose profiles cleanly, and it's an extra command for the user to remember).

### D8. Spring `local` profile is the integration point — connection details live in `application-local.yml`
- **Choice**: Conditionally append a `spring.datasource.*` block to `application-local.yml` (host `postgres` from compose, db `{{app_name}}`, user/pass `{{app_name}}`/`{{app_name}}`). The base `application.yml` gains a conditional `spring.jpa.*` block (Hibernate DDL = `validate`, show-sql = false, open-in-view = false) and a conditional `spring.flyway.*` block (`enabled: true`, `locations: classpath:db/migration`).
- **Why**: Mirrors the existing AWS messaging config pattern (`application.yml` has structural defaults, `application-local.yml` injects local-only values). No env-var override needed for first-pass — deployed envs set `SPRING_DATASOURCE_URL` etc. through their own mechanism, which Spring already honors.
- **Alternatives considered**: Putting all datasource config in `application.yml` with placeholder env vars (more verbose, less local-friendly).

### D9. Sample entity is intentionally tiny
- **Choice**: One `SampleEntity` with `id: Long` (auto-generated) and `name: String`, one `SampleRepository : JpaRepository<SampleEntity, Long>`, one `V1__init.sql` migration creating a `sample_entity` table, and one integration test that saves and reads back a row. No REST endpoint, no service layer.
- **Why**: The point is to prove the wiring (Spring Boot → JPA → Postgres → Flyway → Testcontainers), not to ship a starter app. Teams will replace the sample within an hour of generating the project.
- **Alternatives considered**: A full CRUD REST controller over the entity (more code, more opinions to argue with — name fields, validation, error handling), no sample at all (rejected — without a row in the DB the test can't prove the round-trip).

### D10. Existing requirements get MODIFIED, not just supplemented
- **Choice**: The delta spec at `openspec/changes/add-kotlin-microservice-db-profile/specs/kotlin-microservice-template/spec.md` will use both `## ADDED Requirements` (for the new JPA/Flyway/Postgres requirements) and `## MODIFIED Requirements` (for the two requirements whose behavior actually changes: "Template prompts for the common project keys" — which gains `stack_profile` and loses the "no stack profile prompt" scenario — and the docker-compose requirement, which gains a conditional `postgres` service). Per the openspec rules, MODIFIED requirements MUST include the entire updated requirement block.
- **Why**: The existing requirement that says "the user is NOT prompted for a `stack_profile` value" is actively contradicted by this change. Leaving it unmodified would create a broken main spec after sync.
- **Alternatives considered**: Treating the whole capability as removed and re-added (heavyweight, loses history at sync time).

## Risks / Trade-offs

- **[Risk] Conditional rendering inside `docker-compose.yml` is YAML-fragile.** A stray space inside a `{{- ... -}}` trim block can break parsing for both profile values. → Mitigation: render the template with `stack_profile=default` and `stack_profile=relational-db` during task 9.x and run `docker compose config` against both outputs to validate.
- **[Risk] Spring Boot starter `data-jpa` adds significant cold-start time even when not using JPA — but in `default` it's not on the classpath.** → Confirmed safe: the dep is inside a `{{ if eq stack_profile "relational-db" }}...{{ end }}` block in `build.gradle.kts`, so `default` is byte-identical to today.
- **[Risk] Flyway 10+ requires `flyway-database-postgresql`; forgetting it is a startup-time error that's easy to miss in code review.** → Mitigation: D4 explicitly pins both deps, and the verification step (task 9.x) runs `./gradlew bootRun` against the rendered project to catch it at smoke-test time.
- **[Risk] Testcontainers Postgres pulls a ~150MB image on first run; CI environments without an image cache will see slow first builds.** → Acceptable; mirrors the existing LocalStack constraint. Documented in the rendered README.
- **[Risk] Modifying an existing capability spec means future `/opsx:archive` will produce a `## MODIFIED Requirements` delta against the freshly-synced main spec from the previous archive. The sync logic must handle this.** → The previous archive used a manual sync (no `openspec-sync-specs` skill registered); I'll do the same here and explicitly diff the modified blocks.
- **[Trade-off] No CRUD REST endpoint over the sample entity.** → The bundle is already heavy; resist scope creep. Document in the rendered README that adding a controller is a 5-line follow-up.
- **[Trade-off] No `application-test.yml` profile.** → The integration test uses `@DynamicPropertySource` to inject Testcontainers values directly, which is cleaner than maintaining a parallel YAML file.
- **[Trade-off] Locking to Postgres 16 means a future Postgres 17 bump requires touching `project.json`.** → Acceptable, and that's the whole point of pinning versions there.

## Migration Plan

This change is purely additive from the perspective of existing users:

1. Existing rendered projects don't change. They were generated before `stack_profile` existed; nothing in this repo touches them.
2. Future renders gain a new prompt. Picking `default` (the prompt default) reproduces today's behavior exactly.
3. Picking `relational-db` adds the JPA/Postgres/Flyway bundle.

No rollback strategy needed — this is a template, not a deployed system. If the new profile is broken, it can be patched in a follow-up change without affecting existing generated projects.

## Open Questions

- **Flyway exact version?** Spring Boot 3.3.5's BOM manages a Flyway version, but the `flyway-database-postgresql` dialect module is not in the BOM. We need to pin it manually. Likely `10.18.0` (matches Spring Boot 3.3.5's bundled Flyway core), to be confirmed during apply by checking the BOM and bumping the dialect to match.
- **Should the integration test also exercise Flyway explicitly** (assert that `V1__init.sql` ran by querying `flyway_schema_history`), or is the implicit "test passes => migration ran" enough? Lean toward implicit — explicit Flyway assertions are noise.
- **Hibernate `ddl-auto`: `validate` or `none`?** `validate` catches schema/entity drift at startup, `none` is silent. Lean toward `validate` because it's the canonical Spring Data JPA + Flyway pairing and the failure mode (startup error) is exactly when you want it.
