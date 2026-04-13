# Local dev stack

```
docker compose -f local/docker/docker-compose.yml up --build
```

Brings up:

- `app` — this service (built from the project-root `Dockerfile`)
- `localstack` — SNS + SQS + S3, with an init script that creates the topic, queue, subscription, and bucket
- `otel-collector` — receives OTLP traces from the app and prints them to stdout
{{- if eq stack_profile "relational-db" }}
- `postgres` — sample database
- `redis` — cache for the sample domain
{{- end }}
{{- if eq stack_profile "nosql-cache" }}
- `mongo` — sample document store
- `redis` — cache for the sample domain
{{- end }}
