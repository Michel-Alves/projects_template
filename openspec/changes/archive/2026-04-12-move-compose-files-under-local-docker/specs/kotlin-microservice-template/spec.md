## MODIFIED Requirements

### Requirement: docker-compose stack brings up service, LocalStack, and OTel collector
The generated project SHALL include a `docker-compose.yml` at `local/docker/docker-compose.yml` (not at the project root) that defines at minimum three services: the application (built from the root-level `Dockerfile` via a `build.context` of `../..` so the Dockerfile and the source tree are part of the build context), `localstack` configured with `SERVICES=sns,sqs`, and an OpenTelemetry collector. The compose file's volume mounts for the LocalStack init scripts and the OTel collector config SHALL resolve to `local/docker/localstack/init/` and `local/docker/otel-collector/config.yaml` respectively — i.e. the `localstack/` and `otel-collector/` directories MUST live alongside the compose file, not at the project root. LocalStack MUST run an init script on startup that creates the configured SNS topic and SQS queue and subscribes the queue to the topic. The application service MUST set `SPRING_PROFILES_ACTIVE=local` and point its OTel exporter at the collector. The root-level `Dockerfile` and `.dockerignore` MUST remain at the project root because they are production artifacts, not local-dev assets.

#### Scenario: Compose stack starts cleanly from the new path
- **WHEN** a user runs `docker compose -f local/docker/docker-compose.yml up --build` from the project root of a freshly generated project
- **THEN** all three services reach a healthy state and the application logs show a successful Spring Boot startup banner

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

### Requirement: Repository documentation describes the new template
`CLAUDE.md` and `README.md` SHALL be updated to describe the `kotlin-microservice/` template, its bundled dependencies, the prompts it exposes, and the boilr commands for registering and using it. The documentation MUST explicitly note that this template is single-shape (no `stack_profile`) and lists Spring Boot, Actuator, Micrometer/Prometheus, OpenTelemetry, Log4j2 JSON logging, AWS SDK v2 SNS/SQS, and the docker-compose LocalStack stack as bundled. The `CLAUDE.md` "Kotlin microservice template" section MUST show the compose invocation as `docker compose -f local/docker/docker-compose.yml up --build` to match the new layout. The rendered template's `README.md` MUST document the same invocation in its "Run the full stack with docker-compose" section and MUST include a one-paragraph explanation that the root-level `Dockerfile` is the production artifact while everything under `local/docker/` is local-dev only.

#### Scenario: CLAUDE.md documents the template
- **WHEN** a reader opens `CLAUDE.md`
- **THEN** there is a "Kotlin microservice template" section listing the bundled dependencies and the `boilr template save` / `boilr template use` commands for it
- **AND** the section shows `docker compose -f local/docker/docker-compose.yml up --build` as the command to bring up the local stack

#### Scenario: README.md lists the template
- **WHEN** a reader opens `README.md`
- **THEN** the template index includes `kotlin-microservice` alongside `kotlin` and `clojure` with a one-line description

#### Scenario: Rendered README explains the prod-vs-local layout
- **WHEN** a reader opens the rendered `README.md` of a freshly generated project
- **THEN** the "Run the full stack with docker-compose" section shows the `docker compose -f local/docker/docker-compose.yml up --build` invocation
- **AND** the section contains a paragraph explaining that the root `Dockerfile` is the production runtime image while everything under `local/docker/` is for local development only
