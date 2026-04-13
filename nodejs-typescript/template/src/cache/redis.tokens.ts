{{- if or (eq stack_profile "relational-db") (eq stack_profile "nosql-cache") }}
export const REDIS_CLIENT = Symbol('REDIS_CLIENT');
{{- end }}
