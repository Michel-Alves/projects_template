# {{app_name}}

A Kotlin + Spring Boot microservice generated from the `kotlin-microservice` boilr template.

## Bundled stack

- **Spring Boot {{spring_boot_version}}** (Web + Actuator) on Java {{java_version}} / Kotlin {{kotlin_version}}
- **Observability**: Actuator probes (`/actuator/health/liveness`, `/actuator/health/readiness`), Micrometer + Prometheus (`/actuator/prometheus`), OpenTelemetry SDK + OTLP exporter
- **Logging**: Log4j2 with `JsonTemplateLayout` (ECS-shaped JSON to stdout). The default Logback starter is excluded.
- **Messaging**: AWS SDK v2 SNS publisher + SQS poller, configured via `aws.messaging.*` properties
- **Production runtime**: multi-stage `Dockerfile` at the project root (and `.dockerignore`)
- **Local dev**: `local/docker/docker-compose.yml` running the service alongside LocalStack (SNS/SQS) and an OpenTelemetry collector

## First-run setup

This step is only needed if you want to run `./gradlew` directly (e.g. `./gradlew bootRun` or `./gradlew test`). If you only ever run the service through `docker compose`, you can skip it — the `Dockerfile` builds inside a `gradle:{{gradle_version}}-jdk{{java_version}}` image and brings its own gradle.

The template ships only `gradle/wrapper/gradle-wrapper.properties`. To materialize `gradlew` and `gradle-wrapper.jar` for local `./gradlew` use, run once after rendering:

```bash
gradle wrapper --gradle-version {{gradle_version}}
```

After that, use `./gradlew` for everything.

## Build and run

```bash
./gradlew build           # compile + run unit tests
./gradlew bootRun         # run the service on :8080

curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/actuator/prometheus
```

## Run the full stack with docker-compose

This project separates production and local-dev assets by directory: the root-level `Dockerfile` (and `.dockerignore`) is the **production runtime image** — it's what you build and ship. Everything under `local/docker/` is for **local development only** (compose stack, LocalStack init scripts, OTel collector config). Mixing them at the root would obscure that boundary.

The `Dockerfile` builds inside a `gradle:{{gradle_version}}-jdk{{java_version}}` image, so this works on a fresh checkout — no `gradle wrapper` bootstrap required.

```bash
docker compose -f local/docker/docker-compose.yml up --build
```

This brings up:

- `app` — the service, with `SPRING_PROFILES_ACTIVE=local` and OTel pointed at the collector
- `localstack` — SNS + SQS, with an init script that creates the `{{app_name}}-events` topic and queue and subscribes the queue to the topic
- `otel-collector` — OTLP receiver that prints spans to its stdout

Verify LocalStack is set up:

```bash
docker compose -f local/docker/docker-compose.yml exec localstack awslocal sns list-topics
docker compose -f local/docker/docker-compose.yml exec localstack awslocal sqs list-queues
```

Publish a test message and watch the poller log it as JSON in `docker compose -f local/docker/docker-compose.yml logs app`.

## Configuration

All `aws.messaging.*` properties are bound from `application.yml` and overridable by environment variables (`AWS_MESSAGING_TOPIC_ARN`, `AWS_MESSAGING_QUEUE_URL`, `AWS_MESSAGING_REGION`, etc.). The `local` profile (`application-local.yml`) defaults them to LocalStack.

OpenTelemetry honors the standard env vars: `OTEL_EXPORTER_OTLP_ENDPOINT` (default `http://localhost:4317`) and `OTEL_SERVICE_NAME` (default `{{app_name}}`).

## Tests

```bash
./gradlew test
```

`SqsPollerIntegrationTest` uses Testcontainers' `LocalStackContainer` to verify a publish→consume round-trip end to end. It requires a running Docker daemon.

## Trade-offs to know

- **No `@SqsListener`** — the `SqsPoller` is a hand-rolled `@Scheduled` long-poll loop. Swap it for `spring-cloud-aws-starter-sqs` if you want batch handling, automatic visibility extension, or `@SqsListener` ergonomics.
- **No database layer** — add Spring Data JPA / Exposed and Flyway by hand if you need persistence.
- **OTel collector exports to stdout only** — wire a Jaeger or Tempo backend in `otel-collector/config.yaml` if you want a UI.
