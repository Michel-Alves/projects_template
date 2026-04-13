## Why

The `kotlin-microservice/` template currently mixes production and local-only artifacts at the project root: `Dockerfile` (production runtime image) sits next to `docker-compose.yml`, `localstack/`, and `otel-collector/` (all local-dev-only). A new reader can't tell at a glance which files ship with the deployed service and which are scaffolding for `docker compose up`. Moving the local-only files under `local/docker/` makes the boundary explicit, declutters the root, and leaves room for future `local/*` siblings (e.g. `local/k8s/`, `local/seed-data/`) without further reorganization.

## What Changes

- Move three things from `kotlin-microservice/template/` into `kotlin-microservice/template/local/docker/`:
  - `docker-compose.yml`
  - `localstack/` (the entire directory, including `init/init-aws.sh`)
  - `otel-collector/` (the entire directory, including `config.yaml`)
- **Keep `Dockerfile` and `.dockerignore` at the project root.** They are production artifacts: the Dockerfile produces the runtime image that gets deployed, and `.dockerignore` governs the prod build context.
- Update `docker-compose.yml`'s `build:` block so the build context still resolves to the project root from its new location:
  ```yaml
  build:
    context: ../..
    dockerfile: Dockerfile
  ```
  Volume mounts (`./localstack/init`, `./otel-collector/config.yaml`) stay as-is — they resolve relative to the compose file's new location, and both directories moved alongside it.
- The new invocation is `docker compose -f local/docker/docker-compose.yml up --build`. No wrapper script, alias, or root-level shim — explicit is fine for now.
- Update the rendered `README.md` (`kotlin-microservice/template/README.md`) so the "Run the full stack with docker-compose" section shows the new command and explains the layout split (prod vs local).
- Update the in-repo docs (`CLAUDE.md`) so the "Kotlin microservice template" section shows the new `docker compose -f local/docker/docker-compose.yml up --build` command.

## Capabilities

### New Capabilities
<!-- None — this change relocates files in an existing capability. -->

### Modified Capabilities
- `kotlin-microservice-template`: The existing requirement "docker-compose stack brings up service, LocalStack, and OTel collector" is implicitly tied to a `docker-compose.yml` at the project root (its scenarios reference `docker compose up` from the project root). That requirement MUST be revised so the compose file lives at `local/docker/docker-compose.yml`, the invocation includes `-f local/docker/docker-compose.yml`, and the LocalStack/OTel volume mounts and the build context both resolve correctly from the new location. No other requirements change — the Dockerfile, project layout, build, run, observability, and messaging requirements are unaffected.

## Impact

- **Files moved in `kotlin-microservice/template/`** (no content changes other than the `build.context` adjustment in `docker-compose.yml`):
  - `docker-compose.yml` → `local/docker/docker-compose.yml`
  - `localstack/init/init-aws.sh` → `local/docker/localstack/init/init-aws.sh`
  - `otel-collector/config.yaml` → `local/docker/otel-collector/config.yaml`
- **Files modified in `kotlin-microservice/template/`**:
  - `local/docker/docker-compose.yml` — `build.context` becomes `../..`, `build.dockerfile` becomes `Dockerfile`. All other keys unchanged.
  - `README.md` — "Run the full stack with docker-compose" section updated to show `docker compose -f local/docker/docker-compose.yml up --build` and a one-line note explaining that local-dev assets live under `local/docker/` while the root-level `Dockerfile` is the production artifact.
- **Files modified in this repo**:
  - `CLAUDE.md` — "Kotlin microservice template" section gets the updated command.
  - `README.md` (root) — no change; the one-line description of `kotlin-microservice` doesn't mention the compose path.
- **Files unchanged**: `Dockerfile`, `.dockerignore`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, all source files, all resources, all tests, `project.json`. No version pins move.
- **Coordination with `add-kotlin-microservice-db-profile`**: that change is currently at 2/4 (proposal + design done, no specs/tasks yet) and will add a conditional `postgres` service to the same `docker-compose.yml`. By landing this relocation first, db-profile can target `local/docker/docker-compose.yml` from the start instead of rebasing.
- **No breaking change for existing rendered projects.** The repo only ships the template; previously generated projects keep their old layout because they were rendered before this change. New renders get the new layout.
- **Out of scope**: Renaming `local/docker/` to anything else (e.g. `dev/`, `compose/`); adding a `Makefile`/`justfile`/shell wrapper to shorten the compose command; adding sibling `local/k8s/` or `local/seed-data/` directories now (left as room to grow).
