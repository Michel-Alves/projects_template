# local/

Everything under `local/` is for local development only. The root-level `Dockerfile` and `.dockerignore` are production artifacts — `Dockerfile` builds the runtime image that gets deployed. `local/docker/` holds the docker-compose stack and its supporting configs (LocalStack init scripts, OpenTelemetry collector config) used by `docker compose -f local/docker/docker-compose.yml up --build`.
