## Context

The `kotlin-microservice/template/` root currently holds five docker-related artifacts:

| File | Audience |
|---|---|
| `Dockerfile` | **Production** — builds the runtime image deployed to k8s/ECS/etc. |
| `.dockerignore` | **Production** — governs the prod build context. |
| `docker-compose.yml` | **Local dev** — orchestrates app + LocalStack + OTel collector for `docker compose up`. |
| `localstack/init/init-aws.sh` | **Local dev** — LocalStack init hook. |
| `otel-collector/config.yaml` | **Local dev** — OTel collector pipeline config. |

The two production artifacts and the three local-dev artifacts share visual real estate, which makes the boundary invisible to a new reader. The fix is mechanical: relocate the three local-dev artifacts under `local/docker/` so the layout encodes the boundary.

The proposal already locked in the relocations and the user-facing command. This design only needs to nail the few mechanical details that go wrong if you guess: build context resolution, volume-mount path resolution, init-script execution mode, and the docs touch points.

## Goals / Non-Goals

**Goals:**
- Encode the prod-vs-local boundary in the directory layout: prod artifacts at the project root, local-dev artifacts under `local/docker/`.
- Keep `docker compose -f local/docker/docker-compose.yml up --build` working byte-for-byte with the current end-to-end behavior verified in the previous change (LocalStack init runs, SNS→SQS round-trip works, OTel spans flow through the collector).
- Make room for future `local/*` siblings (`local/k8s/`, `local/seed-data/`, `local/grafana/`) without further reshuffles.

**Non-Goals:**
- Renaming the directory to anything other than `local/docker/`.
- Adding a wrapper script (`Makefile`, `justfile`, shell alias) to shorten the compose invocation. Long form is fine for now.
- Adding a root-level `compose.yaml` shim that `extends` the relocated file. Adds a second compose entry point users have to remember.
- Touching any non-docker files (build, source, tests, app config). Out of scope.
- Coordinating with `add-kotlin-microservice-db-profile` beyond sequencing — that change is paused and will rebase against the new layout when it resumes.

## Decisions

### D1. Build context goes back up to the project root, not to `local/docker/`
- **Choice**: In the relocated `local/docker/docker-compose.yml`, the `app` service uses:
  ```yaml
  build:
    context: ../..
    dockerfile: Dockerfile
  ```
- **Why**: The `Dockerfile` runs `COPY settings.gradle.kts build.gradle.kts gradle.properties ./` and `COPY src src` from the build context. Those paths must resolve to the project root, not to `local/docker/`. The cleanest way is to set `context: ../..` (compose paths are relative to the compose file's directory) and leave `dockerfile: Dockerfile` resolving to `<project root>/Dockerfile`. This requires zero changes inside the `Dockerfile` itself.
- **Alternatives considered**:
  - **Keep `context: .` and put a stub `Dockerfile.local` in `local/docker/`** — would break `COPY src src` because the build context wouldn't include the source tree. Rejected.
  - **Move the `Dockerfile` too** — explicitly contradicts the user's instruction; the `Dockerfile` is a prod artifact and stays at the root.
  - **Symlink the `Dockerfile` into `local/docker/`** — boilr renders to a fresh directory; symlinks are awkward to template and don't survive cleanly across OSes. Rejected.

### D2. Volume mounts stay relative; both directories move together
- **Choice**: The `localstack` and `otel-collector` service blocks keep their existing mount strings:
  ```yaml
  volumes:
    - ./localstack/init:/etc/localstack/init/ready.d
    - ./otel-collector/config.yaml:/etc/otel-collector/config.yaml:ro
  ```
- **Why**: Compose resolves relative volume paths against the directory of the compose file. Since `localstack/init/init-aws.sh` and `otel-collector/config.yaml` move alongside `docker-compose.yml` into `local/docker/`, the relative paths still resolve correctly: `local/docker/localstack/init` and `local/docker/otel-collector/config.yaml`. **Zero string changes inside these mount lines** — only their on-disk location moves. This is the property that makes the relocation cheap.
- **Alternatives considered**: Rewriting the mount strings as absolute paths or `${PWD}`-prefixed (rejected — relative paths are already correct).

### D3. `init-aws.sh` keeps its executable bit
- **Choice**: Set `chmod +x kotlin-microservice/template/local/docker/localstack/init/init-aws.sh` after relocating it. The `git mv` approach preserves the mode bit, but boilr's "save" pipeline copies files into its registry — verify on the rendered output that the file is executable by running `ls -l /tmp/svc-test/local/docker/localstack/init/init-aws.sh` and re-`chmod +x` the source if needed.
- **Why**: LocalStack's init mechanism only invokes scripts under `/etc/localstack/init/ready.d/` if they're executable. The previous change had to `chmod +x` the source file explicitly for the same reason; this is a regression risk worth calling out.
- **Alternatives considered**: Have the LocalStack container `chmod +x` the script on startup via an entrypoint shim (rejected — the file mode is the canonical signal and the existing approach already works).

### D4. Rendered README documents the new command and explains the layout
- **Choice**: In `kotlin-microservice/template/README.md`, the "Run the full stack with docker-compose" section gains:
  - Updated command: `docker compose -f local/docker/docker-compose.yml up --build`
  - One paragraph explaining the layout split: "The root-level `Dockerfile` is the production runtime image — it's what you build and ship. Everything under `local/docker/` is for local development only (compose stack, LocalStack init scripts, OTel collector config). Mixing them at the root makes it hard to tell which files matter in production; this layout encodes that boundary."
- **Why**: The whole point of the change is the explicit boundary; the README is where a new reader will look first.
- **Alternatives considered**: Skipping the explanatory paragraph (rejected — without it, the new layout looks arbitrary).

### D5. `CLAUDE.md` gets the same command update; root `README.md` is untouched
- **Choice**: In `CLAUDE.md`, replace the existing `docker compose up --build` line in the "Kotlin microservice template" section with `docker compose -f local/docker/docker-compose.yml up --build`. The root `README.md` only lists the template name and a one-line description with no command examples, so it does not need to change.
- **Why**: `CLAUDE.md` is the source of truth for "how do I drive these templates" guidance; if its commands drift from reality, the instructions in this file become a foot-gun for both me and the user. The root `README.md`'s `kotlin-microservice` line ("Spring Boot 3 microservice with Actuator, Micrometer/Prometheus, OpenTelemetry, Log4j2 JSON logging, AWS SDK v2 SNS/SQS, and a `docker-compose` LocalStack stack") is generic enough that it stays accurate.
- **Alternatives considered**: Also adding a layout note to the root README (rejected — over-specification for a top-level index file).

## Risks / Trade-offs

- **[Risk] YAML whitespace + relative-path templating creates a quiet failure mode.** A wrong `build.context` value (e.g. `..` instead of `../..`) compiles to valid YAML and only fails at `docker build` time with a confusing "no such file" error. → Mitigation: task 9.x runs `docker compose -f local/docker/docker-compose.yml config` (a parse-and-resolve check) on the rendered output, then runs the full `docker compose up --build` end-to-end against LocalStack and the OTel collector — same recipe used for tasks 9.4-9.6 in the previous change. Verifying the SNS round-trip and at least one OTel span proves the volume mounts also resolved correctly.
- **[Risk] Init-script executable bit gets stripped during `boilr template save`.** → Mitigation: confirm the bit on the rendered file as part of task 9.x; if boilr strips it, document the workaround in the rendered README and (worst case) `chmod +x` the source from a top-level shell script the template no longer offers — but the previous change confirmed boilr preserves the bit, so this is a low-probability risk.
- **[Trade-off] The compose invocation gets longer.** `docker compose -f local/docker/docker-compose.yml up --build` is harder to type than `docker compose up --build`. → User explicitly chose the long form; revisit only if real friction shows up.
- **[Trade-off] `build.context: ../..` is a code smell to anyone who hasn't seen this layout before.** → Mitigation: a one-line YAML comment above the `build:` block explaining why (`# project root: Dockerfile + src/ live one level up so the prod Dockerfile sees the source tree`).
- **[Trade-off] Two changes will modify the same `kotlin-microservice-template` capability spec back-to-back.** → Acceptable. This change ships first and re-syncs the main spec; `add-kotlin-microservice-db-profile` then rebases its delta against the freshly synced docker-compose requirement when it resumes.

## Migration Plan

Purely additive for existing users:

1. Existing rendered projects don't change — they were generated against the old layout and continue to work.
2. New renders get the new layout. The first time a user runs `docker compose ...` after generating, they'll need the new `-f local/docker/docker-compose.yml` flag, which the rendered README documents prominently.
3. No rollback needed — this is a template, and the change is a directory reshuffle. If we discover the new layout is wrong, the next change reshuffles again without affecting existing generated projects.

## Open Questions

- **Do we want the `local/` directory to ship with a `.gitkeep` or a `local/README.md` explaining its purpose?** Without one, an empty `local/` would be invisible in git after the move. Lean: add a one-line `local/README.md` explaining the prod-vs-local convention so the directory's purpose survives a `tree` command. Decide during apply.
