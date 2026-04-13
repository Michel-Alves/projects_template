## 1. Relocate files in the template

- [x] 1.1 Moved `kotlin-microservice/template/docker-compose.yml` → `kotlin-microservice/template/local/docker/docker-compose.yml` (used `mv`, not `git mv`, because the parent `kotlin-microservice/` tree is not yet committed in this branch)
- [x] 1.2 Moved `kotlin-microservice/template/localstack` → `kotlin-microservice/template/local/docker/localstack`
- [x] 1.3 Moved `kotlin-microservice/template/otel-collector` → `kotlin-microservice/template/local/docker/otel-collector`
- [x] 1.4 Confirmed `Dockerfile` and `.dockerignore` are still at `kotlin-microservice/template/`
- [x] 1.5 Confirmed `kotlin-microservice/template/local/docker/localstack/init/init-aws.sh` is still executable (`-rwxr-xr-x`)

## 2. Update the relocated compose file

- [x] 2.1 `local/docker/docker-compose.yml` `app.build` → `context: ../..`, `dockerfile: Dockerfile`
- [x] 2.2 Added the explanatory comment above the `build:` block
- [x] 2.3 Verified the `localstack` and `otel-collector` volume mount strings are unchanged (still `./localstack/init:/...` and `./otel-collector/config.yaml:/...`)

## 3. Optional `local/README.md`

- [x] 3.1 Added `kotlin-microservice/template/local/README.md` explaining the prod-vs-local convention

## 4. Update the rendered project README

- [x] 4.1 `kotlin-microservice/template/README.md` "Run the full stack with docker-compose" section now shows `docker compose -f local/docker/docker-compose.yml up --build` (and updated the `exec` and `logs` examples in the same section to match)
- [x] 4.2 Added the prod-vs-local layout-explanation paragraph at the top of the section

## 5. Update CLAUDE.md

- [x] 5.1 `CLAUDE.md` "Kotlin microservice template" section updated: compose command now `docker compose -f local/docker/docker-compose.yml up --build`, plus a one-line note that `local/docker/` is for local-dev assets while root-level `Dockerfile`/`.dockerignore` are production artifacts

## 6. End-to-end verification

- [x] 6.1 `boilr template save -f` + `boilr template use kotlin-microservice /tmp/svc-test` succeeded; `grep -r '{{' /tmp/svc-test` returned no matches (zero unresolved tokens in any rendered file or path)
- [x] 6.2 Rendered layout verified: `Dockerfile` and `.dockerignore` at root; `local/docker/docker-compose.yml`, `local/docker/localstack/init/init-aws.sh`, `local/docker/otel-collector/config.yaml` all present at the new path; old root-level paths confirmed gone
- [x] 6.3 `ls -l /tmp/svc-test/local/docker/localstack/init/init-aws.sh` shows `-rwxr-xr-x` — boilr preserved the executable bit through `template save`/`template use`
- [x] 6.4 `docker compose -f local/docker/docker-compose.yml config` parsed cleanly and resolved `app.build.context` to `/tmp/svc-test` (the project root) with `dockerfile: Dockerfile`
- [x] 6.5 `docker compose -f local/docker/docker-compose.yml up -d --build` brought up all three services to the `(healthy)` state (app, localstack, otel-collector)
- [x] 6.6 `awslocal sns list-topics` returned `arn:aws:sns:us-east-1:000000000000:myservice-events`; `awslocal sqs list-queues` returned the matching queue URL — init script ran from the relocated path
- [x] 6.7 `awslocal sns publish ... --message hello-from-relocated` produced `Received SQS message: hello-from-relocated` from `SampleMessageHandler` in `docker compose logs app`, JSON-formatted via Log4j2
- [x] 6.8 Hit `/actuator/health` 3× → `docker compose logs otel-collector` showed 3 OTLP `ResourceSpans` with `service.name: Str(myservice)`
- [x] 6.9 `docker compose down -v`, removed the `myservice:dev` image, and `rm -rf /tmp/svc-test`
