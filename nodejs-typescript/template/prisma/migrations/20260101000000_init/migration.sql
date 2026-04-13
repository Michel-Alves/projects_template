{{- if eq stack_profile "relational-db" }}
CREATE TABLE "sample_entity" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "payload" TEXT NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "sample_entity_pkey" PRIMARY KEY ("id")
);
{{- end }}
