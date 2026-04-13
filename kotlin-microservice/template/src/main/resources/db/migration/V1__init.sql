{{- if eq stack_profile "relational-db" -}}
CREATE TABLE sample_entity (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);
{{- end -}}
