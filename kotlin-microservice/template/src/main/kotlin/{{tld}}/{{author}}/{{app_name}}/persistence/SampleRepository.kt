{{- if eq stack_profile "relational-db" -}}
package {{tld}}.{{author}}.{{app_name}}.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface SampleRepository : JpaRepository<SampleEntity, Long>
{{- end -}}
