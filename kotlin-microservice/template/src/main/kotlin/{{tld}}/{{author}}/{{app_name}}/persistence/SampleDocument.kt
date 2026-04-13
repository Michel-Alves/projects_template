{{- if eq stack_profile "nosql-cache" }}
package {{tld}}.{{author}}.{{app_name}}.persistence

data class SampleDocument(
    val id: String,
    val name: String,
)
{{- end }}
