{{- if eq stack_profile "relational-db" -}}
CREATE TABLE sample (
    id          BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(255) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);
{{- end -}}
